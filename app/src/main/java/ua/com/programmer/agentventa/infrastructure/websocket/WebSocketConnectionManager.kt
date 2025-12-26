package ua.com.programmer.agentventa.infrastructure.websocket

import android.content.SharedPreferences
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.shouldUseWebSocket
import ua.com.programmer.agentventa.data.websocket.WebSocketState
import ua.com.programmer.agentventa.domain.repository.NetworkRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Manages automatic WebSocket connection lifecycle.
 *
 * Connection triggers:
 * 1. App comes to foreground + has pending data
 * 2. Network becomes available + has pending data
 * 3. Idle interval elapsed (periodic check for new data from server)
 *
 * Disconnection:
 * - App goes to background and no pending data (after grace period)
 * - Network lost (handled by WebSocketRepository reconnection)
 */
@Singleton
class WebSocketConnectionManager @Inject constructor(
    private val webSocketRepository: WebSocketRepository,
    private val networkRepository: NetworkRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val pendingDataChecker: PendingDataChecker,
    private val userAccountRepository: UserAccountRepository,
    private val sharedPreferences: SharedPreferences,
    private val logger: Logger
) : DefaultLifecycleObserver {

    private val TAG = "WsConnectionManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Connection state exposed for UI observation
    val connectionState: StateFlow<WebSocketState> = webSocketRepository.connectionState

    // Pending data summary for UI
    private val _pendingDataSummary = MutableStateFlow(PendingDataSummary.EMPTY)
    val pendingDataSummary: StateFlow<PendingDataSummary> = _pendingDataSummary.asStateFlow()

    // Last sync timestamp
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private var isAppInForeground = false
    private var currentAccount: UserAccount? = null
    private var connectionJob: Job? = null
    private var periodicCheckJob: Job? = null
    private var backgroundDisconnectJob: Job? = null

    // Grace period before disconnecting when app goes to background (5 minutes)
    private val backgroundGracePeriod = 5 * 60 * 1000L

    /**
     * Initialize the connection manager with app lifecycle.
     * Should be called from Application.onCreate()
     */
    fun initialize() {
        logger.d(TAG, "Initializing WebSocket connection manager")

        // Register as lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Start network monitoring
        networkMonitor.startMonitoring()

        // Observe account changes
        scope.launch {
            userAccountRepository.currentAccount.collect { account ->
                handleAccountChange(account)
            }
        }

        // Observe network connectivity changes
        scope.launch {
            networkMonitor.isConnected.collect { isConnected ->
                if (isConnected && isAppInForeground) {
                    logger.d(TAG, "Network restored, checking connection")
                    checkAndConnect()
                }
            }
        }

        // Observe connection state changes to trigger sync on successful connection
        scope.launch {
            webSocketRepository.connectionState.collect { state ->
                if (state is WebSocketState.Connected) {
                    handleConnectionSuccess()
                }
            }
        }

        // Start periodic check scheduler
        startPeriodicCheck()
    }

    /**
     * Called when WebSocket connection is successfully established.
     * Triggers data sync if there is pending data.
     */
    private suspend fun handleConnectionSuccess() {
        logger.d(TAG, "Connection established, checking for pending data")
        updatePendingDataSummary()

        if (_pendingDataSummary.value.hasPendingData) {
            logger.d(TAG, "Pending data found, triggering sync")
            _lastSyncTime.value = System.currentTimeMillis()
            triggerDataSync()
        } else {
            logger.d(TAG, "No pending data to sync")
        }
    }

    /**
     * App came to foreground.
     */
    override fun onStart(owner: LifecycleOwner) {
        logger.d(TAG, "App to foreground")
        isAppInForeground = true

        // Cancel any pending background disconnect
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = null

        // Check if we should connect
        scope.launch {
            checkAndConnect()
        }
    }

    /**
     * App went to background.
     */
    override fun onStop(owner: LifecycleOwner) {
        logger.d(TAG, "App to background")
        isAppInForeground = false

        // Schedule disconnect after grace period if no pending data
        backgroundDisconnectJob = scope.launch {
            delay(backgroundGracePeriod)

            if (!isAppInForeground && !pendingDataChecker.hasPendingData()) {
                logger.d(TAG, "No pending data, disconnecting after background grace period")
                webSocketRepository.disconnect()
            }
        }
    }

    /**
     * Check conditions and connect if needed.
     * Can be called manually (e.g., "Sync Now" button).
     * Sync is triggered automatically in handleConnectionSuccess() when connection succeeds.
     */
    suspend fun checkAndConnect() {
        updatePendingDataSummary()

        if (!shouldConnect()) {
            return
        }

        val account = currentAccount ?: return

        // Cancel any existing connection attempt
        connectionJob?.cancel()

        connectionJob = scope.launch {
            logger.d(TAG, "Initiating WebSocket connection")
            // Connection success and sync trigger handled by connectionState observer
            webSocketRepository.connect(account)
        }
    }

    /**
     * Force immediate connection attempt (ignores idle interval).
     */
    suspend fun connectNow() {
        val account = currentAccount ?: return

        if (!networkMonitor.isNetworkAvailable()) {
            logger.w(TAG, "Cannot connect: no network")
            return
        }

        if (!account.shouldUseWebSocket()) {
            logger.w(TAG, "Cannot connect: WebSocket not enabled for account")
            return
        }

        webSocketRepository.connect(account)
    }

    /**
     * Manually trigger data sync via WebSocket.
     */
    suspend fun triggerDataSync() {
        // The NetworkRepository handles the actual sync logic
        // It will check if WebSocket is connected and send pending data
        networkRepository.updateDifferential().collect { result ->
            logger.d(TAG, "Sync result: $result")
        }
    }

    /**
     * Get the configured idle interval in minutes.
     */
    fun getIdleIntervalMinutes(): Int {
        val intervalMs = sharedPreferences.getLong(
            PREF_WEBSOCKET_IDLE_INTERVAL,
            Constants.WEBSOCKET_IDLE_INTERVAL_DEFAULT
        )
        return (intervalMs / (60 * 1000)).toInt()
    }

    /**
     * Set the idle interval for periodic checks.
     * @param minutes Interval in minutes (5-60)
     */
    fun setIdleIntervalMinutes(minutes: Int) {
        val clampedMinutes = minutes.coerceIn(5, 60)
        val intervalMs = clampedMinutes * 60 * 1000L

        sharedPreferences.edit {
            putLong(PREF_WEBSOCKET_IDLE_INTERVAL, intervalMs)
        }

        logger.d(TAG, "Idle interval set to $clampedMinutes minutes")

        // Restart periodic check with new interval
        restartPeriodicCheck()
    }

    private fun shouldConnect(): Boolean {
        val account = currentAccount
        if (account == null) {
            logger.d(TAG, "No current account")
            return false
        }

        if (!account.shouldUseWebSocket()) {
            logger.d(TAG, "WebSocket not enabled for account")
            return false
        }

        if (!networkMonitor.isNetworkAvailable()) {
            logger.d(TAG, "No network available")
            return false
        }

        if (webSocketRepository.isConnected()) {
            logger.d(TAG, "Already connected")
            return false
        }

        // Connect if has pending data
        if (_pendingDataSummary.value.hasPendingData) {
            logger.d(TAG, "Has pending data, should connect")
            return true
        }

        // Connect if idle interval has elapsed
        val idleInterval = sharedPreferences.getLong(
            PREF_WEBSOCKET_IDLE_INTERVAL,
            Constants.WEBSOCKET_IDLE_INTERVAL_DEFAULT
        )
        val timeSinceLastSync = System.currentTimeMillis() - _lastSyncTime.value
        if (timeSinceLastSync >= idleInterval) {
            logger.d(TAG, "Idle interval elapsed, should connect to check for new data")
            return true
        }

        return false
    }

    private fun handleAccountChange(account: UserAccount?) {
        val previousAccount = currentAccount
        currentAccount = account

        if (account == null) {
            logger.d(TAG, "Account cleared, disconnecting")
            scope.launch { webSocketRepository.disconnect() }
            return
        }

        // If account changed, reconnect
        if (previousAccount?.guid != account.guid) {
            logger.d(TAG, "Account changed, reconnecting")
            scope.launch {
                webSocketRepository.disconnect()
                delay(500) // Brief delay before reconnecting
                checkAndConnect()
            }
        }
    }

    private suspend fun updatePendingDataSummary() {
        _pendingDataSummary.value = pendingDataChecker.getPendingDataSummary()
    }

    private fun startPeriodicCheck() {
        val interval = sharedPreferences.getLong(
            PREF_WEBSOCKET_IDLE_INTERVAL,
            Constants.WEBSOCKET_IDLE_INTERVAL_DEFAULT
        )

        periodicCheckJob?.cancel()
        periodicCheckJob = scope.launch {
            while (isActive) {
                delay(interval)

                if (isAppInForeground || pendingDataChecker.hasPendingData()) {
                    logger.d(TAG, "Periodic check: checking connection")
                    checkAndConnect()
                }
            }
        }

        logger.d(TAG, "Periodic check started with interval: ${interval / 60000} minutes")
    }

    private fun restartPeriodicCheck() {
        periodicCheckJob?.cancel()
        startPeriodicCheck()
    }

    companion object {
        const val PREF_WEBSOCKET_IDLE_INTERVAL = "websocket_idle_interval"
    }
}
