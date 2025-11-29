package ua.com.programmer.agentventa.presentation.features.websocket

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.data.local.entity.getWebSocketUrl
import ua.com.programmer.agentventa.data.websocket.SendResult
import ua.com.programmer.agentventa.data.websocket.WebSocketState
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WebSocketTestViewModel @Inject constructor(
    private val webSocketRepository: WebSocketRepository,
    private val userAccountRepository: UserAccountRepository,
    private val logger: Logger,
    private val apiKeyProvider: ua.com.programmer.agentventa.infrastructure.config.ApiKeyProvider
) : ViewModel() {

    private val TAG = "WSTestViewModel"

    private val _connectionState = MutableLiveData<String>("Disconnected")
    val connectionState: LiveData<String> = _connectionState

    private val _messageLog = MutableLiveData<List<String>>(emptyList())
    val messageLog: LiveData<List<String>> = _messageLog

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        // Observe WebSocket connection state
        webSocketRepository.connectionState.onEach { state ->
            _connectionState.value = when (state) {
                is WebSocketState.Connected -> "✓ Connected (UUID: ${state.deviceUuid.take(8)})"
                is WebSocketState.Connecting -> "⟳ Connecting... (attempt ${state.attempt})"
                is WebSocketState.Disconnected -> "✗ Disconnected"
                is WebSocketState.Pending -> "⏸ Pending Approval (UUID: ${state.deviceUuid.take(8)})"
                is WebSocketState.Error -> "✗ Error: ${state.error}"
                is WebSocketState.Reconnecting -> "⟳ Reconnecting in ${state.delayMs}ms (attempt ${state.attempt})"
            }
        }.launchIn(viewModelScope)

        // Listen for incoming messages
        webSocketRepository.incomingMessages.onEach { message ->
            addToLog("← Received: type=${message.dataType}, id=${message.messageId}")
            logger.d(TAG, "Received message: ${message.dataType}")
        }.launchIn(viewModelScope)
    }

    fun connect() {
        viewModelScope.launch {
            val account = userAccountRepository.getCurrent()
            if (account != null && account.dataFormat == Constants.SYNC_FORMAT_WEBSOCKET) {
                val backendHost = apiKeyProvider.backendHost
                val wsUrl = account.getWebSocketUrl(backendHost)

                addToLog("→ Connecting...")
                addToLog("  Backend: $backendHost")
                addToLog("  Device UUID: ${account.guid}")
                addToLog("  URL: $wsUrl")

                val success = webSocketRepository.connect(account)
                if (!success) {
                    addToLog("✗ Connection failed - check configuration")
                }
            } else {
                addToLog("✗ Error: No WebSocket account configured")
                addToLog("  Please configure a WebSocket account first")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            addToLog("→ Disconnecting...")
            webSocketRepository.disconnect()
        }
    }

    fun sendTestMessage() {
        viewModelScope.launch {
            val payload = mapOf(
                "test" to "Hello from Android",
                "timestamp" to System.currentTimeMillis().toString(),
                "data_type" to "test_message"
            )

            addToLog("→ Sending test message...")

            // Convert map to JSON string
            val jsonPayload = com.google.gson.Gson().toJson(payload)

            webSocketRepository.sendData(
                dataType = Constants.DATA_OPTIONS,
                data = jsonPayload
            ).collect { result ->
                when (result) {
                    is SendResult.Pending -> {
                        addToLog("  ⏳ Message pending: ${result.messageId.takeLast(8)}")
                    }
                    is SendResult.Sent -> {
                        addToLog("  ✓ Message sent: ${result.messageId.takeLast(8)}")
                    }
                    is SendResult.Acknowledged -> {
                        addToLog("  ✓ Message acknowledged: ${result.messageId.takeLast(8)}")
                    }
                    is SendResult.Failed -> {
                        addToLog("  ✗ Send failed: ${result.error}")
                    }
                }
            }
        }
    }

    fun clearLog() {
        _messageLog.value = emptyList()
    }

    private fun addToLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        _messageLog.value = (_messageLog.value ?: emptyList()) + logEntry
    }
}
