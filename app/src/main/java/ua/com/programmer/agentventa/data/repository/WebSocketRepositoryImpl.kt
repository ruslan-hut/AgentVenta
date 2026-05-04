package ua.com.programmer.agentventa.data.repository

import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.getWebSocketUrl
import ua.com.programmer.agentventa.data.local.entity.isValidForWebSocketConnection
import ua.com.programmer.agentventa.data.websocket.*
import ua.com.programmer.agentventa.domain.repository.DataExchangeRepository
import ua.com.programmer.agentventa.domain.repository.WebSocketRepository
import ua.com.programmer.agentventa.infrastructure.config.ApiKeyProvider
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.utility.Constants
import ua.com.programmer.agentventa.utility.XMap
import android.content.SharedPreferences
import com.google.gson.Gson
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
    private val apiKeyProvider: ApiKeyProvider,
    private val dataExchangeRepository: DataExchangeRepository,
    private val userAccountRepository: ua.com.programmer.agentventa.domain.repository.UserAccountRepository,
    private val sharedPreferences: SharedPreferences
) : WebSocketRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "AV-WebSocket"

    // Serializes catalog apply + cleanup so that: (a) multiple incoming WS data
    // messages process in arrival order (saves cannot overlap), and (b) the
    // post-batch cleanup runs strictly after every save in the same batch.
    // Without this, batch_complete in message K could fire while message K-1's
    // save is still in flight, and cleanup would delete rows that were just
    // written.
    private val catalogProcessingMutex = Mutex()

    // Counters aggregated across catalog chunks within one batch. Logged once
    // on batch_complete so per-chunk save events do not spam the log.
    // Mutated only under [catalogProcessingMutex].
    private val batchCounters = mutableMapOf<String, Int>()

    // Serializes connect/disconnect so an account switch (or rapid foreground/
    // background flap) cannot leave two live WebSocket instances. Without this,
    // a checkAndConnect() racing with disconnect() could open a new socket while
    // the old one is still being closed; both listeners would write to the same
    // pendingMessages/currentAccount fields, and only the listener-identity
    // guard masks the symptom.
    private val connectionMutex = Mutex()

    // Connection state management
    private val _connectionState = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected)
    override val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    // Last successful license check timestamp (persisted)
    private val _lastLicenseCheckTime = MutableStateFlow(
        sharedPreferences.getLong(Constants.PREF_LAST_LICENSE_CHECK, 0L)
    )
    override val lastLicenseCheckTime: StateFlow<Long> = _lastLicenseCheckTime.asStateFlow()

    // Incoming message stream
    private val _incomingMessages = MutableSharedFlow<IncomingDataMessage>(replay = 0, extraBufferCapacity = 100)
    override val incomingMessages: Flow<IncomingDataMessage> = _incomingMessages.asSharedFlow()

    // Document acknowledgment stream for notifying about successfully uploaded documents
    private val _documentAcks = MutableSharedFlow<DocumentAck>(replay = 0, extraBufferCapacity = 100)
    override val documentAcks: SharedFlow<DocumentAck> = _documentAcks.asSharedFlow()

    // Batch complete stream: server signals all catalog data has been pushed
    private val _batchComplete = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 10)
    override val batchComplete: SharedFlow<Long> = _batchComplete.asSharedFlow()

    // WebSocket connection and state
    private var webSocket: WebSocket? = null
    private var currentAccount: UserAccount? = null
    private var reconnectionJob: Job? = null
    private var pingJob: Job? = null
    private var reconnectAttempt = 0
    private var isPendingDevice = false  // Flag to prevent reconnection for pending devices
    private var isLicenseError = false   // Flag to prevent reconnection for license errors

    // Message tracking
    private val pendingMessages = ConcurrentHashMap<String, PendingMessage>()
    private val messageResults = ConcurrentHashMap<String, MutableSharedFlow<SendResult>>()

    // Current account GUID for tagging incoming data
    @Volatile
    private var currentAccountGuid: String? = null

    override suspend fun connect(account: UserAccount): Boolean = connectionMutex.withLock {
        if (isOccupied()) {
            logger.d(TAG, "Skipping connect: state=${_connectionState.value::class.simpleName}")
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

        // Backend host and API key available via apiKeyProvider (not logged to reduce noise)

        currentAccount = account
        reconnectAttempt = 0
        isPendingDevice = false  // Reset pending flag on new connection attempt
        isLicenseError = false   // Reset license error flag on new connection attempt
        cancelReconnection()

        return openSocketLocked(account)
    }

    /**
     * State check used by all connect paths to avoid opening a duplicate socket.
     * Treats Connecting and Reconnecting as "already in progress" — without this
     * a second connect() racing in between performConnection() and onOpen would
     * pass an isConnected()==false check and create a parallel socket, leaving
     * the server with two connections for the same UUID.
     * Must be called while [connectionMutex] is held.
     */
    private fun isOccupied(): Boolean {
        return when (_connectionState.value) {
            is WebSocketState.Connected,
            is WebSocketState.Connecting,
            is WebSocketState.Reconnecting -> true
            else -> false
        }
    }

    /**
     * Single point of WebSocket creation. Closes any leftover socket reference
     * before opening a new one so the server never sees two live connections
     * for the same UUID. Must be called while [connectionMutex] is held.
     */
    private fun openSocketLocked(account: UserAccount): Boolean {
        webSocket?.let { existing ->
            logger.d(TAG, "Closing leftover socket before new connect")
            try {
                // cancel() bypasses the close handshake — we do not want the
                // peer to interpret this as the "current" connection still
                // being alive while the new one is being established.
                existing.cancel()
            } catch (e: Exception) {
                logger.w(TAG, "Error cancelling leftover socket: ${e.message}")
            }
            webSocket = null
        }
        return performConnection(account)
    }

    override suspend fun disconnect() = connectionMutex.withLock {
        logger.d(TAG, "Disconnecting...")
        cancelReconnection()
        cancelPing()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        currentAccount = null
        isPendingDevice = false
        isLicenseError = false
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
                messageResults.remove(messageId)
                resultFlow.emit(SendResult.Failed(messageId, "Failed to send", canRetry = true))
            }
        } catch (e: Exception) {
            pendingMessages.remove(messageId)
            messageResults.remove(messageId)
            resultFlow.emit(SendResult.Failed(messageId, e.message ?: "Unknown error", canRetry = true))
            logger.e(TAG, "Send error: ${e.message}")
        }

        return resultFlow.asSharedFlow()
    }

    override suspend fun sendMessage(type: String, payload: String): Flow<SendResult> {
        val messageId = "${System.currentTimeMillis()}-${(0..9999).random()}"
        val resultFlow = MutableSharedFlow<SendResult>(replay = 1)
        messageResults[messageId] = resultFlow

        if (!isConnected()) {
            resultFlow.emit(SendResult.Failed(messageId, "Not connected", canRetry = true))
            return resultFlow.asSharedFlow()
        }

        try {
            val message = WebSocketMessageFactory.createMessage(type, payload, messageId)

            // Extract document_guid from payload for document upload messages
            val documentGuid = extractDocumentGuid(type, payload)

            // Track pending message for document uploads
            if (documentGuid != null) {
                val pending = PendingMessage(
                    messageId = messageId,
                    dataType = type,
                    data = payload,
                    documentGuid = documentGuid
                )
                pendingMessages[messageId] = pending
            }

            val sent = webSocket?.send(message) ?: false
            if (sent) {
                resultFlow.emit(SendResult.Sent(messageId))
                logger.d(TAG, "Message sent: type=$type, id=$messageId${if (documentGuid != null) ", doc=$documentGuid" else ""}")
            } else {
                resultFlow.emit(SendResult.Failed(messageId, "Failed to send", canRetry = true))
                pendingMessages.remove(messageId)
                messageResults.remove(messageId)
            }
        } catch (e: Exception) {
            resultFlow.emit(SendResult.Failed(messageId, e.message ?: "Unknown error", canRetry = true))
            pendingMessages.remove(messageId)
            messageResults.remove(messageId)
            logger.e(TAG, "Send error: ${e.message}")
        }

        return resultFlow.asSharedFlow()
    }

    /**
     * Extracts document_guid from payload for document upload messages.
     * Returns null for non-document messages.
     */
    private fun extractDocumentGuid(type: String, payload: String): String? {
        val documentTypes = listOf(
            Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_ORDER,
            Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_CASH,
            Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_IMAGE,
            Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_LOCATION
        )

        if (type !in documentTypes) return null

        return try {
            val gson = Gson()
            val json = gson.fromJson(payload, com.google.gson.JsonObject::class.java)
            json.get("document_guid")?.asString
        } catch (e: Exception) {
            logger.w(TAG, "Failed to extract document_guid from payload: ${e.message}")
            null
        }
    }

    /**
     * Maps WebSocket message type to document type for DocumentAck.
     * Returns null for non-document message types.
     */
    private fun mapMessageTypeToDocumentType(messageType: String): String? {
        return when (messageType) {
            Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_ORDER -> "order"
            Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_CASH -> "cash"
            Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_IMAGE -> "image"
            Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_LOCATION -> "location"
            else -> null
        }
    }

    override suspend fun reconnect(): Boolean = connectionMutex.withLock {
        if (isOccupied()) {
            logger.d(TAG, "Skipping reconnect: state=${_connectionState.value::class.simpleName}")
            return@withLock false
        }
        val account = currentAccount ?: return@withLock false

        cancelReconnection()
        reconnectAttempt = 0
        return@withLock openSocketLocked(account)
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
        // Snapshot the IDs first; we may mutate the map while iterating.
        val ids = pendingMessages.keys.toList()
        for (id in ids) {
            val pending = pendingMessages[id] ?: continue
            // Drop expired or out-of-retries entries so the maps don't grow
            // unboundedly when the relay never ACKs.
            if (pending.hasExceededRetries() || pending.isExpired()) {
                pendingMessages.remove(id)
                messageResults.remove(id)
                continue
            }
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

        return retryCount
    }

    override suspend fun clearPendingMessages() {
        pendingMessages.clear()
        messageResults.clear()
    }

    override fun setCurrentAccountGuid(guid: String) {
        currentAccountGuid = guid
        logger.d(TAG, "Current account GUID set: ${guid.take(8)}...")
        recoverPendingCleanup(guid)
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
            val baseUrl = account.getWebSocketUrl(backendHost)
            if (baseUrl.isEmpty()) {
                _connectionState.value = WebSocketState.Error("Invalid WebSocket URL", canRetry = false)
                return false
            }

            reconnectAttempt++
            _connectionState.value = WebSocketState.Connecting(reconnectAttempt)

            // Construct authentication token: <API_KEY>:<DEVICE_UUID>
            val apiKey = apiKeyProvider.webSocketApiKey
            val deviceUuid = account.guid
            val authToken = "$apiKey:$deviceUuid"

            // Encode app_parameters as base64 query parameter so the server
            // receives them on initial connect (before any WebSocket messages)
            val appParamsJson = Gson().toJson(buildAppParameters(account))
            val appParamsBase64 = Base64.encodeToString(
                appParamsJson.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
            val url = "$baseUrl?app_parameters=$appParamsBase64"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $authToken")
                .build()

            webSocket = okHttpClient.newWebSocket(request, WebSocketListener())
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

        // Don't schedule reconnection if license error
        if (isLicenseError) {
            logger.d(TAG, "Skipping reconnection - license error")
            return
        }

        cancelReconnection()

        val delay = calculateBackoffDelay(reconnectAttempt)
        // Reconnection delay and attempt tracked via WebSocketState.Reconnecting

        _connectionState.value = WebSocketState.Reconnecting(delay, reconnectAttempt)

        reconnectionJob = scope.launch {
            delay(delay.toLong())
            // Route through the mutex + state check so a manual connect()
            // racing with this fired job cannot result in two open sockets.
            connectionMutex.withLock {
                // State may have changed between scheduling and firing — for
                // example, disconnect() ran (Disconnected) or another connect
                // succeeded (Connected). In the latter case bail.
                if (_connectionState.value is WebSocketState.Connected ||
                    _connectionState.value is WebSocketState.Connecting) {
                    return@withLock
                }
                openSocketLocked(account)
            }
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Int {
        val baseDelay = Constants.WEBSOCKET_RECONNECT_INITIAL_DELAY
        val maxDelay = Constants.WEBSOCKET_RECONNECT_MAX_DELAY
        val calculatedDelay = baseDelay * (1 shl (attempt - 1).coerceAtMost(6))
        return calculatedDelay.coerceAtMost(maxDelay)
    }

    override fun cancelReconnection() {
        reconnectionJob?.cancel()
        reconnectionJob = null
    }

    private fun startPingScheduler() {
        cancelPing()
        pingJob = scope.launch {
            // Send first ping immediately so server receives app_parameters
            // even if connection is short-lived (e.g. pending devices)
            try {
                val accountData = buildPingAccountData()
                val pingMessage = WebSocketMessageFactory.createPingMessage(accountData)
                webSocket?.send(pingMessage)
            } catch (e: Exception) {
                logger.e(TAG, "Initial ping error: ${e.message}")
            }

            while (isActive && isConnected()) {
                delay(Constants.WEBSOCKET_PING_INTERVAL.toLong())
                try {
                    val accountData = buildPingAccountData()
                    val pingMessage = WebSocketMessageFactory.createPingMessage(accountData)
                    webSocket?.send(pingMessage)
                } catch (e: Exception) {
                    logger.e(TAG, "Ping error: ${e.message}")
                }
            }
        }
    }

    /**
     * Builds account data map for inclusion in ping messages.
     * Wraps UserAccount fields in "app_parameters" object so the server
     * can store them as device.app_params.
     */
    private fun buildPingAccountData(): Map<String, Any?> {
        val account = currentAccount ?: return emptyMap()

        val appParameters = buildMap {
            put("device_uuid", account.guid)
            put("description", account.description)
            put("license", account.license)
            put("data_format", account.dataFormat)
            put("db_server", account.dbServer)
            put("db_name", account.dbName)
            put("db_user", account.dbUser)
            put("db_password", account.dbPassword)
            put("use_websocket", account.useWebSocket)
            WebSocketMessageFactory.parseOptionsToJson(account.options)?.let {
                put("options", it)
            }
        }

        return mapOf("app_parameters" to appParameters)
    }

    /**
     * Builds app_parameters map from a UserAccount for URL query parameter encoding.
     * Used during initial WebSocket connection so the server receives device info
     * before any WebSocket messages are exchanged.
     */
    private fun buildAppParameters(account: UserAccount): Map<String, Any?> {
        return buildMap {
            put("device_uuid", account.guid)
            put("description", account.description)
            put("license", account.license)
            put("data_format", account.dataFormat)
            put("db_server", account.dbServer)
            put("db_name", account.dbName)
            put("db_user", account.dbUser)
            put("db_password", account.dbPassword)
            put("use_websocket", account.useWebSocket)
            WebSocketMessageFactory.parseOptionsToJson(account.options)?.let {
                put("options", it)
            }
        }
    }

    private fun cancelPing() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun handleIncomingMessage(text: String) {
        val messages = WebSocketMessageFactory.parseMessages(text)
        if (messages.isEmpty()) {
            logger.e(TAG, "Failed to parse WebSocket message")
            return
        }
        for (message in messages) {
            handleSingleMessage(message)
        }
    }

    private fun handleSingleMessage(message: WebSocketMessage) {
        try {
            when (message.type) {
                Constants.WEBSOCKET_MESSAGE_TYPE_DATA -> {
                    val dataMessage = WebSocketMessageFactory.parseDataMessage(message)
                    if (dataMessage != null) {
                        // Handle unified array payload - route by examining value_id in items
                        when (dataMessage.dataType) {
                            Constants.WEBSOCKET_DATA_TYPE_CATALOG -> {
                                // Simplified format: process array and route based on value_id
                                handleUnifiedPayload(dataMessage)
                            }
                            else -> {
                                // Legacy format or specific handlers (settings, etc.)
                                // Send acknowledgment
                                val ack = WebSocketMessageFactory.createAckMessage(dataMessage.messageId)
                                webSocket?.send(ack)

                                // Emit to subscribers
                                scope.launch {
                                    _incomingMessages.emit(dataMessage)
                                }
                            }
                        }
                    } else {
                        logger.e(TAG, "Failed to parse data message")
                    }
                }

                Constants.WEBSOCKET_MESSAGE_TYPE_ACK -> {
                    val ackMessage = WebSocketMessageFactory.parseAckMessage(message)
                    if (ackMessage != null) {
                        logger.d(TAG, "Ack received: ${ackMessage.messageId} (status: ${ackMessage.status})")

                        // Check if this ACK is for a document upload and emit DocumentAck
                        val pendingMessage = pendingMessages.remove(ackMessage.messageId)
                        if (pendingMessage?.documentGuid != null) {
                            val documentType = mapMessageTypeToDocumentType(pendingMessage.dataType)
                            if (documentType != null) {
                                scope.launch {
                                    _documentAcks.emit(DocumentAck(
                                        documentType = documentType,
                                        documentGuid = pendingMessage.documentGuid,
                                        messageId = ackMessage.messageId
                                    ))
                                    logger.d(TAG, "Document ACK emitted: type=$documentType, guid=${pendingMessage.documentGuid}")
                                }
                            }
                        }

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
                    handlePongMessage(message)
                }

                Constants.WEBSOCKET_MESSAGE_TYPE_ERROR -> {
                    val errorMessage = WebSocketMessageFactory.parseErrorMessage(message)
                    if (errorMessage != null) {
                        logger.e(TAG, "Server error: ${errorMessage.error} (status: ${errorMessage.status}, reason: ${errorMessage.reason})")

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

                        // Check for license-specific errors
                        when (errorMessage.error) {
                            Constants.LICENSE_ERROR_EXPIRED -> {
                                logger.e(TAG, "License expired: ${errorMessage.reason}")
                                isLicenseError = true  // Set flag to prevent reconnection
                                _connectionState.value = WebSocketState.LicenseError(
                                    errorCode = errorMessage.error,
                                    reason = errorMessage.reason ?: "License expired"
                                )
                                cancelReconnection()
                                return
                            }
                            Constants.LICENSE_ERROR_NOT_ACTIVE -> {
                                logger.e(TAG, "License not active: ${errorMessage.reason}")
                                isLicenseError = true  // Set flag to prevent reconnection
                                _connectionState.value = WebSocketState.LicenseError(
                                    errorCode = errorMessage.error,
                                    reason = errorMessage.reason ?: "License not active"
                                )
                                cancelReconnection()
                                return
                            }
                            Constants.LICENSE_ERROR_DEVICE_LIMIT -> {
                                logger.e(TAG, "Device limit reached: ${errorMessage.reason}")
                                isLicenseError = true  // Set flag to prevent reconnection
                                _connectionState.value = WebSocketState.LicenseError(
                                    errorCode = errorMessage.error,
                                    reason = errorMessage.reason ?: "Device limit reached"
                                )
                                cancelReconnection()
                                return
                            }
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

    /**
     * Handles unified payload with simplified array format.
     * Processes array where each item has a value_id to identify its type.
     * Routes items to appropriate handlers based on value_id.
     *
     * Expected format:
     * {
     *   "type": "data",
     *   "payload": [
     *     { "value_id": "options", ... },
     *     { "value_id": "clients", ... },
     *     { "value_id": "goods", ... }
     *   ]
     * }
     */
    private fun handleUnifiedPayload(dataMessage: IncomingDataMessage) {
        scope.launch {
            // Serialize with other catalog messages and with cleanup replay so
            // batch_complete in a later message cannot overtake an in-flight
            // save from an earlier message.
            catalogProcessingMutex.withLock {
                processUnifiedPayloadLocked(dataMessage)
            }
        }
    }

    private suspend fun processUnifiedPayloadLocked(dataMessage: IncomingDataMessage) {
        try {
            // Extract data array from the message
            val dataArray = dataMessage.data.getAsJsonArray("data")
            if (dataArray == null || dataArray.size() == 0) {
                logger.w(TAG, "Empty data array in unified payload")
                sendAck(dataMessage.messageId, "unified", 0)
                return
            }

            // Get current account guid for database operations
            val accountGuid = currentAccountGuid ?: currentAccount?.guid ?: run {
                logger.e(TAG, "No current account for unified payload")
                return
            }

            val optionsItems = mutableListOf<com.google.gson.JsonObject>()
            val counters = mutableMapOf<String, Int>()
            val batchSize = 500
            val batch = ArrayList<XMap>(batchSize)
            val gson = Gson()
            var batchCompleteTimestamp: Long? = null

            for (i in 0 until dataArray.size()) {
                try {
                    val item = dataArray.get(i).asJsonObject

                    // Check for batch_complete sentinel from server
                    val itemType = item.get("type")?.asString
                    if (itemType == Constants.VALUE_ID_BATCH_COMPLETE) {
                        batchCompleteTimestamp = item.get("timestamp")?.asLong
                        logger.d(TAG, "Batch complete signal received, timestamp=$batchCompleteTimestamp")
                        continue
                    }

                    val valueId = item.get("value_id")?.asString

                    if (valueId.isNullOrEmpty()) {
                        logger.w(TAG, "Item $i missing value_id, skipping")
                        continue
                    }

                    when (valueId) {
                        Constants.VALUE_ID_OPTIONS -> {
                            optionsItems.add(item)
                        }
                        else -> {
                            // 1C embeds a UTC timestamp in every data element;
                            // XMap picks it up from the map automatically.
                            // We only set databaseId (account GUID) here.
                            val itemMap = gson.fromJson(item, Map::class.java) as Map<*, *>
                            val xMap = XMap(itemMap)
                            xMap.setDatabaseId(accountGuid)
                            batch.add(xMap)
                        }
                    }

                    counters[valueId] = (counters[valueId] ?: 0) + 1

                    // Save in batches to avoid holding all items in memory
                    if (batch.size >= batchSize) {
                        dataExchangeRepository.saveData(batch)
                        batch.clear()
                    }
                } catch (e: Exception) {
                    logger.e(TAG, "Error processing item $i: ${e.message}")
                }
            }

            // Save remaining items
            if (batch.isNotEmpty()) {
                dataExchangeRepository.saveData(batch)
                batch.clear()
            }

            // Process options items
            if (optionsItems.isNotEmpty()) {
                processOptionsItems(optionsItems, dataMessage.messageId)
            }

            val totalItems = counters.values.sum() - optionsItems.size
            if (totalItems > 0 || optionsItems.isNotEmpty()) {
                for ((key, value) in counters) {
                    if (key == Constants.VALUE_ID_OPTIONS) continue
                    batchCounters[key] = (batchCounters[key] ?: 0) + value
                }
                sendAck(dataMessage.messageId, "catalog", totalItems)
            } else if (batchCompleteTimestamp == null) {
                logger.w(TAG, "No valid items in unified payload")
                sendAck(dataMessage.messageId, "unified", 0)
            } else {
                sendAck(dataMessage.messageId, "batch_complete", 0)
            }

            // After every item from this batch is persisted, run cleanup for
            // stale catalog rows. We write a crash-recovery checkpoint first so
            // that if the process dies between save and cleanup, the next
            // session replays the same cleanup exactly once.
            if (batchCompleteTimestamp != null) {
                val batchTotal = batchCounters.values.sum()
                if (batchTotal > 0) {
                    logger.d(TAG, "Saved $batchTotal catalog items: $batchCounters")
                }
                batchCounters.clear()
                runBatchCleanup(accountGuid, batchCompleteTimestamp)
                _batchComplete.emit(batchCompleteTimestamp)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error handling unified payload: ${e.message}")
            sendError(dataMessage.messageId, "unified", e.message ?: "Processing failed")
        }
    }

    /**
     * Runs the timestamp-based cleanup for stale catalog rows with a persisted
     * checkpoint so a crash mid-cleanup can be resumed on the next session.
     * Must be called under [catalogProcessingMutex].
     */
    private suspend fun runBatchCleanup(accountGuid: String, timestamp: Long) {
        try {
            sharedPreferences.edit()
                .putLong(Constants.PREF_PENDING_CLEANUP_TIMESTAMP, timestamp)
                .putString(Constants.PREF_PENDING_CLEANUP_ACCOUNT, accountGuid)
                .apply()

            dataExchangeRepository.cleanUp(accountGuid, timestamp)

            sharedPreferences.edit()
                .remove(Constants.PREF_PENDING_CLEANUP_TIMESTAMP)
                .remove(Constants.PREF_PENDING_CLEANUP_ACCOUNT)
                .apply()
        } catch (e: Exception) {
            logger.e(TAG, "Batch cleanup failed (will retry next session): ${e.message}")
        }
    }

    /**
     * Replays a pending cleanup written by a prior session that was killed
     * between the save and the cleanup. No-op unless a checkpoint exists for
     * the given account. Safe to call multiple times — the checkpoint is
     * cleared on success.
     */
    private fun recoverPendingCleanup(accountGuid: String) {
        val pendingAccount = sharedPreferences.getString(
            Constants.PREF_PENDING_CLEANUP_ACCOUNT, null
        ) ?: return
        if (pendingAccount != accountGuid) return
        val pendingTimestamp = sharedPreferences.getLong(
            Constants.PREF_PENDING_CLEANUP_TIMESTAMP, 0L
        )
        if (pendingTimestamp <= 0L) return

        scope.launch {
            catalogProcessingMutex.withLock {
                logger.d(TAG, "Replaying pending cleanup: account=$accountGuid ts=$pendingTimestamp")
                runBatchCleanup(accountGuid, pendingTimestamp)
            }
        }
    }

    /**
     * Processes options items from unified payload.
     */
    private suspend fun processOptionsItems(
        optionsItems: List<com.google.gson.JsonObject>,
        messageId: String
    ) {
        try {
            // Use first options item (should only be one)
            val optionsObject = optionsItems.first().deepCopy()

            // Extract license before removing it from options
            val license = optionsObject.get("license")?.asString ?: ""

            optionsObject.remove("value_id")

            val gson = Gson()
            val optionsJson = gson.toJson(optionsObject)

            try {
                // Atomic read-modify-write: prevents a concurrent token refresh
                // from clobbering the new options/license (or vice versa).
                val saved = userAccountRepository.updateCurrent { current ->
                    current.copy(
                        options = optionsJson,
                        license = license.ifEmpty { current.license }
                    )
                }
                if (saved == null) {
                    logger.e(TAG, "No current account for options update")
                    sendError(messageId, "options", "No current account")
                    return
                }
                logger.d(TAG, "Updated options for account ${saved.guid} (${optionsJson.length} chars)")
                currentAccount = saved
                sendAck(messageId, "options", 1)
            } catch (e: Exception) {
                logger.e(TAG, "Failed to save options: ${e.message}")
                sendError(messageId, "options", "Failed to save: ${e.message}")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error processing options: ${e.message}")
            sendError(messageId, "options", e.message ?: "Failed to process")
        }
    }

    /**
     * Handles pong message from server.
     * Extracts license number from payload if present and saves to account.
     *
     * Expected format:
     * {
     *   "type": "pong",
     *   "message_id": "some-id",
     *   "timestamp": "2025-12-27T12:00:00Z",
     *   "status": "approved",
     *   "payload": {
     *     "license_number": "ABCD-EF1234"
     *   }
     * }
     */
    private fun handlePongMessage(message: WebSocketMessage) {
        scope.launch {
            try {
                val payload = message.payload?.asJsonObject ?: return@launch
                val licenseNumber = payload.get("license_number")?.asString

                if (licenseNumber.isNullOrEmpty()) {
                    return@launch
                }

                // Atomic read-modify-write: prevents a concurrent token refresh
                // or options save from clobbering the license (or vice versa).
                // The transform also early-outs if the license is unchanged so
                // we don't issue a no-op DB write. Log only when the license
                // actually changes — pong arrives on every server interaction.
                val previousLicense = currentAccount?.license
                val saved = userAccountRepository.updateCurrent { current ->
                    if (current.license == licenseNumber) current
                    else current.copy(license = licenseNumber)
                }
                if (saved != null) {
                    currentAccount = saved
                    if (previousLicense != licenseNumber) {
                        logger.d(TAG, "License received: ${licenseNumber.take(6)}...")
                    }
                }

            } catch (e: Exception) {
                logger.e(TAG, "Error handling pong message: ${e.message}")
            }
        }
    }

    /**
     * Sends a generic acknowledgment message
     */
    private fun sendAck(messageId: String?, dataType: String, itemCount: Int) {
        if (messageId.isNullOrEmpty()) return

        try {
            val ackMessage = WebSocketMessageFactory.createAckMessage(messageId)
            webSocket?.send(ackMessage)
        } catch (e: Exception) {
            logger.e(TAG, "Error sending ACK: ${e.message}")
        }
    }

    /**
     * Sends a generic error message
     */
    private fun sendError(messageId: String?, dataType: String, error: String) {
        if (messageId.isNullOrEmpty()) return

        try {
            val gson = Gson()
            val errorPayload = mapOf(
                "data_type" to dataType,
                "error" to error
            )
            val errorMessage = WebSocketMessageFactory.createMessage(
                Constants.WEBSOCKET_MESSAGE_TYPE_ERROR,
                gson.toJson(errorPayload),
                messageId
            )
            webSocket?.send(errorMessage)
            logger.e(TAG, "Sent error for $dataType: $error")
        } catch (e: Exception) {
            logger.e(TAG, "Error sending error message: ${e.message}")
        }
    }

    private fun updateLicenseCheckTime() {
        val now = System.currentTimeMillis()
        _lastLicenseCheckTime.value = now
        sharedPreferences.edit().putLong(Constants.PREF_LAST_LICENSE_CHECK, now).apply()
    }

    // OkHttp WebSocket listener
    private inner class WebSocketListener : okhttp3.WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempt = 0
            val deviceUuid = currentAccount?.guid ?: "unknown"
            _connectionState.value = WebSocketState.Connected(deviceUuid)
            updateLicenseCheckTime()
            startPingScheduler()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== this@WebSocketRepositoryImpl.webSocket) {
                logger.d(TAG, "Ignoring message from stale connection (${text.length} bytes)")
                return
            }
            logger.d(TAG, "Frame received: ${text.length} bytes")
            handleIncomingMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            logger.d(TAG, "Closing: $code - $reason")
            if (webSocket !== this@WebSocketRepositoryImpl.webSocket) {
                logger.d(TAG, "Ignoring onClosing from stale connection")
                return
            }
            cancelPing()
            if (isPendingDevice || isLicenseError) {
                _connectionState.value = WebSocketState.Disconnected
                return
            }
            if (code != 1000) {
                _connectionState.value = WebSocketState.Error("Connection closing: $reason", canRetry = true)
                scheduleReconnection()
            } else {
                _connectionState.value = WebSocketState.Disconnected
                scheduleReconnection()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger.d(TAG, "Closed: $code - $reason")
            cancelPing()

            // Ignore events from stale (replaced) connections
            if (webSocket !== this@WebSocketRepositoryImpl.webSocket) {
                logger.d(TAG, "Ignoring onClosed from stale connection")
                return
            }

            // Don't reconnect if device is pending approval
            if (isPendingDevice) {
                logger.d(TAG, "Connection closed for pending device - no reconnection")
                return
            }

            // Don't reconnect if license error
            if (isLicenseError) {
                logger.d(TAG, "Connection closed due to license error - no reconnection")
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

            // Ignore events from stale (replaced) connections
            if (webSocket !== this@WebSocketRepositoryImpl.webSocket) {
                logger.d(TAG, "Ignoring onFailure from stale connection")
                return
            }

            // Don't reconnect if device is pending approval
            if (isPendingDevice) {
                logger.d(TAG, "Connection failed for pending device - no reconnection")
                return
            }

            // Don't reconnect if license error
            if (isLicenseError) {
                logger.d(TAG, "Connection failed due to license error - no reconnection")
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
