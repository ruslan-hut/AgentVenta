package ua.com.programmer.agentventa.infrastructure.websocket

import android.content.SharedPreferences
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.isValidForWebSocketConnection
import ua.com.programmer.agentventa.data.local.entity.shouldUseWebSocket
import ua.com.programmer.agentventa.data.websocket.WebSocketState
import ua.com.programmer.agentventa.domain.repository.NetworkRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Manages automatic WebSocket connection lifecycle.
 *
 * IMPORTANT: WebSocket ALWAYS connects for license management and device status,
 * regardless of the useWebSocket flag. The useWebSocket flag only controls
 * whether DATA EXCHANGE uses WebSocket or HTTP.
 *
 * Connection triggers:
 * 1. App comes to foreground (always - for license/status)
 * 2. Network becomes available (always - for license/status)
 * 3. Idle interval elapsed (periodic check for license/status updates)
 * 4. Pending data exists (only if useWebSocket=true for data sync)
 *
 * Device Status Flow:
 * - Device must be approved via WebSocket before any data sync
 * - HTTP operations are blocked until device is approved
 * - License errors prevent data sync until resolved
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

    // Device approval status derived from connection state
    // Device is considered approved only when WebSocket is connected with "approved" status
    val isDeviceApproved: Boolean
        get() = connectionState.value is WebSocketState.Connected

    // Device is pending approval
    val isDevicePending: Boolean
        get() = connectionState.value is WebSocketState.Pending

    // Device has license error (expired, not active, device limit)
    val hasLicenseError: Boolean
        get() = connectionState.value is WebSocketState.LicenseError

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
        // Register lifecycle observer, start network monitoring, observe account changes

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

        // Update last sync time when catalog data is received from server
        scope.launch {
            webSocketRepository.incomingMessages.collect {
                _lastSyncTime.value = System.currentTimeMillis()
            }
        }

        // Update last sync time on batch_complete (full catalog sync finished)
        scope.launch {
            webSocketRepository.batchComplete.collect {
                _lastSyncTime.value = System.currentTimeMillis()
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
        Log.d("XBUG", "WsManager: connection success, checking pending data")
        logger.d(TAG, "Connection established, checking for pending data")
        updatePendingDataSummary()

        val pending = _pendingDataSummary.value
        Log.d("XBUG", "WsManager: pendingData=${pending.hasPendingData} (orders=${pending.ordersCount}, cash=${pending.cashCount})")
        if (pending.hasPendingData) {
            _lastSyncTime.value = System.currentTimeMillis()
            Log.d("XBUG", "WsManager: triggering data sync")
            triggerDataSync()
        }
    }

    /**
     * App came to foreground.
     */
    override fun onStart(owner: LifecycleOwner) {
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
        isAppInForeground = false

        // Schedule disconnect after grace period if no pending data
        backgroundDisconnectJob = scope.launch {
            delay(backgroundGracePeriod)

            if (!isAppInForeground && !pendingDataChecker.hasPendingData()) {
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
            Log.d("XBUG", "WsManager: checkAndConnect - shouldConnect=false")
            return
        }

        val account = currentAccount ?: return

        Log.d("XBUG", "WsManager: checkAndConnect - connecting, account=${account.guid.take(8)}, useWs=${account.useWebSocket}")
        // Cancel any existing connection attempt
        connectionJob?.cancel()

        connectionJob = scope.launch {
            // Connection success and sync trigger handled by connectionState observer
            webSocketRepository.connect(account)
        }
    }

    /**
     * Force immediate connection attempt (ignores idle interval).
     * WebSocket ALWAYS connects for license management, regardless of useWebSocket flag.
     */
    suspend fun connectNow() {
        val account = currentAccount ?: return

        if (!networkMonitor.isNetworkAvailable()) {
            logger.w(TAG, "Cannot connect: no network")
            return
        }

        if (!account.isValidForWebSocketConnection()) {
            logger.w(TAG, "Cannot connect: account not valid for WebSocket (missing guid)")
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
            return false
        }

        // WebSocket ALWAYS connects for license management and device status
        // The useWebSocket flag only controls DATA EXCHANGE method, not the connection itself
        if (!account.isValidForWebSocketConnection()) {
            return false
        }

        if (!networkMonitor.isNetworkAvailable()) {
            return false
        }

        if (webSocketRepository.isConnected()) {
            return false
        }

        // Connect if has pending data (only for accounts using WebSocket for data)
        if (account.shouldUseWebSocket() && _pendingDataSummary.value.hasPendingData) {
            return true
        }

        // Connect if idle interval has elapsed (for license/status updates)
        val idleInterval = sharedPreferences.getLong(
            PREF_WEBSOCKET_IDLE_INTERVAL,
            Constants.WEBSOCKET_IDLE_INTERVAL_DEFAULT
        )
        val timeSinceLastSync = System.currentTimeMillis() - _lastSyncTime.value
        if (timeSinceLastSync >= idleInterval) {
            return true
        }

        // Always connect for license management on app start
        return true
    }

    private fun handleAccountChange(account: UserAccount?) {
        val previousAccount = currentAccount
        currentAccount = account

        if (account == null) {
            scope.launch { webSocketRepository.disconnect() }
            return
        }

        // If account changed, reconnect
        if (previousAccount?.guid != account.guid) {
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
                    checkAndConnect()
                }
            }
        }

        // Periodic check started with configured interval
    }

    private fun restartPeriodicCheck() {
        periodicCheckJob?.cancel()
        startPeriodicCheck()
    }

    companion object {
        const val PREF_WEBSOCKET_IDLE_INTERVAL = "websocket_idle_interval"
    }
}
