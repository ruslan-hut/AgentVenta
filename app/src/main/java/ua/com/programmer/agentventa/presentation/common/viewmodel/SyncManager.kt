package ua.com.programmer.agentventa.presentation.common.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.data.remote.Result
import ua.com.programmer.agentventa.data.websocket.WebSocketState
import ua.com.programmer.agentventa.domain.repository.NetworkRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.utility.Constants
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync operation events (one-time).
 */
sealed class SyncEvent {
    data class Progress(val message: String) : SyncEvent()
    data class Success(val message: String) : SyncEvent()
    data class Error(val message: String) : SyncEvent()
}

/**
 * Manages data synchronization operations.
 * Extracted from SharedViewModel for single responsibility.
 */
@OptIn(FlowPreview::class)
@Singleton
class SyncManager @Inject constructor(
    private val networkRepository: NetworkRepository,
    private val webSocketRepository: WebSocketRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * WebSocket connection state for UI feedback.
     * Observe this to show connection status indicators.
     */
    val webSocketState: StateFlow<WebSocketState> = webSocketRepository.connectionState
    private val _updateState = MutableStateFlow<Result?>(null)
    val updateState: StateFlow<Result?> = _updateState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _progressMessage = MutableStateFlow("")
    val progressMessage: StateFlow<String> = _progressMessage.asStateFlow()

    private val _syncEvents = EventChannel<SyncEvent>()
    val syncEvents = _syncEvents.flow

    // Snackbar events for UI notifications
    private val _snackbarEvents = EventChannel<WebSocketSnackbarEvent>()
    val snackbarEvents = _snackbarEvents.flow

    // Aggregation counters for document acks (debounced)
    private val pendingOrderAcks = MutableStateFlow(0)
    private val pendingCashAcks = MutableStateFlow(0)
    private val pendingImageAcks = MutableStateFlow(0)
    private val pendingLocationAcks = MutableStateFlow(0)

    // Track last connection state to avoid duplicate notifications
    private var lastConnectionState: WebSocketState? = null

    init {
        observeWebSocketEvents()
    }

    private fun observeWebSocketEvents() {
        // Observe connection state for errors
        // Note: StateFlow already guarantees distinctUntilChanged behavior
        webSocketRepository.connectionState
            .onEach { state -> handleConnectionStateChange(state) }
            .launchIn(scope)

        // Observe document acknowledgments
        webSocketRepository.documentAcks
            .onEach { ack ->
                when (ack.documentType) {
                    "order" -> pendingOrderAcks.value++
                    "cash" -> pendingCashAcks.value++
                    "image" -> pendingImageAcks.value++
                    "location" -> pendingLocationAcks.value++
                }
            }
            .launchIn(scope)

        // Aggregate acks with 2-second debounce
        setupAckAggregation(pendingOrderAcks, "order")
        setupAckAggregation(pendingCashAcks, "cash")
        setupAckAggregation(pendingImageAcks, "image")
        setupAckAggregation(pendingLocationAcks, "location")

        // Observe incoming catalog data
        webSocketRepository.incomingMessages
            .filter { msg ->
                msg.dataType == Constants.WEBSOCKET_DATA_TYPE_CATALOG &&
                        msg.status == "success"
            }
            .onEach { msg ->
                // Extract count from data if available
                val count = try {
                    msg.data.getAsJsonArray("data")?.size() ?: 1
                } catch (e: Exception) {
                    1
                }
                _snackbarEvents.send(WebSocketSnackbarEvent.CatalogReceived(msg.dataType, count))
            }
            .launchIn(scope)
    }

    private fun handleConnectionStateChange(state: WebSocketState) {
        // Skip if same as last state
        if (state == lastConnectionState) return
        lastConnectionState = state

        val event = when (state) {
            is WebSocketState.Error -> WebSocketSnackbarEvent.ConnectionError(state.error)
            is WebSocketState.LicenseError -> WebSocketSnackbarEvent.LicenseError(state.reason)
            is WebSocketState.Pending -> WebSocketSnackbarEvent.PendingApproval
            // Skip non-error states
            is WebSocketState.Connected,
            is WebSocketState.Connecting,
            is WebSocketState.Reconnecting,
            is WebSocketState.Disconnected -> null
        }

        event?.let { _snackbarEvents.send(it) }
    }

    private fun setupAckAggregation(pendingAcks: MutableStateFlow<Int>, type: String) {
        pendingAcks
            .debounce(2000) // 2-second aggregation window
            .filter { it > 0 }
            .onEach { count ->
                _snackbarEvents.send(WebSocketSnackbarEvent.DataSent(type, count))
                pendingAcks.value = 0
            }
            .launchIn(scope)
    }

    fun callDiffSync(scope: CoroutineScope, afterSync: () -> Unit) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _progressMessage.value = ""
        scope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.updateDifferential().collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        handleSyncResult(result)
                    }
                }
            }
            afterSync()
        }
    }

    fun callFullSync(scope: CoroutineScope, afterSync: () -> Unit) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _progressMessage.value = ""
        scope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.updateAll().collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        handleSyncResult(result)
                    }
                }
            }
            afterSync()
        }
    }

    fun callPrintDocument(scope: CoroutineScope, guid: String, cacheDir: File?, afterSync: (Boolean) -> Unit) {
        if (_isRefreshing.value) return afterSync(false)
        if (cacheDir == null) return afterSync(false)
        _isRefreshing.value = true
        _progressMessage.value = ""
        scope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.getPrintData(guid, cacheDir).collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        if (result is Result.Success || result is Result.Error) {
                            _isRefreshing.value = false
                            afterSync(result is Result.Success)
                        }
                    }
                }
            }
        }
    }

    private fun handleSyncResult(result: Result) {
        when (result) {
            is Result.Success -> {
                _isRefreshing.value = false
                _syncEvents.send(SyncEvent.Success(result.message))
            }
            is Result.Error -> {
                _isRefreshing.value = false
                _syncEvents.send(SyncEvent.Error(result.message))
                // Also emit snackbar event for HTTP sync errors
                _snackbarEvents.send(WebSocketSnackbarEvent.SyncError(result.message))
            }
            is Result.Progress -> {
                _syncEvents.send(SyncEvent.Progress(result.message))
            }
        }
    }

    fun addProgressText(text: String) {
        val current = _progressMessage.value
        _progressMessage.value = if (current.isBlank()) text else "$current\n$text"
    }
}
