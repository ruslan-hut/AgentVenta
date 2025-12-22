package ua.com.programmer.agentventa.presentation.features.websocket

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.websocket.WebSocketState
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.infrastructure.websocket.PendingDataChecker
import ua.com.programmer.agentventa.infrastructure.websocket.PendingDataSummary
import ua.com.programmer.agentventa.infrastructure.websocket.WebSocketConnectionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for WebSocket status screen.
 * Shows connection state, pending data counts, and allows manual sync trigger.
 * Connection is managed automatically by WebSocketConnectionManager.
 */
@HiltViewModel
class WebSocketTestViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webSocketRepository: WebSocketRepository,
    private val userAccountRepository: UserAccountRepository,
    private val settingsSyncRepository: ua.com.programmer.agentventa.domain.repository.SettingsSyncRepository,
    private val connectionManager: WebSocketConnectionManager,
    private val pendingDataChecker: PendingDataChecker,
    private val logger: Logger
) : ViewModel() {

    private val TAG = "WSStatusViewModel"

    private val _connectionState = MutableLiveData(context.getString(R.string.websocket_disconnected))
    val connectionState: LiveData<String> = _connectionState

    private val _pendingDataInfo = MutableLiveData<String>("")
    val pendingDataInfo: LiveData<String> = _pendingDataInfo

    private val _lastSyncTime = MutableLiveData(context.getString(R.string.websocket_never))
    val lastSyncTime: LiveData<String> = _lastSyncTime

    private val _messageLog = MutableLiveData<List<String>>(emptyList())
    val messageLog: LiveData<List<String>> = _messageLog

    private val _userEmail = MutableLiveData<String>("")
    val userEmail: LiveData<String> = _userEmail

    private val _settingsSyncStatus = MutableLiveData<String>("")
    val settingsSyncStatus: LiveData<String> = _settingsSyncStatus

    private val _isSyncing = MutableLiveData<Boolean>(false)
    val isSyncing: LiveData<Boolean> = _isSyncing

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    init {
        // Observe WebSocket connection state
        webSocketRepository.connectionState.onEach { state ->
            _connectionState.value = when (state) {
                is WebSocketState.Connected -> context.getString(R.string.websocket_connected, "${state.deviceUuid.take(8)}…")
                is WebSocketState.Connecting -> context.getString(R.string.websocket_connecting, state.attempt)
                is WebSocketState.Disconnected -> context.getString(R.string.websocket_disconnected)
                is WebSocketState.Pending -> context.getString(R.string.websocket_pending_approval)
                is WebSocketState.Error -> context.getString(R.string.websocket_error, state.error)
                is WebSocketState.Reconnecting -> context.getString(R.string.websocket_reconnecting, state.delayMs / 1000)
            }
        }.launchIn(viewModelScope)

        // Observe pending data summary
        connectionManager.pendingDataSummary.onEach { summary ->
            _pendingDataInfo.value = formatPendingDataSummary(summary)
        }.launchIn(viewModelScope)

        // Observe last sync time
        connectionManager.lastSyncTime.onEach { timestamp ->
            _lastSyncTime.value = if (timestamp > 0) {
                fullDateFormat.format(Date(timestamp))
            } else {
                context.getString(R.string.websocket_never)
            }
        }.launchIn(viewModelScope)

        // Listen for incoming messages
        webSocketRepository.incomingMessages.onEach { message ->
            addToLog(context.getString(R.string.websocket_received, message.dataType))
        }.launchIn(viewModelScope)

        // Load initial pending data count
        refreshPendingData()
    }

    /**
     * Trigger immediate sync check and connection.
     */
    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            addToLog(context.getString(R.string.websocket_sync_triggered))

            try {
                connectionManager.checkAndConnect()
                addToLog(context.getString(R.string.websocket_sync_completed))
            } catch (e: Exception) {
                addToLog(context.getString(R.string.websocket_sync_error, e.message ?: ""))
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Refresh pending data counts.
     */
    fun refreshPendingData() {
        viewModelScope.launch {
            val summary = pendingDataChecker.getPendingDataSummary()
            _pendingDataInfo.value = formatPendingDataSummary(summary)
        }
    }

    fun clearLog() {
        _messageLog.value = emptyList()
    }

    fun uploadSettings() {
        viewModelScope.launch {
            val email = _userEmail.value?.trim()
            if (email.isNullOrEmpty()) {
                _settingsSyncStatus.value = "Please enter user email"
                addToLog("Upload failed: Email required")
                return@launch
            }

            val account = userAccountRepository.getCurrent()
            if (account == null) {
                _settingsSyncStatus.value = "No account configured"
                addToLog("Upload failed: No account")
                return@launch
            }

            _settingsSyncStatus.value = "Uploading settings..."
            addToLog("Uploading settings for: $email")

            val options = ua.com.programmer.agentventa.presentation.features.settings.UserOptionsBuilder.build(account)

            settingsSyncRepository.uploadSettings(email, account, options).collect { result ->
                when (result) {
                    is ua.com.programmer.agentventa.data.websocket.SettingsSyncResult.Success -> {
                        _settingsSyncStatus.value = "Settings uploaded"
                        addToLog("Settings uploaded successfully")
                    }
                    is ua.com.programmer.agentventa.data.websocket.SettingsSyncResult.Error -> {
                        _settingsSyncStatus.value = "Upload failed"
                        addToLog("Upload error: ${result.message}")
                    }
                    is ua.com.programmer.agentventa.data.websocket.SettingsSyncResult.NotFound -> {
                        _settingsSyncStatus.value = "Unexpected result"
                        addToLog("Unexpected: NotFound on upload")
                    }
                }
            }
        }
    }

    fun downloadSettings() {
        viewModelScope.launch {
            val email = _userEmail.value?.trim()
            if (email.isNullOrEmpty()) {
                _settingsSyncStatus.value = "Please enter user email"
                addToLog("Download failed: Email required")
                return@launch
            }

            _settingsSyncStatus.value = "Downloading settings..."
            addToLog("Requesting settings for: $email")

            settingsSyncRepository.downloadSettings(email).collect { result ->
                when (result) {
                    is ua.com.programmer.agentventa.data.websocket.SettingsSyncResult.Success -> {
                        _settingsSyncStatus.value = "Settings downloaded"
                        addToLog("Settings received")
                    }
                    is ua.com.programmer.agentventa.data.websocket.SettingsSyncResult.NotFound -> {
                        _settingsSyncStatus.value = "Settings not found"
                        addToLog("No settings found for: ${result.userEmail}")
                    }
                    is ua.com.programmer.agentventa.data.websocket.SettingsSyncResult.Error -> {
                        _settingsSyncStatus.value = "Download failed"
                        addToLog("Download error: ${result.message}")
                    }
                }
            }
        }
    }

    fun setUserEmail(email: String) {
        _userEmail.value = email
    }

    private fun formatPendingDataSummary(summary: PendingDataSummary): String {
        if (!summary.hasPendingData) {
            return context.getString(R.string.websocket_no_pending_data)
        }

        val parts = mutableListOf<String>()
        if (summary.ordersCount > 0) parts.add(context.getString(R.string.websocket_orders_count, summary.ordersCount))
        if (summary.cashCount > 0) parts.add(context.getString(R.string.websocket_cash_count, summary.cashCount))
        if (summary.imagesCount > 0) parts.add(context.getString(R.string.websocket_images_count, summary.imagesCount))
        if (summary.locationsCount > 0) parts.add(context.getString(R.string.websocket_locations_count, summary.locationsCount))

        return context.getString(R.string.websocket_pending_format, parts.joinToString(", "))
    }

    private fun addToLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        _messageLog.value = (_messageLog.value ?: emptyList()) + logEntry
    }
}
