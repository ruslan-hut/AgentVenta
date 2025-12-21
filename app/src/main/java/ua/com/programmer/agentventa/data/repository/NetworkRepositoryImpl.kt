package ua.com.programmer.agentventa.data.repository

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import retrofit2.HttpException
import retrofit2.Retrofit
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.connectionSettingsChanged
import ua.com.programmer.agentventa.data.local.entity.getBaseUrl
import ua.com.programmer.agentventa.data.local.entity.getGuid
import ua.com.programmer.agentventa.data.local.entity.isValidForHttpConnection
import ua.com.programmer.agentventa.data.local.entity.shouldUseWebSocket
import ua.com.programmer.agentventa.data.local.entity.toMap
import ua.com.programmer.agentventa.data.remote.api.HttpClientApi
import ua.com.programmer.agentventa.data.remote.interceptor.HttpAuthInterceptor
import ua.com.programmer.agentventa.data.remote.interceptor.TokenRefresh
import ua.com.programmer.agentventa.data.remote.Result
import ua.com.programmer.agentventa.data.remote.SendResult
import ua.com.programmer.agentventa.data.remote.TokenManager
import ua.com.programmer.agentventa.data.websocket.SendResult as WebSocketSendResult
import kotlinx.coroutines.flow.first
import ua.com.programmer.agentventa.data.remote.TokenManagerImpl
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.domain.repository.DataExchangeRepository
import ua.com.programmer.agentventa.domain.repository.NetworkRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.presentation.features.settings.UserOptionsBuilder
import ua.com.programmer.agentventa.utility.Constants
import ua.com.programmer.agentventa.utility.XMap
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class NetworkRepositoryImpl @Inject constructor(
    private val userAccountRepository: UserAccountRepository,
    private val dataRepository: DataExchangeRepository,
    private val retrofit: Retrofit.Builder,
    private val httpAuthInterceptor: HttpAuthInterceptor,
    private val logger: Logger,
    private val tokenManager: TokenManager,
    tokenRefresh: TokenRefresh,
    private val webSocketRepository: ua.com.programmer.agentventa.domain.repository.WebSocketRepository
): NetworkRepository {

    private var _timestamp = 0L
    private var _options = UserOptionsBuilder.build(null)

    private var currentSystemAccount: UserAccount? = null
    private var account: UserAccount? = null
    private var apiService: HttpClientApi? = null
    private var token = ""
    private val counters = mutableMapOf<String, Int>()

    private val logTag = "NetworkRepo"

    private val gson = Gson()

    init {

        tokenRefresh.setRefreshToken { tokenManager.refreshTokenSync() }

        userAccountRepository.currentAccount.onEach { userAccount ->

            if (userAccount == null) return@onEach
            currentSystemAccount = userAccount
            if (isNotValidAccount(currentSystemAccount)) {
                logger.w(logTag, "Account ${currentSystemAccount?.getGuid()} is not valid for connection")
                return@onEach
            }

            account?.let {
                if (it.connectionSettingsChanged(userAccount)) {
                    apiService = null
                } else {
                    token = userAccount.token
                    account = it.copy(
                        token = userAccount.token,
                        options = userAccount.options,
                        license = userAccount.license
                    )
                    return@onEach
                }
            }

            userAccount.let {

                account = it
                //accountGuid = it.guid
                token = it.token

                httpAuthInterceptor.setCredentials(it.dbUser, it.dbPassword)

                val retrofit = retrofit.baseUrl(it.getBaseUrl()).build()
                apiService = retrofit.create(HttpClientApi::class.java)

                // Configure token manager with new account and API service
                tokenManager.configure(it)
                if (tokenManager is TokenManagerImpl) {
                    tokenManager.setApiService(apiService!!)
                }

                //logger.d("NetworkRepositoryImpl", "set connection: ${it.dbServer}: ${it.dbUser}")
            }

        }.launchIn(CoroutineScope(Dispatchers.IO))

        // Subscribe to WebSocket document acknowledgments to mark documents as sent
        webSocketRepository.documentAcks.onEach { ack ->
            val accountGuid = currentSystemAccount?.guid ?: return@onEach
            try {
                when (ack.documentType) {
                    "order" -> {
                        val updated = dataRepository.markOrderSentViaWebSocket(accountGuid, ack.documentGuid)
                        logger.d(logTag, "Order marked as sent via WebSocket: ${ack.documentGuid} (updated=$updated)")
                    }
                    "cash" -> {
                        val updated = dataRepository.markCashSentViaWebSocket(accountGuid, ack.documentGuid)
                        logger.d(logTag, "Cash marked as sent via WebSocket: ${ack.documentGuid} (updated=$updated)")
                    }
                    "image" -> {
                        val updated = dataRepository.markImageSentViaWebSocket(accountGuid, ack.documentGuid)
                        logger.d(logTag, "Image marked as sent via WebSocket: ${ack.documentGuid} (updated=$updated)")
                    }
                    "location" -> {
                        // For locations, the documentGuid is client_guid
                        val updated = dataRepository.markLocationSentViaWebSocket(accountGuid, ack.documentGuid)
                        logger.d(logTag, "Location marked as sent via WebSocket: ${ack.documentGuid} (updated=$updated)")
                    }
                    else -> {
                        logger.w(logTag, "Unknown document type in ACK: ${ack.documentType}")
                    }
                }
            } catch (e: Exception) {
                logger.e(logTag, "Error marking document as sent: ${e.message}")
            }
        }.launchIn(CoroutineScope(Dispatchers.IO))
    }

    private suspend fun onConnectionError() {
        token = ""
        tokenManager.clearToken()
    }

    private val prepare: Flow<Result> = flow {
        if (isNotValidAccount(currentSystemAccount)) {
            emit(Result.Error("Wrong connection settings"))
            return@flow
        }

        _timestamp = System.currentTimeMillis()
        val accountGuid = account?.guid ?: ""
        counters.clear()
        tokenManager.resetCounter()

        if (accountGuid.isBlank()) {
            emit(Result.Error("No settings for connection"))
            return@flow
        }

        _options = UserOptionsBuilder.build(account)
        token = account?.token ?: ""

        if (token.isBlank() || _options.isEmpty) {
            when (val result = tokenManager.refreshToken("prepare")) {
                is TokenManager.TokenResult.Success -> {
                    token = result.token
                    if (!result.canRead) {
                        logger.w(logTag, "User has no read access")
                        emit(Result.Error("User has no read access"))
                        return@flow
                    }
                }
                is TokenManager.TokenResult.Error -> {
                    logger.e(logTag, "Token refresh failed: ${result.message}")
                    emit(Result.Error(result.message))
                    return@flow
                }
            }
        }

        if (token.isBlank()) {
            emit(Result.Error("Forbidden"))
            return@flow
        }

        val hasOptions = !(account?.options?.isEmpty() ?: true)
        val server = account?.dbServer ?: ""
        logger.d(logTag, "api call for: ${account?.getGuid()} opt=$hasOptions svr=$server")
        emit(Result.Progress("start: ${account?.description}"))
    }

    override suspend fun updateAll(): Flow<Result> = flow {

        runCatching {
            prepare.collect {
                emit(it)
                if (it is Result.Error) throw Exception(it.message)
            }
        }.onFailure {
            logger.w(logTag, "prepare: $it")
            return@flow
        }

        val accountGuid = account?.guid ?: ""

        val queue = mutableListOf<String>()
        queue.add("clients")
        queue.add("debts")
        queue.add("goods")
        queue.add("payment_types")
        if (_options.useCompanies) queue.add("companies")
        if (_options.useStores) queue.add("stores")
        if (_options.useStores) queue.add("rests")
        if (_options.clientsLocations) queue.add("clients_locations")
        if (_options.clientsDirections) queue.add("clients_directions")
        if (_options.clientsProducts) queue.add("clients_goods")
        if (_options.loadImages) queue.add("images")

        for (item in queue) {
            try {
                makeDataRequest(accountGuid, _timestamp, item, "")
                for (c in counters) {
                    emit(Result.Progress("${c.key}: ${c.value}"))
                }
                counters.clear()
            } catch (e: HttpException) {
                logger.e(logTag, "Http error: $item: $e")
                onConnectionError()
                emit(Result.Error("${e.message()} (${e.code()})"))
                return@flow
            } catch (e: Exception) {
                logger.e(logTag, "Exception: $item: $e")
                emit(Result.Error(e.message ?: "unknown error"))
                return@flow
            }
        }

        dataRepository.cleanUp(accountGuid, _timestamp)
        // simulate update of account for observers to load changed data
        account?.let {
            userAccountRepository.saveAccount(it)
        }

        val timeSpent = showTime(_timestamp, System.currentTimeMillis())
        logger.d(logTag, "finish update: $timeSpent")
        emit(Result.Progress("finish: $timeSpent"))
        emit(Result.Success(""))
    }

    override suspend fun updateDifferential(): Flow<Result> = flow {

        runCatching {
            prepare.collect {
                emit(it)
                if (it is Result.Error) throw Exception(it.message)
            }
        }.onFailure {
            logger.w(logTag, "prepare: $it")
            return@flow
        }

        val accountGuid = account?.guid ?: ""

        if(_options.write) {
            try {
                sendDocuments(accountGuid).collect {
                    emit(it)
                }
            } catch (e: HttpException) {
                logger.e(logTag, "Http error: send: $e")
                onConnectionError()
                emit(Result.Error("${e.message()} (${e.code()})"))
                return@flow
            } catch (e: Exception) {
                logger.e(logTag, "Exception: send: $e")
                emit(Result.Error(e.message ?: "unknown error"))
                return@flow
            }
        } else {
            emit(Result.Error("No write access"))
        }

        val timeSpent = showTime(_timestamp, System.currentTimeMillis())
        logger.d(logTag, "finish: $timeSpent")
        emit(Result.Progress("finish: $timeSpent"))
        emit(Result.Success(""))
    }

    override suspend fun getDebtContent(type: String, guid: String): Flow<Result> = flow {
        try {
            val response = apiService?.getDocumentContent(type, guid, token)
            if (response != null) {
                val data = XMap(response)
                val content = data.getString("content")
                val error = data.getString("error")
                if (error.isNotEmpty()) {
                    emit(Result.Error(error))
                }
                if (content.isNotEmpty()) {
                    dataRepository.saveDebtContent(account?.guid ?: "", guid, content)
                    emit(Result.Success(""))
                }
            }else{
                emit(Result.Error("No response"))
            }
        } catch (e: Exception) {
            logger.e(logTag, "debt: $type: $e")
            emit(Result.Error(e.message ?: "unknown error"))
        }
    }

    override suspend fun getPrintData(guid: String, storage: File): Flow<Result> = flow {
        try {
            val response = apiService?.getPrintData(guid)
            if (response != null) {
                val data = XMap(response)
                val content = data.getString("data")
                val error = data.getString("error")
                if (error.isNotEmpty()) {
                    emit(Result.Error(error))
                }
                if (processPrintData(guid, content, storage)) {
                    emit(Result.Success(""))
                } else {
                    emit(Result.Error("decode data error"))
                }
            }else{
                emit(Result.Error("No response"))
            }
        } catch (e: Exception) {
            logger.e(logTag, "print: $e")
            emit(Result.Error(e.message ?: "unknown error"))
        }
    }

    private suspend fun makeDataRequest(account: String, time: Long, type: String, element: String) {

        if (token.isBlank()) throw Exception("get: token is empty")

        val more = if (element.isNotBlank()) {
            "-more$element"
        } else {
            ""
        }

        val response = apiService?.get(type, token, more)

        if (response != null) {
            if (response.containsKey("data")){
                val data = response["data"] as List<*>
                saveData(account, time, data)
            }
            if (response.containsKey("more")){
                val nextElement = getMoreNumber("${response["more"]}")
                makeDataRequest(account, time, type, nextElement)
            }
        }
    }

    private suspend fun makePostRequest(data: JsonObject, account: String, guid: String, type: String) {

        if (token.isBlank()) throw Exception("post: token is empty")

        val response = apiService?.post(token, data)

        response?.let {
            Log.d("NetworkRepositoryImpl", "response: $it")
            val result = XMap(it)
            val error = result.getString("error")
            val status = result.getString("status")
            when (val resultCode = result.getString("result")) {
                "ok" -> {
                    val sendResult = SendResult(
                        account = account,
                        guid = guid,
                        type = type,
                        status = status.ifEmpty { "?" },
                        error = error
                    )
                    dataRepository.saveSendResult(sendResult)
                    if (error.isNotEmpty() && error != "null") {
                        logger.w(logTag, "send: ok with warn: $error")
                    }
                }
                "error" -> {
                    logger.w(logTag, "send: $error")
                }
                else -> {
                    logger.w(logTag, "send: unexpected result: $resultCode")
                }
            }
        }

    }

    /**
     * Main document sync routing method
     * Routes to either HTTP or WebSocket sync based on UserAccount.shouldUseWebSocket()
     */
    private fun sendDocuments(account: String): Flow<Result> = flow {
        // Check if we should use WebSocket or HTTP sync
        val currentAccount = this@NetworkRepositoryImpl.account
        if (currentAccount != null && currentAccount.shouldUseWebSocket()) {
            // Use WebSocket sync
            logger.d(logTag, "Using WebSocket sync for account: ${currentAccount.description}")
            sendDocumentsViaWebSocket(account).collect { emit(it) }
        } else {
            // Use HTTP sync
            logger.d(logTag, "Using HTTP sync for account: ${currentAccount?.description ?: account}")
            sendDocumentsViaHttp(account).collect { emit(it) }
        }
    }

    /**
     * Send documents via HTTP (original implementation)
     */
    private fun sendDocumentsViaHttp(account: String): Flow<Result> = flow {

        val documents = dataRepository.getOrders(account)
        val type = Constants.DOCUMENT_ORDER

        if (documents.isNotEmpty()) {
            for (document in documents) {
                val content = dataRepository.getOrderContent(account, document.guid).map { it.toMap() }
                val documentData = document.toMap(account, content)
                val json = gson.toJsonTree(documentData).asJsonObject
                makePostRequest(json, account, document.guid, type)
            }
            emit(Result.Progress("$type: ${documents.size}"))
        }

        val cashList = dataRepository.getCash(account)
        val typeCash = Constants.DOCUMENT_CASH

        if (cashList.isNotEmpty()) {
            for (cash in cashList) {
                val cashContent = cash.toMap(account)
                val json = gson.toJsonTree(cashContent).asJsonObject
                makePostRequest(json, account, cash.guid, typeCash)
            }
            emit(Result.Progress("$typeCash: ${cashList.size}"))
        }

        val images = dataRepository.getClientImages(account)
        val typeImage = Constants.DATA_CLIENT_IMAGE

        if (images.isNotEmpty()) {
            for (image in images) {
                val imageContent = image.toMap()
                val json = gson.toJsonTree(imageContent).asJsonObject
                makePostRequest(json, account, image.guid, typeImage)
            }
            emit(Result.Progress("$typeImage: ${images.size}"))
        }

        val locations = dataRepository.getClientLocations(account)
        val typeLocation = Constants.DATA_CLIENT_LOCATION

        if (locations.isNotEmpty()) {
            for (location in locations) {
                val locationContent = location.toMap()
                val json = gson.toJsonTree(locationContent).asJsonObject
                makePostRequest(json, account, location.clientGuid, typeLocation)
            }
            emit(Result.Progress("$typeLocation: ${locations.size}"))
        }

    }

    /**
     * Send documents via WebSocket
     */
    private fun sendDocumentsViaWebSocket(account: String): Flow<Result> = flow {
        // Check WebSocket connection before starting sync
        if (!webSocketRepository.isConnected()) {
            emit(Result.Progress("Connecting to WebSocket..."))
            val currentAccount = this@NetworkRepositoryImpl.account
            if (currentAccount != null) {
                val connected = webSocketRepository.connect(currentAccount)
                if (!connected && !webSocketRepository.isConnected()) {
                    emit(Result.Error("Failed to connect to WebSocket relay server"))
                    return@flow
                }
                // Wait a moment for connection to stabilize
                kotlinx.coroutines.delay(500)
            } else {
                emit(Result.Error("No account configured for WebSocket"))
                return@flow
            }
        }

        var totalDocuments = 0

        // Upload orders
        val documents = dataRepository.getOrders(account)
        if (documents.isNotEmpty()) {
            emit(Result.Progress("Uploading ${documents.size} orders via WebSocket..."))
            for (document in documents) {
                val content = dataRepository.getOrderContent(account, document.guid)
                // Use LOrderContent directly from DAO
                uploadOrderViaWebSocket(document, content).collect { result ->
                    when (result) {
                        is Result.Success -> totalDocuments++
                        is Result.Error -> logger.e(logTag, "Order upload failed: ${result.message}")
                        else -> {}
                    }
                }
            }
            emit(Result.Progress("${Constants.DOCUMENT_ORDER}: $totalDocuments"))
        }

        // Upload cash receipts
        val cashList = dataRepository.getCash(account)
        if (cashList.isNotEmpty()) {
            emit(Result.Progress("Uploading ${cashList.size} cash receipts via WebSocket..."))
            var cashCount = 0
            for (cash in cashList) {
                uploadCashViaWebSocket(cash).collect { result ->
                    when (result) {
                        is Result.Success -> cashCount++
                        is Result.Error -> logger.e(logTag, "Cash upload failed: ${result.message}")
                        else -> {}
                    }
                }
            }
            emit(Result.Progress("${Constants.DOCUMENT_CASH}: $cashCount"))
        }

        // Upload client images
        val images = dataRepository.getClientImages(account)
        if (images.isNotEmpty()) {
            emit(Result.Progress("Uploading ${images.size} images via WebSocket..."))
            var imageCount = 0
            for (image in images) {
                val payloadMap = mapOf(
                    "image" to image.toMap(),
                    "document_guid" to image.guid
                )
                val payloadJson = gson.toJson(payloadMap)

                val sendResult = webSocketRepository.sendMessage(
                    type = Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_IMAGE,
                    payload = payloadJson
                ).first()

                when (sendResult) {
                    is WebSocketSendResult.Sent, is WebSocketSendResult.Acknowledged -> {
                        imageCount++
                        logger.d(logTag, "Image ${image.guid} uploaded successfully")
                    }
                    is WebSocketSendResult.Failed -> {
                        logger.e(logTag, "Image upload failed: ${sendResult.error}")
                    }
                    is WebSocketSendResult.Pending -> {
                        logger.d(logTag, "Image ${image.guid} queued for upload")
                    }
                }
            }
            emit(Result.Progress("${Constants.DATA_CLIENT_IMAGE}: $imageCount"))
        }

        // Upload client locations
        val locations = dataRepository.getClientLocations(account)
        if (locations.isNotEmpty()) {
            emit(Result.Progress("Uploading ${locations.size} locations via WebSocket..."))
            val locationMaps = locations.map { it.toMap() }
            val payloadMap = mapOf(
                "locations" to locationMaps,
                "count" to locations.size
            )
            val payloadJson = gson.toJson(payloadMap)

            val sendResult = webSocketRepository.sendMessage(
                type = Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_LOCATION,
                payload = payloadJson
            ).first()

            when (sendResult) {
                is WebSocketSendResult.Sent, is WebSocketSendResult.Acknowledged -> {
                    logger.d(logTag, "${locations.size} locations uploaded successfully")
                    emit(Result.Progress("${Constants.DATA_CLIENT_LOCATION}: ${locations.size}"))
                }
                is WebSocketSendResult.Failed -> {
                    logger.e(logTag, "Location upload failed: ${sendResult.error}")
                    emit(Result.Progress("${Constants.DATA_CLIENT_LOCATION}: 0 (failed)"))
                }
                is WebSocketSendResult.Pending -> {
                    logger.d(logTag, "${locations.size} locations queued for upload")
                    emit(Result.Progress("${Constants.DATA_CLIENT_LOCATION}: ${locations.size} (queued)"))
                }
            }
        }
    }

    private suspend fun saveData(account: String, time: Long, data: List<*>) {
        if (data.isEmpty()) return
        val dataset = ArrayList<XMap>()
        try {
            for (listItem in data) {

                val item = XMap(listItem as Map<*, *>)
                item.setDatabaseId(account)
                item.setTimestamp(time)
                dataset.add(item)

                val type = item.getValueId()
                counters[type] = counters[type]?.plus(1) ?: 1

            }
        } catch (e: Exception) {
            logger.e(logTag, "Read data error: $e")
        }
        dataRepository.saveData(dataset)
    }

    private fun showTime(begin: Long, end: Long): String {
        val ms = end - begin
        var seconds = ms / 1000
        if (seconds < 1) return "0.$ms s"
        val days = seconds / 86400
        val hours = (seconds - days * 86400) / 3600
        val minutes = (seconds - days * 86400 - hours * 3600) / 60
        seconds = seconds - days * 86400 - hours * 3600 - minutes * 60
        var result = ""
        if (days > 0) result = "$days d "
        if (hours > 0 || result.isNotEmpty()) result = "$result$hours h "
        if (minutes > 0 || result.isNotEmpty()) result = "$result$minutes m "
        result = "$result$seconds s "
        return result
    }

    private fun isNotValidAccount(it: UserAccount?): Boolean {
        return !(it?.isValidForHttpConnection() ?: false)
    }

    private fun getMoreNumber(more: String): String {
        return try {
            more.toDouble().toInt().toString()
        } catch (e: NumberFormatException) {
            logger.e(logTag, "more from $more: $e")
            ""
        }
    }

    private fun processPrintData(guid: String, data: String, storage: File): Boolean {
        if (data.isEmpty()) return false

        val fileName = "$guid.pdf"
        val fileOutputStream: FileOutputStream

        try {
            val listFiles = storage.listFiles()
            if (listFiles != null) {
                for (pdfFile in listFiles) {
                    val cacheFileName = pdfFile.name
                    if (pdfFile.isFile && cacheFileName.contains(".pdf")) {
                        pdfFile.delete()
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            logger.e(logTag,"cache file delete: $e")
            return false
        }

        try {
            val printFile = File(storage, fileName)
            fileOutputStream = FileOutputStream(printFile.absolutePath)
            fileOutputStream.write(Base64.decode(data, Base64.DEFAULT))
            fileOutputStream.close()
        } catch (e: java.lang.Exception) {
            logger.e(logTag,"print file save: $e")
            return false
        }
        return true
    }

    // ========================================================================
    // WebSocket Document Synchronization Methods
    // ========================================================================

    /**
     * Upload a single order with its content via WebSocket
     */
    override suspend fun uploadOrderViaWebSocket(
        order: ua.com.programmer.agentventa.data.local.entity.Order,
        orderContent: List<ua.com.programmer.agentventa.data.local.entity.LOrderContent>
    ): Flow<Result> = flow {
        try {
            val currentAccount = account ?: run {
                emit(Result.Error("No account configured"))
                return@flow
            }

            emit(Result.Progress("Uploading order ${order.number} via WebSocket..."))

            val gson = Gson()

            // Serialize order to map using LOrderContent.toMap()
            val orderMap = order.toMap(currentAccount.guid, orderContent.map { it.toMap() })
            val orderJson = gson.toJsonTree(orderMap).asJsonObject

            // Serialize order content
            val contentJsonArray = orderContent.map {
                gson.toJsonTree(it.toMap()).asJsonObject
            }

            // Create payload
            val payloadMap = mapOf(
                "order" to orderMap,
                "content" to orderContent.map { it.toMap() },
                "document_guid" to order.guid
            )
            val payloadJson = gson.toJson(payloadMap)

            // Send via WebSocket and collect the result
            val sendResult = webSocketRepository.sendMessage(
                type = Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_ORDER,
                payload = payloadJson
            ).first()

            when (sendResult) {
                is WebSocketSendResult.Sent, is WebSocketSendResult.Acknowledged -> {
                    logger.d(logTag, "Order ${order.number} uploaded successfully via WebSocket")
                    emit(Result.Success("Order ${order.number} uploaded"))
                }
                is WebSocketSendResult.Failed -> {
                    logger.e(logTag, "Failed to upload order via WebSocket: ${sendResult.error}")
                    emit(Result.Error(sendResult.error))
                }
                is WebSocketSendResult.Pending -> {
                    logger.d(logTag, "Order ${order.number} queued for upload")
                    emit(Result.Progress("Order ${order.number} queued"))
                }
            }
        } catch (e: Exception) {
            logger.e(logTag, "Error uploading order via WebSocket: $e")
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Upload a single cash receipt via WebSocket
     */
    override suspend fun uploadCashViaWebSocket(
        cash: ua.com.programmer.agentventa.data.local.entity.Cash
    ): Flow<Result> = flow {
        try {
            val currentAccount = account ?: run {
                emit(Result.Error("No account configured"))
                return@flow
            }

            emit(Result.Progress("Uploading cash receipt ${cash.number} via WebSocket..."))

            val gson = Gson()

            // Serialize cash to map
            val cashMap = cash.toMap(currentAccount.guid)

            // Create payload
            val payloadMap = mapOf(
                "cash" to cashMap,
                "document_guid" to cash.guid
            )
            val payloadJson = gson.toJson(payloadMap)

            // Send via WebSocket and collect the result
            val sendResult = webSocketRepository.sendMessage(
                type = Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_CASH,
                payload = payloadJson
            ).first()

            when (sendResult) {
                is WebSocketSendResult.Sent, is WebSocketSendResult.Acknowledged -> {
                    logger.d(logTag, "Cash ${cash.number} uploaded successfully via WebSocket")
                    emit(Result.Success("Cash ${cash.number} uploaded"))
                }
                is WebSocketSendResult.Failed -> {
                    logger.e(logTag, "Failed to upload cash via WebSocket: ${sendResult.error}")
                    emit(Result.Error(sendResult.error))
                }
                is WebSocketSendResult.Pending -> {
                    logger.d(logTag, "Cash ${cash.number} queued for upload")
                    emit(Result.Progress("Cash ${cash.number} queued"))
                }
            }
        } catch (e: Exception) {
            logger.e(logTag, "Error uploading cash via WebSocket: $e")
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Upload product images via WebSocket
     */
    override suspend fun uploadImagesViaWebSocket(
        images: List<ua.com.programmer.agentventa.data.local.entity.ProductImage>
    ): Flow<Result> = flow {
        try {
            if (images.isEmpty()) {
                emit(Result.Success("No images to upload"))
                return@flow
            }

            emit(Result.Progress("Uploading ${images.size} images via WebSocket..."))

            val gson = Gson()
            var uploadedCount = 0

            for (image in images) {
                // Serialize image to map
                val imageMap = image.toMap()

                // Create payload
                val payloadMap = mapOf(
                    "image" to imageMap,
                    "document_guid" to image.guid
                )
                val payloadJson = gson.toJson(payloadMap)

                // Send via WebSocket and collect the result
                val sendResult = webSocketRepository.sendMessage(
                    type = Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_IMAGE,
                    payload = payloadJson
                ).first()

                when (sendResult) {
                    is WebSocketSendResult.Sent, is WebSocketSendResult.Acknowledged -> {
                        uploadedCount++
                        logger.d(logTag, "Image ${image.guid} uploaded successfully")
                        emit(Result.Progress("Uploaded $uploadedCount of ${images.size} images"))
                    }
                    is WebSocketSendResult.Failed -> {
                        logger.e(logTag, "Failed to upload image: ${sendResult.error}")
                        // Continue with other images
                    }
                    is WebSocketSendResult.Pending -> {
                        logger.d(logTag, "Image ${image.guid} queued for upload")
                    }
                }
            }

            emit(Result.Success("Uploaded $uploadedCount of ${images.size} images"))
        } catch (e: Exception) {
            logger.e(logTag, "Error uploading images via WebSocket: $e")
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Upload location history via WebSocket
     */
    override suspend fun uploadLocationsViaWebSocket(
        locations: List<ua.com.programmer.agentventa.data.local.entity.LocationHistory>
    ): Flow<Result> = flow {
        try {
            if (locations.isEmpty()) {
                emit(Result.Success("No locations to upload"))
                return@flow
            }

            emit(Result.Progress("Uploading ${locations.size} location records via WebSocket..."))

            val gson = Gson()

            // Serialize all locations to maps
            val locationMaps = locations.map { it.toMap() }

            // Create payload
            val payloadMap = mapOf(
                "locations" to locationMaps,
                "count" to locations.size
            )
            val payloadJson = gson.toJson(payloadMap)

            // Send via WebSocket (batch upload) and collect the result
            val sendResult = webSocketRepository.sendMessage(
                type = Constants.WEBSOCKET_MESSAGE_TYPE_UPLOAD_LOCATION,
                payload = payloadJson
            ).first()

            when (sendResult) {
                is WebSocketSendResult.Sent, is WebSocketSendResult.Acknowledged -> {
                    logger.d(logTag, "${locations.size} locations uploaded successfully via WebSocket")
                    emit(Result.Success("${locations.size} locations uploaded"))
                }
                is WebSocketSendResult.Failed -> {
                    logger.e(logTag, "Failed to upload locations via WebSocket: ${sendResult.error}")
                    emit(Result.Error(sendResult.error))
                }
                is WebSocketSendResult.Pending -> {
                    logger.d(logTag, "${locations.size} locations queued for upload")
                    emit(Result.Progress("${locations.size} locations queued"))
                }
            }
        } catch (e: Exception) {
            logger.e(logTag, "Error uploading locations via WebSocket: $e")
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Download all catalogs via WebSocket
     *
     * Note: This method sends a request to the server to push catalog updates.
     * The actual catalog data will be received via WebSocket messages and handled
     * by the WebSocketRepository's message handler.
     */
    override suspend fun downloadCatalogsViaWebSocket(fullSync: Boolean): Flow<Result> = flow {
        try {
            emit(Result.Progress("Requesting catalogs via WebSocket..."))

            // Get last sync timestamp
            val lastSyncTimestamp = if (fullSync) null else _timestamp

            // Create catalog request payload
            val catalogTypes = listOf(
                "clients",
                "products",
                "debts",
                "companies",
                "stores",
                "rests",
                "prices",
                "images"
            )

            val payloadMap = mapOf(
                "catalog_types" to catalogTypes,
                "full_sync" to fullSync,
                "last_sync_timestamp" to lastSyncTimestamp
            )
            val payloadJson = Gson().toJson(payloadMap)

            // Send catalog request via WebSocket and collect the result
            val sendResult = webSocketRepository.sendMessage(
                type = Constants.WEBSOCKET_MESSAGE_TYPE_DOWNLOAD_CATALOGS,
                payload = payloadJson
            ).first()

            when (sendResult) {
                is WebSocketSendResult.Sent, is WebSocketSendResult.Acknowledged -> {
                    logger.d(logTag, "Catalog request sent successfully via WebSocket")
                    emit(Result.Progress("Catalog request sent, waiting for server response..."))
                    // Note: Actual catalog data will be received via WebSocket messages
                    // and processed by WebSocketRepository message handlers
                    emit(Result.Success("Catalog request sent"))
                }
                is WebSocketSendResult.Failed -> {
                    logger.e(logTag, "Failed to request catalogs via WebSocket: ${sendResult.error}")
                    emit(Result.Error(sendResult.error))
                }
                is WebSocketSendResult.Pending -> {
                    logger.d(logTag, "Catalog request queued")
                    emit(Result.Progress("Catalog request queued"))
                }
            }
        } catch (e: Exception) {
            logger.e(logTag, "Error requesting catalogs via WebSocket: $e")
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Perform full document sync via WebSocket
     * Uploads all unsent documents and requests catalog updates
     *
     * Note: This method is simplified - marking documents as sent is handled by
     * the WebSocket message handlers when they receive confirmation from the server
     */
    override suspend fun syncViaWebSocket(): Flow<Result> = flow {
        try {
            emit(Result.Progress("Starting WebSocket sync..."))

            val currentAccount = account
            if (currentAccount == null) {
                emit(Result.Error("No current account"))
                return@flow
            }

            // Use the existing sendDocumentsViaWebSocket which handles all document uploads
            sendDocumentsViaWebSocket(currentAccount.guid).collect { result ->
                emit(result)
            }

            // Request catalog updates
            emit(Result.Progress("Requesting catalog updates..."))
            downloadCatalogsViaWebSocket(fullSync = false).collect { result ->
                when (result) {
                    is Result.Progress -> emit(result)
                    is Result.Success -> {
                        logger.d(logTag, "Catalog request sent")
                        emit(Result.Success("WebSocket sync complete"))
                    }
                    is Result.Error -> {
                        logger.e(logTag, "Failed to request catalogs: ${result.message}")
                        emit(result)
                    }
                }
            }

        } catch (e: Exception) {
            logger.e(logTag, "Error during WebSocket sync: $e")
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }
}