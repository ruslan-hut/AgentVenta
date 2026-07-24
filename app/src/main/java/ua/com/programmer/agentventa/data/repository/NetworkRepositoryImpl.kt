package ua.com.programmer.agentventa.data.repository

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import retrofit2.HttpException
import retrofit2.Retrofit
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.connectionSettingsChanged
import ua.com.programmer.agentventa.data.local.entity.getBaseUrl
import ua.com.programmer.agentventa.data.local.entity.getGuid
import ua.com.programmer.agentventa.data.local.entity.isDemo
import ua.com.programmer.agentventa.data.local.entity.isRelayRest
import ua.com.programmer.agentventa.data.local.entity.isValidForHttpConnection
import ua.com.programmer.agentventa.data.local.entity.toMap
import ua.com.programmer.agentventa.data.remote.api.HttpClientApi
import ua.com.programmer.agentventa.data.remote.interceptor.HttpAuthInterceptor
import ua.com.programmer.agentventa.data.remote.interceptor.TokenRefresh
import ua.com.programmer.agentventa.data.remote.Result
import ua.com.programmer.agentventa.data.remote.SendResult
import ua.com.programmer.agentventa.data.remote.SyncStats
import ua.com.programmer.agentventa.data.remote.TokenManager
import ua.com.programmer.agentventa.data.remote.TokenManagerImpl
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import ua.com.programmer.agentventa.infrastructure.notification.SyncNotifier
import ua.com.programmer.agentventa.domain.repository.DataExchangeRepository
import ua.com.programmer.agentventa.domain.repository.NetworkRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import ua.com.programmer.agentventa.extensions.trimForLog
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
    private val relaySyncClient: RelaySyncClient,
    private val syncNotifier: SyncNotifier
): NetworkRepository {

    private var _timestamp = 0L
    private var _options = UserOptionsBuilder.build(null)

    // The following four fields form the sync config and can be mutated from:
    //   - the currentAccount.onEach observer (account switches)
    //   - the token-refresh path during a sync
    // They are @Volatile so cross-thread writes are visible and each field
    // read is atomic. Consistency across fields is enforced by snapshotting
    // currentSystemAccount.guid at the start of each sync and guarding against
    // mid-sync account switches (see ensureSameAccount).
    @Volatile private var currentSystemAccount: UserAccount? = null
    @Volatile private var account: UserAccount? = null
    @Volatile private var apiService: HttpClientApi? = null
    @Volatile private var token = ""
    private val counters = mutableMapOf<String, Int>()

    private val logTag = "AV-NetworkRepo"

    private val gson = Gson()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {

        tokenRefresh.setRefreshToken { tokenManager.refreshTokenSync() }

        userAccountRepository.currentAccount.onEach { userAccount ->

            if (userAccount == null) return@onEach
            currentSystemAccount = userAccount

            // Only configure the direct-1C HTTP service for HTTP-capable accounts;
            // relay-REST accounts carry no dbServer and sync via RelaySyncClient.
            val isHttpAccount = !isNotValidAccount(currentSystemAccount)

            account?.let {
                if (it.connectionSettingsChanged(userAccount)) {
                    apiService = null
                } else {
                    token = userAccount.token
                    // Use userAccount (fresh from DB) as base to preserve all
                    // user-changed fields connectionSettingsChanged() doesn't track
                    account = userAccount
                    return@onEach
                }
            }

            userAccount.let {
                account = it
                token = it.token

                // Only configure HTTP service for HTTP-capable accounts
                if (isHttpAccount) {
                    httpAuthInterceptor.setCredentials(it.dbUser, it.dbPassword)

                    val retrofit = retrofit.baseUrl(it.getBaseUrl()).build()
                    apiService = retrofit.create(HttpClientApi::class.java)

                    // Configure token manager with new account and API service
                    tokenManager.configure(it)
                    if (tokenManager is TokenManagerImpl) {
                        tokenManager.setApiService(apiService!!)
                    }
                } else {
                    logger.d(logTag, "REST relay configured: ${it.guid.trimForLog()}")
                    apiService = null
                }
            }

        }.launchIn(scope)
    }

    private suspend fun onConnectionError() {
        token = ""
        tokenManager.clearToken()
    }

    /**
     * Aborts the caller if the active account has changed since [expectedGuid]
     * was captured. Prevents a mid-sync account switch from sending data from
     * account A to the connection of account B.
     */
    private fun ensureSameAccount(expectedGuid: String) {
        val current = currentSystemAccount?.guid
        if (current != expectedGuid) {
            throw IllegalStateException(
                "Active account changed during sync (expected=$expectedGuid, current=$current)"
            )
        }
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

        // Direct HTTP mode always calls GET /check to get fresh options from 1C.
        when (val result = tokenManager.refreshToken("prepare")) {
            is TokenManager.TokenResult.Success -> {
                token = result.token
                // Reload account from DB to get fresh options saved by TokenManager
                userAccountRepository.getCurrent()?.let { freshAccount ->
                    account = freshAccount
                }
                _options = UserOptionsBuilder.build(account)
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

        if (token.isBlank()) {
            emit(Result.Error("Forbidden"))
            return@flow
        }

        // Approval/license is gated by the relay /status endpoint for every
        // non-demo account, including direct-1C HTTP (data still goes direct to
        // the customer's 1C; only the license check uses the relay). Runs after
        // the token refresh so the app params sent to the relay carry the fresh
        // options just fetched from 1C. The demo account is fully self-contained
        // and needs neither approval nor license.
        val currentAccount = account
        if (currentAccount?.isDemo() == true) {
            logger.d(logTag, "Demo account - skipping device approval check")
        } else if (currentAccount != null) {
            val (isApproved, approvalError) = relaySyncClient.checkApproval(currentAccount)
            if (!isApproved) {
                logger.w(logTag, "Device not approved for HTTP operations: $approvalError")
                emit(Result.Error(approvalError))
                return@flow
            }
        }

        val hasOptions = !(account?.options?.isEmpty() ?: true)
        val server = account?.dbServer ?: ""
        logger.d(logTag, "api call for: ${account?.getGuid()} opt=$hasOptions svr=$server")
        emit(Result.Progress("start: ${account?.description}"))
    }

    /**
     * Reports the run's outcome to the notification shade once the flow ends.
     * Applied at the outermost level so every early `emit(Result.Error)` return
     * inside the sync body is still accounted for.
     */
    private fun Flow<Result>.withSyncNotification(stats: SyncStats): Flow<Result> {
        var error: String? = null
        return onEach { if (it is Result.Error) error = it.message }
            .onCompletion { cause ->
                if (cause == null) syncNotifier.notifyResult(account, stats, error)
            }
    }

    override suspend fun updateAll(): Flow<Result> {
        val stats = SyncStats()
        return updateAllFlow(stats).withSyncNotification(stats)
    }

    private fun updateAllFlow(stats: SyncStats): Flow<Result> = flow {
        val currentAccount = account

        // REST-relay sync path: pull catalog + upload documents over /api/v1/device.
        if (currentAccount != null && currentAccount.isRelayRest()) {
            logger.d(logTag, "Using REST relay sync (full)")
            relaySyncViaRest(currentAccount, stats).collect { emit(it) }
            return@flow
        }

        // HTTP full sync path (direct 1C / demo)

        runCatching {
            prepare.collect {
                emit(it)
                if (it is Result.Error) throw Exception(it.message)
            }
        }.onFailure {
            logger.w(logTag, "prepare: $it")
            emit(Result.Error(it.message ?: "Preparation failed"))
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
        if (_options.complexDiscounts) queue.add("discounts")


        for (item in queue) {
            try {
                ensureSameAccount(accountGuid)
                makeDataRequest(accountGuid, _timestamp, item, stats)
                for (c in counters) {
                    emit(Result.Progress("${c.key}: ${c.value}"))
                }
                counters.clear()
            } catch (e: HttpException) {
                logger.e(logTag, "Http error: $item: $e")
                onConnectionError()
                emit(Result.Error("${e.message()} (${e.code()})"))
                return@flow
            } catch (e: IllegalStateException) {
                logger.w(logTag, "aborting sync: ${e.message}")
                emit(Result.Error(e.message ?: "account changed"))
                return@flow
            } catch (e: Exception) {
                logger.e(logTag, "Exception: $item: $e")
                emit(Result.Error(e.message ?: "unknown error"))
                return@flow
            }
        }

        dataRepository.cleanUp(accountGuid, _timestamp)
        account?.let {
            userAccountRepository.saveAccount(it)
        }

        val timeSpent = showTime(_timestamp, System.currentTimeMillis())
        logger.d(logTag, "finish update: $timeSpent")
        emit(Result.Progress("finish: $timeSpent"))
        emit(Result.Success(""))
    }

    // Shared REST-relay sync: verify approval via /status, upload unsent
    // documents, then pull pending catalog. Used by both updateAll and
    // updateDifferential — for the relay the full/differential distinction is
    // moot since the catalog is delta-by-timestamp on the server.
    private fun relaySyncViaRest(account: UserAccount, stats: SyncStats): Flow<Result> = flow {
        _timestamp = System.currentTimeMillis()
        logger.d(logTag, "relaySyncViaRest begin: ${account.guid.trimForLog()}")
        val (approved, message) = relaySyncClient.checkApproval(account)
        logger.d(logTag, "relay approval: approved=$approved msg=$message")
        if (!approved) {
            emit(Result.Error(message))
            return@flow
        }
        emit(Result.Progress("start: ${account.description}"))
        try {
            relaySyncClient.uploadDocuments(account, stats).collect { emit(it) }
            relaySyncClient.pullCatalog(account, stats).collect { emit(it) }
        } catch (e: CancellationException) {
            logger.w(logTag, "REST relay sync CANCELLED: ${e.message}")
            throw e
        } catch (e: Exception) {
            logger.e(logTag, "REST relay sync error (${e.javaClass.simpleName}): $e")
            emit(Result.Error(e.message ?: "REST sync failed"))
            return@flow
        }
        val timeSpent = showTime(_timestamp, System.currentTimeMillis())
        logger.d(logTag, "REST relay sync finish: $timeSpent")
        emit(Result.Progress("finish: $timeSpent"))
        emit(Result.Success(""))
    }.onCompletion { cause ->
        if (cause != null) logger.w(logTag, "relaySyncViaRest flow ended: ${cause.javaClass.simpleName}: ${cause.message}")
    }

    override suspend fun updateDifferential(): Flow<Result> {
        val stats = SyncStats()
        return updateDifferentialFlow(stats).withSyncNotification(stats)
    }

    private fun updateDifferentialFlow(stats: SyncStats): Flow<Result> = flow {
        val currentAccount = account

        // REST-relay accounts: upload documents and pull any pending catalog.
        if (currentAccount != null && currentAccount.isRelayRest()) {
            logger.d(logTag, "Using REST relay sync (differential)")
            relaySyncViaRest(currentAccount, stats).collect { emit(it) }
            return@flow
        }

        // HTTP sync path (direct 1C / demo)
        runCatching {
            prepare.collect {
                emit(it)
                if (it is Result.Error) throw Exception(it.message)
            }
        }.onFailure {
            logger.w(logTag, "prepare: $it")
            emit(Result.Error(it.message ?: "Preparation failed"))
            return@flow
        }

        val accountGuid = currentAccount?.guid ?: ""

        if(_options.write) {
            try {
                sendDocuments(accountGuid, stats).collect {
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

    /**
     * Pulls a full catalog from the server, page by page, following the
     * opaque `more` cursor the server sets on each response. The server
     * decides when pagination ends by either omitting `more` or returning it
     * empty; we additionally stop if the cursor fails to advance (server bug
     * protection) or if we exceed [Constants.SYNC_MAX_PAGES] (runaway guard).
     */
    private suspend fun makeDataRequest(account: String, time: Long, type: String, stats: SyncStats) {
        if (token.isBlank()) throw Exception("get: token is empty")

        val api = apiService ?: return
        var cursor = ""
        var pages = 0

        while (pages < Constants.SYNC_MAX_PAGES) {
            ensureSameAccount(account)
            val suffix = if (cursor.isEmpty()) "" else "-more$cursor"
            val response = api.get(type, token, suffix)

            (response["data"] as? List<*>)?.let { saveData(account, time, it, stats) }

            val nextCursor = parseMoreCursor(response["more"]) ?: return
            if (nextCursor.isEmpty()) return
            if (nextCursor == cursor) {
                logger.w(logTag, "pagination ($type): cursor did not advance ('$cursor'), stopping")
                return
            }
            cursor = nextCursor
            pages++
        }
        logger.w(logTag, "pagination ($type): reached max page limit ${Constants.SYNC_MAX_PAGES}")
    }

    /**
     * Parses the server's pagination cursor. Gson deserialises JSON numbers as
     * [Number], so handle that directly rather than round-tripping through
     * `toString()`. Returns `""` to signal "stop paginating" and `null` on
     * unparseable input (also treated as stop, with a log line).
     */
    private fun parseMoreCursor(raw: Any?): String? {
        return when (raw) {
            null -> ""
            is Number -> raw.toLong().toString()
            is String -> {
                if (raw.isBlank()) return ""
                raw.toDoubleOrNull()?.toLong()?.toString()
                    ?: run {
                        logger.e(logTag, "pagination: cannot parse more='$raw'")
                        null
                    }
            }
            else -> {
                logger.e(logTag, "pagination: unexpected more type=${raw::class.java.simpleName}")
                null
            }
        }
    }

    /** Returns true when the server accepted the document. */
    private suspend fun makePostRequest(data: JsonObject, account: String, guid: String, type: String): Boolean {

        if (token.isBlank()) throw Exception("post: token is empty")

        val response = apiService?.post(token, data) ?: return false

        var accepted = false
        response.let {
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
                    accepted = true
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

        return accepted
    }

    /**
     * Uploads unsent documents (orders, cash, images, locations) to the 1C
     * server via HTTP POST. Used by the direct-1C / demo path.
     */
    private fun sendDocuments(account: String, stats: SyncStats): Flow<Result> = flow {

        val documents = dataRepository.getOrders(account)
        val type = Constants.DOCUMENT_ORDER

        if (documents.isNotEmpty()) {
            val contentByOrder = dataRepository.getPendingOrdersContent(account)
            for (document in documents) {
                ensureSameAccount(account)
                val content = (contentByOrder[document.guid] ?: emptyList()).map { it.toMap() }
                val documentData = document.toMap(account, content)
                val json = gson.toJsonTree(documentData).asJsonObject
                if (makePostRequest(json, account, document.guid, type)) stats.addSent(type)
            }
            emit(Result.Progress("$type: ${documents.size}"))
        }

        val cashList = dataRepository.getCash(account)
        val typeCash = Constants.DOCUMENT_CASH

        if (cashList.isNotEmpty()) {
            for (cash in cashList) {
                val cashContent = cash.toMap(account)
                val json = gson.toJsonTree(cashContent).asJsonObject
                if (makePostRequest(json, account, cash.guid, typeCash)) stats.addSent(typeCash)
            }
            emit(Result.Progress("$typeCash: ${cashList.size}"))
        }

        val images = dataRepository.getClientImages(account)
        val typeImage = Constants.DATA_CLIENT_IMAGE

        if (images.isNotEmpty()) {
            for (image in images) {
                val imageContent = image.toMap()
                val json = gson.toJsonTree(imageContent).asJsonObject
                if (makePostRequest(json, account, image.guid, typeImage)) stats.addSent(typeImage)
            }
            emit(Result.Progress("$typeImage: ${images.size}"))
        }

        val locations = dataRepository.getClientLocations(account)
        val typeLocation = Constants.DATA_CLIENT_LOCATION

        if (locations.isNotEmpty()) {
            for (location in locations) {
                val locationContent = location.toMap()
                val json = gson.toJsonTree(locationContent).asJsonObject
                if (makePostRequest(json, account, location.clientGuid, typeLocation)) stats.addSent(typeLocation)
            }
            emit(Result.Progress("$typeLocation: ${locations.size}"))
        }

    }

    private suspend fun saveData(account: String, time: Long, data: List<*>, stats: SyncStats) {
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
        stats.addReceived(dataRepository.saveData(dataset))
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

}