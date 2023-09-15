package ua.com.programmer.agentventa.http

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import retrofit2.Retrofit
import ua.com.programmer.agentventa.dao.entity.UserAccount
import ua.com.programmer.agentventa.dao.entity.connectionSettingsChanged
import ua.com.programmer.agentventa.dao.entity.getBaseUrl
import ua.com.programmer.agentventa.dao.entity.getGuid
import ua.com.programmer.agentventa.dao.entity.isValidForHttpConnection
import ua.com.programmer.agentventa.dao.entity.toMap
import ua.com.programmer.agentventa.extensions.trimForLog
import ua.com.programmer.agentventa.logger.Logger
import ua.com.programmer.agentventa.repository.DataExchangeRepository
import ua.com.programmer.agentventa.repository.NetworkRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import ua.com.programmer.agentventa.settings.UserOptionsBuilder
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
    tokenRefresh: TokenRefresh
): NetworkRepository {

    private var _timestamp = 0L
    private var _options = UserOptionsBuilder.build(null)

    private var currentSystemAccount: UserAccount? = null
    private var account: UserAccount? = null
    private var apiService: HttpClientApi? = null
    private var token = ""
    private val counters = mutableMapOf<String, Int>()

    private val logTag = "NetworkRepo"

    private val typeToken = "token"
    private val maxTokenRefresh = 3

    private val gson = Gson()

    init {

        tokenRefresh.setRefreshToken(::refreshToken)

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

                //logger.d("NetworkRepositoryImpl", "set connection: ${it.dbServer}: ${it.dbUser}")
            }

        }.launchIn(CoroutineScope(Dispatchers.IO))
    }

    @Synchronized
    private fun refreshToken(tag: String = "interceptor"): String {
        counters[typeToken] = counters[typeToken]?.plus(1) ?: 1
        if (counters[typeToken]!! > maxTokenRefresh) {
            logger.w(logTag, "$tag: token refresh limit reached")
            throw Exception("token refresh limit reached")
        }
        val accountGuid = account?.guid ?: ""
        if (accountGuid.isEmpty()) {
            logger.w(logTag, "wrong account data")
            throw Exception("account guid is empty")
        }
        val response = runBlocking { apiService?.check(accountGuid) }
        val options = XMap(response as Map<*, *>)
        val newToken = options.getString("token")
        val canRead = options.getBoolean("read")

        account?.let {
            logger.d(logTag, "$tag: token received: ${newToken.trimForLog()}")
            val updatedAccount = it.copy(
                token = newToken,
                options = options.toJson(),
                license = options.getString("license")
            )
            runBlocking { userAccountRepository.saveAccount(updatedAccount) }
        }
        if (!canRead) {
            logger.w(logTag, "$tag: user has no read access")
            throw Exception("$tag: user has no read access")
        }
        return newToken
    }

    private fun onConnectionError() {
        token = ""
        account?.let {
            logger.w(logTag, "deleting token on error")
            val updatedAccount = it.copy(
                token = "",
            )
            runBlocking { userAccountRepository.saveAccount(updatedAccount) }
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
        if (accountGuid.isBlank()) {
            emit(Result.Error("No settings for connection"))
            return@flow
        }

        _options = UserOptionsBuilder.build(account)
        token = account?.token ?: ""

        if (token.isBlank() || _options.isEmpty) try {
            token = refreshToken("prepare")
        } catch (e: HttpException) {
            logger.e(logTag, "Token refresh error: $e")
            emit(Result.Error("${e.message()} (${e.code()})"))
            return@flow
        } catch (e: Exception) {
            logger.e(logTag, "Token refresh failed: $e")
            emit(Result.Error(e.message ?: "unknown error"))
            return@flow
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

    private fun sendDocuments(account: String): Flow<Result> = flow {

        val documents = dataRepository.getOrders(account)
        val type = Constants.DOCUMENT_ORDER

        for (document in documents) {
            val content = dataRepository.getOrderContent(account, document.guid).map { it.toMap() }
            val documentData = document.toMap(account, content)
            val json = gson.toJsonTree(documentData).asJsonObject
            makePostRequest(json, account, document.guid, type)
        }
        emit(Result.Progress("$type: ${documents.size}"))

        val images = dataRepository.getClientImages(account)
        val typeImage = Constants.DATA_CLIENT_IMAGE

        for (image in images) {
            val imageContent = image.toMap()
            val json = gson.toJsonTree(imageContent).asJsonObject
            makePostRequest(json, account, image.guid, typeImage)
        }
        emit(Result.Progress("$typeImage: ${images.size}"))

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
}