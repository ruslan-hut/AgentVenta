package ua.com.programmer.agentventa.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.getWebSocketUrl
import ua.com.programmer.agentventa.data.local.entity.isValidForWebSocketConnection
import ua.com.programmer.agentventa.data.websocket.*
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.config.ApiKeyProvider
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of WebSocketRepository using OkHttp WebSocket client.
 * Manages WebSocket connection lifecycle, message delivery, and reconnection logic.
 *
 * Authentication:
 * - Uses API key from local.properties (shared across all app instances)
 * - Token format: Authorization: Bearer <API_KEY>:<DEVICE_UUID>
 * - API key validates request is from legitimate app
 * - Device UUID (UserAccount.guid) identifies individual device/account
 */
@Singleton
class WebSocketRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val logger: Logger,
    private val apiKeyProvider: ApiKeyProvider
) : WebSocketRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "WebSocket"

    // Connection state management
    private val _connectionState = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected)
    override val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    // Incoming message stream
    private val _incomingMessages = MutableSharedFlow<IncomingDataMessage>(replay = 0, extraBufferCapacity = 100)
    override val incomingMessages: Flow<IncomingDataMessage> = _incomingMessages.asSharedFlow()

    // WebSocket connection and state
    private var webSocket: WebSocket? = null
    private var currentAccount: UserAccount? = null
    private var reconnectionJob: Job? = null
    private var pingJob: Job? = null
    private var reconnectAttempt = 0
    private var isPendingDevice = false  // Flag to prevent reconnection for pending devices

    // Message tracking
    private val pendingMessages = ConcurrentHashMap<String, PendingMessage>()
    private val messageResults = ConcurrentHashMap<String, MutableSharedFlow<SendResult>>()

    override suspend fun connect(account: UserAccount): Boolean {
        if (isConnected()) {
            logger.d(TAG, "Already connected")
            return false
        }

        if (!account.isValidForWebSocketConnection()) {
            _connectionState.value = WebSocketState.Error(
                error = "Invalid account configuration for WebSocket",
                canRetry = false
            )
            return false
        }

        // Validate API key is configured
        if (!apiKeyProvider.hasWebSocketApiKey()) {
            logger.e(TAG, "WebSocket API key not configured in local.properties (WEBSOCKET_API_KEY)")
            _connectionState.value = WebSocketState.Error(
                error = "API key not configured",
                canRetry = false
            )
            return false
        }

        // Validate backend host is configured
        if (!apiKeyProvider.hasBackendHost()) {
            logger.e(TAG, "Backend host not configured in local.properties (KEY_HOST)")
            _connectionState.value = WebSocketState.Error(
                error = "Backend host not configured",
                canRetry = false
            )
            return false
        }

        logger.d(TAG, "Backend host: ${apiKeyProvider.backendHost}")
        logger.d(TAG, "API key: ${apiKeyProvider.getMaskedWebSocketApiKey()}")

        currentAccount = account
        reconnectAttempt = 0
        isPendingDevice = false  // Reset pending flag on new connection attempt
        cancelReconnection()

        return performConnection(account)
    }

    override suspend fun disconnect() {
        logger.d(TAG, "Disconnecting...")
        cancelReconnection()
        cancelPing()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        currentAccount = null
        isPendingDevice = false
        _connectionState.value = WebSocketState.Disconnected
        pendingMessages.clear()
        messageResults.clear()
    }

    override suspend fun sendData(dataType: String, data: String): Flow<SendResult> {
        val messageId = "${System.currentTimeMillis()}-${(0..9999).random()}"
        val resultFlow = MutableSharedFlow<SendResult>(replay = 1)
        messageResults[messageId] = resultFlow

        if (!isConnected()) {
            resultFlow.emit(SendResult.Failed(messageId, "Not connected", canRetry = true))
            return resultFlow.asSharedFlow()
        }

        try {
            val message = WebSocketMessageFactory.createDataMessage(dataType, data, messageId)
            val pending = PendingMessage(messageId, dataType, data)
            pendingMessages[messageId] = pending

            val sent = webSocket?.send(message) ?: false
            if (sent) {
                resultFlow.emit(SendResult.Sent(messageId))
                logger.d(TAG, "Message sent: $messageId")
            } else {
                pendingMessages.remove(messageId)
                resultFlow.emit(SendResult.Failed(messageId, "Failed to send", canRetry = true))
            }
        } catch (e: Exception) {
            pendingMessages.remove(messageId)
            resultFlow.emit(SendResult.Failed(messageId, e.message ?: "Unknown error", canRetry = true))
            logger.e(TAG, "Send error: ${e.message}")
        }

        return resultFlow.asSharedFlow()
    }

    override suspend fun reconnect(): Boolean {
        if (isConnected()) return false
        val account = currentAccount ?: return false

        cancelReconnection()
        reconnectAttempt = 0
        return performConnection(account)
    }

    override fun isConnected(): Boolean {
        return _connectionState.value is WebSocketState.Connected
    }

    override fun getPendingMessageCount(): Int {
        return pendingMessages.size
    }

    override suspend fun retryFailedMessages(): Int {
        if (!isConnected()) return 0

        var retryCount = 0
        pendingMessages.values.forEach { pending ->
            if (!pending.hasExceededRetries() && !pending.isExpired()) {
                try {
                    val message = WebSocketMessageFactory.createDataMessage(
                        pending.dataType,
                        pending.data,
                        pending.messageId
                    )
                    val sent = webSocket?.send(message) ?: false
                    if (sent) {
                        pendingMessages[pending.messageId] = pending.incrementRetry()
                        retryCount++
                    }
                } catch (e: Exception) {
                    logger.e(TAG, "Retry error: ${e.message}")
                }
            }
        }

        return retryCount
    }

    override suspend fun clearPendingMessages() {
        pendingMessages.clear()
        messageResults.clear()
    }

    // Private implementation methods

    private fun performConnection(account: UserAccount): Boolean {
        try {
            // Get backend host from ApiKeyProvider (predefined in local.properties)
            val backendHost = apiKeyProvider.backendHost
            if (backendHost.isEmpty()) {
                logger.e(TAG, "Backend host not configured in local.properties (KEY_HOST)")
                _connectionState.value = WebSocketState.Error(
                    "Backend host not configured",
                    canRetry = false
                )
                return false
            }

            // Build WebSocket URL using predefined backend host
            val url = account.getWebSocketUrl(backendHost)
            if (url.isEmpty()) {
                _connectionState.value = WebSocketState.Error("Invalid WebSocket URL", canRetry = false)
                return false
            }

            reconnectAttempt++
            _connectionState.value = WebSocketState.Connecting(reconnectAttempt)

            // Construct authentication token: <API_KEY>:<DEVICE_UUID>
            val apiKey = apiKeyProvider.webSocketApiKey
            val deviceUuid = account.guid
            val authToken = "$apiKey:$deviceUuid"

            logger.d(TAG, "Connecting to: $backendHost")
            logger.d(TAG, "Device UUID: $deviceUuid")

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $authToken")
                .build()

            webSocket = okHttpClient.newWebSocket(request, WebSocketListener())
            logger.d(TAG, "Connection initiated: $url")
            return true
        } catch (e: Exception) {
            logger.e(TAG, "Connection error: ${e.message}")
            _connectionState.value = WebSocketState.Error(e.message ?: "Connection failed", canRetry = true)
            scheduleReconnection()
            return false
        }
    }

    private fun scheduleReconnection() {
        val account = currentAccount ?: return

        // Don't schedule reconnection if device is pending
        if (isPendingDevice) {
            logger.d(TAG, "Skipping reconnection - device is pending approval")
            return
        }

        cancelReconnection()

        val delay = calculateBackoffDelay(reconnectAttempt)
        logger.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempt)")

        _connectionState.value = WebSocketState.Reconnecting(delay, reconnectAttempt)

        reconnectionJob = scope.launch {
            delay(delay.toLong())
            performConnection(account)
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Int {
        val baseDelay = Constants.WEBSOCKET_RECONNECT_INITIAL_DELAY
        val maxDelay = Constants.WEBSOCKET_RECONNECT_MAX_DELAY
        val calculatedDelay = baseDelay * (1 shl (attempt - 1).coerceAtMost(6))
        return calculatedDelay.coerceAtMost(maxDelay)
    }

    private fun cancelReconnection() {
        reconnectionJob?.cancel()
        reconnectionJob = null
    }

    private fun startPingScheduler() {
        cancelPing()
        pingJob = scope.launch {
            while (isActive && isConnected()) {
                delay(Constants.WEBSOCKET_PING_INTERVAL.toLong())
                try {
                    val pingMessage = WebSocketMessageFactory.createPingMessage()
                    webSocket?.send(pingMessage)
                    logger.d(TAG, "Ping sent")
                } catch (e: Exception) {
                    logger.e(TAG, "Ping error: ${e.message}")
                }
            }
        }
    }

    private fun cancelPing() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val message = WebSocketMessageFactory.parseMessage(text) ?: run {
                logger.w(TAG, "Failed to parse message")
                return
            }

            when (message.type) {
                Constants.WEBSOCKET_MESSAGE_TYPE_DATA -> {
                    val dataMessage = WebSocketMessageFactory.parseDataMessage(message)
                    if (dataMessage != null) {
                        logger.d(TAG, "Data received: ${dataMessage.dataType} (status: ${dataMessage.status})")

                        // Send acknowledgment
                        val ack = WebSocketMessageFactory.createAckMessage(dataMessage.messageId)
                        webSocket?.send(ack)

                        // Emit to subscribers
                        scope.launch {
                            _incomingMessages.emit(dataMessage)
                        }
                    }
                }

                Constants.WEBSOCKET_MESSAGE_TYPE_ACK -> {
                    val ackMessage = WebSocketMessageFactory.parseAckMessage(message)
                    if (ackMessage != null) {
                        logger.d(TAG, "Ack received: ${ackMessage.messageId} (status: ${ackMessage.status})")

                        pendingMessages.remove(ackMessage.messageId)
                        messageResults[ackMessage.messageId]?.let {
                            scope.launch {
                                it.emit(SendResult.Acknowledged(ackMessage.messageId))
                            }
                        }
                    }
                }

                Constants.WEBSOCKET_MESSAGE_TYPE_PING -> {
                    val pong = WebSocketMessageFactory.createPongMessage()
                    webSocket?.send(pong)
                }

                Constants.WEBSOCKET_MESSAGE_TYPE_PONG -> {
                    logger.d(TAG, "Pong received")
                }

                Constants.WEBSOCKET_MESSAGE_TYPE_ERROR -> {
                    val errorMessage = WebSocketMessageFactory.parseErrorMessage(message)
                    if (errorMessage != null) {
                        logger.e(TAG, "Server error: ${errorMessage.error} (status: ${errorMessage.status})")

                        // Check if device is pending approval
                        if (errorMessage.status == Constants.DEVICE_STATUS_PENDING) {
                            logger.w(TAG, "Device is pending approval - stopping reconnection attempts")
                            isPendingDevice = true  // Set flag to prevent reconnection
                            val deviceUuid = currentAccount?.guid ?: "unknown"
                            _connectionState.value = WebSocketState.Pending(deviceUuid)
                            // Cancel any scheduled reconnections
                            cancelReconnection()
                            return
                        }

                        // Check if device is denied
                        if (errorMessage.status == Constants.DEVICE_STATUS_DENIED) {
                            logger.e(TAG, "Device access has been denied")
                            _connectionState.value = WebSocketState.Error(
                                error = "Device access denied",
                                canRetry = false
                            )
                            cancelReconnection()
                            return
                        }

                        // Handle message-specific errors
                        if (errorMessage.messageId != null) {
                            pendingMessages.remove(errorMessage.messageId)
                            messageResults[errorMessage.messageId]?.let {
                                scope.launch {
                                    it.emit(SendResult.Failed(
                                        errorMessage.messageId,
                                        errorMessage.error,
                                        canRetry = true
                                    ))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Message handling error: ${e.message}")
        }
    }

    // OkHttp WebSocket listener
    private inner class WebSocketListener : okhttp3.WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            logger.d(TAG, "Connected successfully")
            reconnectAttempt = 0
            val deviceUuid = currentAccount?.guid ?: "unknown"
            _connectionState.value = WebSocketState.Connected(deviceUuid)
            startPingScheduler()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleIncomingMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            logger.d(TAG, "Closing: $code - $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger.d(TAG, "Closed: $code - $reason")
            cancelPing()

            // Don't reconnect if device is pending approval
            if (isPendingDevice) {
                logger.d(TAG, "Connection closed for pending device - no reconnection")
                return
            }

            if (code != 1000) { // Not a normal closure
                _connectionState.value = WebSocketState.Error("Connection closed: $reason", canRetry = true)
                scheduleReconnection()
            } else {
                _connectionState.value = WebSocketState.Disconnected
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger.e(TAG, "Connection failed: ${t.message}")
            cancelPing()

            // Don't reconnect if device is pending approval
            if (isPendingDevice) {
                logger.d(TAG, "Connection failed for pending device - no reconnection")
                return
            }

            _connectionState.value = WebSocketState.Error(
                t.message ?: "Connection failed",
                canRetry = true
            )
            scheduleReconnection()
        }
    }
}
