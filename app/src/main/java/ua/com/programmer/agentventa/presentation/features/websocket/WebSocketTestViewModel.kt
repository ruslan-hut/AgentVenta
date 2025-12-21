package ua.com.programmer.agentventa.presentation.features.websocket

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
    private val webSocketRepository: WebSocketRepository,
    private val userAccountRepository: UserAccountRepository,
    private val settingsSyncRepository: ua.com.programmer.agentventa.domain.repository.SettingsSyncRepository,
    private val connectionManager: WebSocketConnectionManager,
    private val pendingDataChecker: PendingDataChecker,
    private val logger: Logger
) : ViewModel() {

    private val TAG = "WSStatusViewModel"

    private val _connectionState = MutableLiveData<String>("Disconnected")
    val connectionState: LiveData<String> = _connectionState

    private val _pendingDataInfo = MutableLiveData<String>("")
    val pendingDataInfo: LiveData<String> = _pendingDataInfo

    private val _lastSyncTime = MutableLiveData<String>("Never")
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
                is WebSocketState.Connected -> "Connected (${state.deviceUuid.take(8)}...)"
                is WebSocketState.Connecting -> "Connecting... (attempt ${state.attempt})"
                is WebSocketState.Disconnected -> "Disconnected"
                is WebSocketState.Pending -> "Pending Approval"
                is WebSocketState.Error -> "Error: ${state.error}"
                is WebSocketState.Reconnecting -> "Reconnecting in ${state.delayMs / 1000}s"
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
                "Never"
            }
        }.launchIn(viewModelScope)

        // Listen for incoming messages
        webSocketRepository.incomingMessages.onEach { message ->
            addToLog("Received: ${message.dataType}")
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
            addToLog("Manual sync triggered...")

            try {
                connectionManager.checkAndConnect()
                addToLog("Sync check completed")
            } catch (e: Exception) {
                addToLog("Sync error: ${e.message}")
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
            return "No pending data"
        }

        val parts = mutableListOf<String>()
        if (summary.ordersCount > 0) parts.add("${summary.ordersCount} orders")
        if (summary.cashCount > 0) parts.add("${summary.cashCount} cash")
        if (summary.imagesCount > 0) parts.add("${summary.imagesCount} images")
        if (summary.locationsCount > 0) parts.add("${summary.locationsCount} locations")

        return "Pending: ${parts.joinToString(", ")}"
    }

    private fun addToLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        _messageLog.value = (_messageLog.value ?: emptyList()) + logEntry
    }
}
