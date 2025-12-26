package ua.com.programmer.agentventa.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    private val userAccountRepository: ua.com.programmer.agentventa.domain.repository.UserAccountRepository
) : WebSocketRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "WebSocket"

    // Connection state management
    private val _connectionState = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected)
    override val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    // Incoming message stream
    private val _incomingMessages = MutableSharedFlow<IncomingDataMessage>(replay = 0, extraBufferCapacity = 100)
    override val incomingMessages: Flow<IncomingDataMessage> = _incomingMessages.asSharedFlow()

    // Document acknowledgment stream for notifying about successfully uploaded documents
    private val _documentAcks = MutableSharedFlow<DocumentAck>(replay = 0, extraBufferCapacity = 100)
    override val documentAcks: SharedFlow<DocumentAck> = _documentAcks.asSharedFlow()

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
        isLicenseError = false   // Reset license error flag on new connection attempt
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
                resultFlow.emit(SendResult.Failed(messageId, "Failed to send", canRetry = true))
            }
        } catch (e: Exception) {
            pendingMessages.remove(messageId)
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
            }
        } catch (e: Exception) {
            resultFlow.emit(SendResult.Failed(messageId, e.message ?: "Unknown error", canRetry = true))
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
            logger.d(TAG, "API Key present: ${apiKey.isNotEmpty()}, length: ${apiKey.length}")
            logger.d(TAG, "Auth token format: Bearer <${apiKey.length} chars>:<${deviceUuid.length} chars>")

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

        // Don't schedule reconnection if license error
        if (isLicenseError) {
            logger.d(TAG, "Skipping reconnection - license error")
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
                logger.e(TAG, "Failed to parse WebSocket message")
                return
            }

            when (message.type) {
                Constants.WEBSOCKET_MESSAGE_TYPE_DATA -> {
                    val dataMessage = WebSocketMessageFactory.parseDataMessage(message)
                    if (dataMessage != null) {
                        logger.d(TAG, "Data received: ${dataMessage.dataType} (status: ${dataMessage.status})")

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
                    logger.d(TAG, "Pong received")
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

                Constants.WEBSOCKET_MESSAGE_TYPE_DOWNLOAD_CATALOGS -> {
                    handleCatalogUpdate(message)
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Message handling error: ${e.message}")
        }
    }

    /**
     * Handles incoming catalog update messages from the server.
     * Parses the catalog data and saves it to the local database.
     */
    private fun handleCatalogUpdate(message: WebSocketMessage) {
        scope.launch {
            try {
                val gson = Gson()
                val payload = message.payload

                // Parse the payload as a JSON object
                val payloadObj = gson.fromJson(payload, Map::class.java) ?: run {
                    logger.e(TAG, "Invalid catalog update payload")
                    return@launch
                }

                val catalogType = payloadObj["catalog_type"] as? String
                val data = payloadObj["data"] as? List<*>
                val messageId = message.messageId

                if (catalogType == null || data == null) {
                    logger.e(TAG, "Missing catalog_type or data in payload")
                    return@launch
                }

                logger.d(TAG, "Received catalog update: $catalogType (${data.size} items)")

                // Get current account guid for database operations
                val accountGuid = currentAccount?.guid ?: run {
                    logger.e(TAG, "No current account for catalog update")
                    return@launch
                }

                // Get current timestamp for catalog update
                val timestamp = System.currentTimeMillis()

                // Convert data to XMap format (same as HTTP sync uses)
                val xMapList = data.mapNotNull { item ->
                    try {
                        val itemMap = item as? Map<*, *> ?: return@mapNotNull null
                        val xMap = XMap(itemMap)
                        xMap.setDatabaseId(accountGuid)
                        xMap.setTimestamp(timestamp)
                        xMap
                    } catch (e: Exception) {
                        logger.e(TAG, "Error converting catalog item: ${e.message}")
                        null
                    }
                }

                if (xMapList.isEmpty()) {
                    logger.w(TAG, "No valid catalog items to save")
                    // Still send ACK even if no items
                    sendCatalogAck(messageId, catalogType, 0)
                    return@launch
                }

                // Save catalog data using existing DataExchangeRepository
                try {
                    dataExchangeRepository.saveData(xMapList)
                    logger.d(TAG, "Saved ${xMapList.size} items for catalog: $catalogType")

                    // Send acknowledgment to server
                    sendCatalogAck(messageId, catalogType, xMapList.size)
                } catch (e: Exception) {
                    logger.e(TAG, "Error saving catalog data: ${e.message}")
                    sendCatalogError(messageId, catalogType, e.message ?: "Save failed")
                }

            } catch (e: Exception) {
                logger.e(TAG, "Error handling catalog update: ${e.message}")
            }
        }
    }

    /**
     * Sends an acknowledgment message after successfully processing catalog data
     */
    private fun sendCatalogAck(messageId: String?, catalogType: String, itemCount: Int) {
        if (messageId.isNullOrEmpty()) return

        try {
            val ackMessage = WebSocketMessageFactory.createAckMessage(messageId)
            webSocket?.send(ackMessage)
            logger.d(TAG, "Sent ACK for catalog $catalogType ($itemCount items)")
        } catch (e: Exception) {
            logger.e(TAG, "Error sending catalog ACK: ${e.message}")
        }
    }

    /**
     * Sends an error message if catalog processing failed
     */
    private fun sendCatalogError(messageId: String?, catalogType: String, error: String) {
        if (messageId.isNullOrEmpty()) return

        try {
            val gson = Gson()
            val errorPayload = mapOf(
                "catalog_type" to catalogType,
                "error" to error
            )
            val errorMessage = WebSocketMessageFactory.createMessage(
                Constants.WEBSOCKET_MESSAGE_TYPE_ERROR,
                gson.toJson(errorPayload),
                messageId
            )
            webSocket?.send(errorMessage)
            logger.e(TAG, "Sent error for catalog $catalogType: $error")
        } catch (e: Exception) {
            logger.e(TAG, "Error sending catalog error: ${e.message}")
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
            try {
                // Extract data array from the message
                val dataArray = dataMessage.data.getAsJsonArray("data")
                if (dataArray == null || dataArray.size() == 0) {
                    logger.w(TAG, "Empty data array in unified payload")
                    sendAck(dataMessage.messageId, "unified", 0)
                    return@launch
                }

                // Get current account guid for database operations
                val accountGuid = currentAccount?.guid ?: run {
                    logger.e(TAG, "No current account for unified payload")
                    return@launch
                }

                // Separate items by value_id
                val optionsItems = mutableListOf<com.google.gson.JsonObject>()
                val catalogItems = mutableListOf<XMap>()
                val counters = mutableMapOf<String, Int>()
                val timestamp = System.currentTimeMillis()

                for (i in 0 until dataArray.size()) {
                    try {
                        val item = dataArray.get(i).asJsonObject
                        val valueId = item.get("value_id")?.asString

                        if (valueId.isNullOrEmpty()) {
                            logger.w(TAG, "Item $i missing value_id, skipping")
                            continue
                        }

                        when (valueId) {
                            Constants.VALUE_ID_OPTIONS -> {
                                // Collect options items for processing
                                optionsItems.add(item)
                            }
                            else -> {
                                // Catalog items (clients, goods, debts, etc.)
                                val gson = Gson()
                                val itemMap = gson.fromJson(item, Map::class.java) as Map<*, *>
                                val xMap = XMap(itemMap)
                                xMap.setDatabaseId(accountGuid)
                                xMap.setTimestamp(timestamp)
                                catalogItems.add(xMap)
                            }
                        }

                        counters[valueId] = (counters[valueId] ?: 0) + 1
                    } catch (e: Exception) {
                        logger.e(TAG, "Error processing item $i: ${e.message}")
                    }
                }

                // Process options items
                if (optionsItems.isNotEmpty()) {
                    processOptionsItems(optionsItems, dataMessage.messageId)
                }

                // Process catalog items
                if (catalogItems.isNotEmpty()) {
                    processCatalogItems(catalogItems, counters, dataMessage.messageId)
                }

                // If nothing to process
                if (optionsItems.isEmpty() && catalogItems.isEmpty()) {
                    logger.w(TAG, "No valid items in unified payload")
                    sendAck(dataMessage.messageId, "unified", 0)
                }

            } catch (e: Exception) {
                logger.e(TAG, "Error handling unified payload: ${e.message}")
                sendError(dataMessage.messageId, "unified", e.message ?: "Processing failed")
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
            val account = currentAccount ?: run {
                logger.e(TAG, "No current account for options update")
                sendError(messageId, "options", "No current account")
                return
            }

            // Use first options item (should only be one)
            val optionsObject = optionsItems.first().deepCopy()
            optionsObject.remove("value_id")

            val gson = Gson()
            val optionsJson = gson.toJson(optionsObject)

            logger.d(TAG, "Updating options for account ${account.guid} (${optionsJson.length} chars)")

            val updatedAccount = account.copy(options = optionsJson)

            try {
                userAccountRepository.saveAccount(updatedAccount)
                logger.d(TAG, "Options saved successfully")
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
     * Processes catalog items from unified payload.
     */
    private suspend fun processCatalogItems(
        catalogItems: List<XMap>,
        counters: Map<String, Int>,
        messageId: String
    ) {
        try {
            dataExchangeRepository.saveData(catalogItems)
            logger.d(TAG, "Saved ${catalogItems.size} catalog items: $counters")
            sendAck(messageId, "catalog", catalogItems.size)
        } catch (e: Exception) {
            logger.e(TAG, "Error saving catalog data: ${e.message}")
            sendError(messageId, "catalog", e.message ?: "Save failed")
        }
    }

    /**
     * Legacy handler for catalog update (kept for backwards compatibility).
     * @deprecated Use handleUnifiedPayload instead
     */
    @Deprecated("Use handleUnifiedPayload instead")
    private fun handleUnifiedCatalogUpdate(dataMessage: IncomingDataMessage) {
        scope.launch {
            try {
                // Extract data array from the message
                val dataArray = dataMessage.data.getAsJsonArray("data")
                if (dataArray == null || dataArray.size() == 0) {
                    logger.w(TAG, "Empty data array in unified catalog update")
                    sendAck(dataMessage.messageId, "catalog", 0)
                    return@launch
                }

                // Get current account guid for database operations
                val accountGuid = currentAccount?.guid ?: run {
                    logger.e(TAG, "No current account for unified catalog update")
                    return@launch
                }

                // Get current timestamp for catalog update
                val timestamp = System.currentTimeMillis()

                // Convert data array to XMap format
                val xMapList = mutableListOf<XMap>()
                val counters = mutableMapOf<String, Int>()

                for (i in 0 until dataArray.size()) {
                    try {
                        val item = dataArray.get(i).asJsonObject
                        val gson = Gson()
                        val itemMap = gson.fromJson(item, Map::class.java) as Map<*, *>

                        val xMap = XMap(itemMap)
                        xMap.setDatabaseId(accountGuid)
                        xMap.setTimestamp(timestamp)
                        xMapList.add(xMap)

                        // Count by value_id
                        val valueId = xMap.getValueId()
                        counters[valueId] = (counters[valueId] ?: 0) + 1
                    } catch (e: Exception) {
                        logger.e(TAG, "Error converting catalog item: ${e.message}")
                    }
                }

                if (xMapList.isEmpty()) {
                    logger.w(TAG, "No valid items in unified catalog update")
                    sendAck(dataMessage.messageId, "catalog", 0)
                    return@launch
                }

                // Save catalog data using existing DataExchangeRepository
                try {
                    dataExchangeRepository.saveData(xMapList)
                    logger.d(TAG, "Saved ${xMapList.size} unified catalog items: $counters")

                    // Send acknowledgment to server
                    sendAck(dataMessage.messageId, "catalog", xMapList.size)
                } catch (e: Exception) {
                    logger.e(TAG, "Error saving unified catalog data: ${e.message}")
                    sendError(dataMessage.messageId, "catalog", e.message ?: "Save failed")
                }

            } catch (e: Exception) {
                logger.e(TAG, "Error handling unified catalog update: ${e.message}")
            }
        }
    }

    /**
     * Handles options update from the server.
     * Updates the current UserAccount's options field.
     *
     * Expected format:
     * {
     *   "data_type": "options",
     *   "data": [
     *     {
     *       "value_id": "options",
     *       "allowPriceTypeChoose": false,
     *       "write": true,
     *       ...
     *     }
     *   ]
     * }
     */
    private fun handleOptionsUpdate(dataMessage: IncomingDataMessage) {
        scope.launch {
            try {
                // Extract data array from the message
                val dataArray = dataMessage.data.getAsJsonArray("data")
                if (dataArray == null || dataArray.size() == 0) {
                    logger.w(TAG, "Empty data array in options update")
                    sendAck(dataMessage.messageId, "options", 0)
                    return@launch
                }

                // Get current account
                val account = currentAccount ?: run {
                    logger.e(TAG, "No current account for options update")
                    sendError(dataMessage.messageId, "options", "No current account")
                    return@launch
                }

                // Find the options object (first item with value_id = "options")
                var optionsObject: com.google.gson.JsonObject? = null
                for (i in 0 until dataArray.size()) {
                    val item = dataArray.get(i).asJsonObject
                    val valueId = item.get("value_id")?.asString
                    if (valueId == Constants.VALUE_ID_OPTIONS) {
                        optionsObject = item
                        break
                    }
                }

                if (optionsObject == null) {
                    logger.w(TAG, "No options object found in data array")
                    sendAck(dataMessage.messageId, "options", 0)
                    return@launch
                }

                // Remove value_id field from options before saving
                optionsObject.remove("value_id")

                // Convert to JSON string
                val gson = Gson()
                val optionsJson = gson.toJson(optionsObject)

                logger.d(TAG, "Updating options for account ${account.guid}: ${optionsJson.take(200)}")

                // Update the current account's options
                val updatedAccount = account.copy(options = optionsJson)

                // Save to database using UserAccountRepository
                try {
                    userAccountRepository.saveAccount(updatedAccount)
                    logger.d(TAG, "Options saved to database successfully (${optionsJson.length} chars)")

                    // Send acknowledgment
                    sendAck(dataMessage.messageId, "options", 1)
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to save options to database: ${e.message}")
                    sendError(dataMessage.messageId, "options", "Failed to save: ${e.message}")
                }

            } catch (e: Exception) {
                logger.e(TAG, "Error handling options update: ${e.message}")
                sendError(dataMessage.messageId, "options", e.message ?: "Failed to update options")
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
            logger.d(TAG, "Sent ACK for $dataType ($itemCount items)")
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
