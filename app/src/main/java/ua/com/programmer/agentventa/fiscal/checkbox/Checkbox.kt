package ua.com.programmer.agentventa.fiscal.checkbox

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ua.com.programmer.agentventa.extensions.roundToInt
import ua.com.programmer.agentventa.fiscal.FiscalOptions
import ua.com.programmer.agentventa.fiscal.FiscalService
import ua.com.programmer.agentventa.fiscal.FiscalState
import ua.com.programmer.agentventa.fiscal.OperationResult
import ua.com.programmer.agentventa.repository.OrderRepository
import ua.com.programmer.agentventa.utility.Constants
import ua.com.programmer.agentventa.utility.XMap
import java.io.File
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.util.UUID

class Checkbox(private val orderRepository: OrderRepository): FiscalService {

    override val serviceId = Constants.FISCAL_PROVIDER_CHECKBOX

    private val url = "https://api.checkbox.in.ua/"
    private var retrofit: Retrofit? = null
    private var api: CheckboxApi? = null
    private var options: FiscalOptions? = null
    private val crashlytics = FirebaseCrashlytics.getInstance()

    private var token: String = ""
    private var license: String = ""
    private var offlineMode: Boolean = false
    private var shiftId: String = ""
    private var shiftOpenedAt: String = ""

    private fun build(): CheckboxApi {
        if (api != null) return api as CheckboxApi

        val okBuilder = OkHttpClient.Builder()
            .addInterceptor {chain ->

                val builder = chain.request().newBuilder()
                builder
                    .addHeader("X-Client-Name", "ua.com.programmer.agentventa")
                    .addHeader("X-Client-Version", "v0.1")
                    .addHeader("X-Device-ID", "${options?.deviceId}")
                if (license.isNotBlank()) {
                    builder.addHeader("X-License-Key", license)
                }
                if (token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
                val request = builder.build()
                chain.proceed(request)
            }

//        if (BuildConfig.DEBUG) {
//            val logInterceptor = HttpLoggingInterceptor()
//            logInterceptor.level = HttpLoggingInterceptor.Level.BODY
//            okBuilder.addInterceptor(logInterceptor)
//        }
        val okHttp = okBuilder.build()

        retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl(url)
            .client(okHttp)
            .build()

        api = retrofit?.create(CheckboxApi::class.java) as CheckboxApi

        return api as CheckboxApi
    }

    override fun currentState(): FiscalState {
        return FiscalState(
            authorized = token.isNotBlank(),
            offline = offlineMode,
            shiftOpened = shiftId.isNotBlank(),
            shiftOpenedAt = shiftOpenedAt,
        )
    }

    override suspend fun init(fiscalOptions: FiscalOptions): OperationResult {
        this.options = fiscalOptions
        if (license != fiscalOptions.fiscalNumber) {
            license = fiscalOptions.fiscalNumber
            token = ""
            api = null
        }
        return OperationResult(true)
    }

    override suspend fun cashierLogin(fiscalOptions: FiscalOptions): OperationResult {
        val response = callApi {
            build().cashierLogin(CashierPin(fiscalOptions.cashier))
        }
        token = response.getString("access_token")
        if (token.isNotBlank()) {
            checkStatus(options as FiscalOptions)
        }
        return OperationResult(token.isNotBlank(), response.getString("message"))
    }

    override suspend fun cashierLogout(fiscalOptions: FiscalOptions): OperationResult {
        val response = callApi {
            build().cashierLogout()
        }
        val message = response.getString("message")
        token = ""
        return OperationResult(message.isBlank(), message)
    }

    override suspend fun checkStatus(fiscalOptions: FiscalOptions): OperationResult {
        val response = callApi {
            build().checkStatus()
        }
        val message = response.getString("message")
        offlineMode = response.getBoolean("offline_mode")
        if (response.getBoolean("has_shift")) {
            getShift()
        } else {
            shiftId = ""
        }
        return OperationResult(message.isBlank(), message)
    }

    private suspend fun getShiftById(id: String): OperationResult {
        val response = callApi {
            build().getShift(id)
        }
        val message = response.getString("message")
        if (message.isNotBlank()) return OperationResult(false, message)
        val status = response.getString("status")
        shiftId = if (status == "OPENED") {
            response.getString("id")
        } else {
            ""
        }
        shiftOpenedAt = convertDate(response.getString("opened_at"))
        return OperationResult(true)
    }

    override suspend fun openShift(fiscalOptions: FiscalOptions): OperationResult {
        if (shiftId.isNotBlank()) return OperationResult(false, "Зміна вже відкрита")
        val newShiftId = UUID.randomUUID().toString()
        val response = callApi {
            build().createShift(Shift(newShiftId))
        }
        val message = response.getString("message")
        if (message.isNotBlank()) {
            return OperationResult(false, message)
        }

        while (shiftId.isBlank()) {
            withContext(Dispatchers.IO) {
                Thread.sleep(1000)
            }
            val getShiftResponse = getShiftById(newShiftId)
            if (!getShiftResponse.success) {
                return getShiftResponse
            }
        }

        return OperationResult(true)
    }

    override suspend fun closeShift(fiscalOptions: FiscalOptions): OperationResult {
        if (shiftId.isBlank()) return OperationResult(false, "Немає відкритої зміни")
        val response = callApi {
            build().closeShift()
        }
        var message = response.getString("message")
        if (message.isNotBlank()) {
            return OperationResult(false, message)
        }

        var status = response.getString("status")
        while (status == "CLOSING") {
            withContext(Dispatchers.IO) {
                Thread.sleep(1000)
            }
            val shift = callApi {
                build().getShift(shiftId)
            }
            message = shift.getString("message")
            status = shift.getString("status")
            if (message.isNotBlank()) return OperationResult(false, message)
        }

        if (message.isBlank()) {
            shiftId = ""
        }

        if (status == "CLOSED") {

            val zReportData = response.getString("z_report")
            if (zReportData.isNotBlank()) {
                val report = XMap(zReportData)
                val id = report.getString("id")
                return getReportFile(fiscalOptions, id)
            }

        }

        return OperationResult(
            success = false,
            message = "$status $message",
        )
    }

    override suspend fun createXReport(fiscalOptions: FiscalOptions): OperationResult {
        if (shiftId.isBlank()) return OperationResult(false, "Немає відкритої зміни")
        val response = callApi {
            build().createXReport()
        }
        val message = response.getString("message")
        if (message.isNotBlank()) {
            return OperationResult(false, message)
        }

        val id = response.getString("id")
        return getReportFile(fiscalOptions, id)
    }

    private suspend fun getReportFile(fiscalOptions: FiscalOptions, id: String): OperationResult {
        val file: File
        val fileName: String

        if (fiscalOptions.useTextPrinter) {
            fileName = "$id.txt"
            file = File(options?.fileDir, fileName)
            try {
                val fileResponse = build().getReportText(id, fiscalOptions.printAreaWidth)
                saveFileData(fileResponse, file)
            } catch (e: Exception) {
                return OperationResult(false, "Помилка отримання звіту. ${e.message}")
            }
        } else {
            fileName = "$id.png"
            file = File(options?.fileDir, fileName)
            try {
                val fileResponse = build().getReportPng(id)
                saveFileData(fileResponse, file)
            } catch (e: Exception) {
                return OperationResult(false, "Помилка отримання звіту. ${e.message}")
            }
        }

        if (file.exists()) {
            return OperationResult(
                success = true,
                fileId = fileName,
            )
        }
        return OperationResult(false, "Помилка отримання звіту. Файл не збережено")
    }

    override suspend fun createReceipt(fiscalOptions: FiscalOptions): OperationResult {
        if (shiftId.isBlank()) return OperationResult(false, "Немає відкритої зміни")

        // before create receipt check receipt status
        val checkResult = checkReceiptStatus(fiscalOptions.orderGuid)
        if (checkResult.success) {
            // receipt is already registered
            return OperationResult(true, receiptId = fiscalOptions.orderGuid)
        }else if (checkResult.receiptId.isNotBlank()) {
            // receipt is already registered but not done
            return checkResult
        }

        val order = orderRepository.getOrder(fiscalOptions.orderGuid)
            ?: return OperationResult(false, "Не знайдено документ")

        val content = orderRepository.getContent(fiscalOptions.orderGuid)
        val receipt = Receipt(
            id = order.guid,
            goods = content.map {
                ReceiptLine(
                    good = Good(
                        code = it.code,
                        name = it.description,
                        price = it.price.roundToInt(100),
                    ),
                    quantity = it.quantity.roundToInt(1000),
                    isReturn = order.isReturn == 1,
                )
            },
            payments = listOf(
                Payment(
                    type = mapPaymentType(order.paymentType),
                    value = order.price.roundToInt(100),
                ),
            ),
        )

        val response = callApi {
            build().createReceipt(receipt)
        }

        val message = response.getString("message")
        val status = response.getString("status")

        if (status.isBlank()) {
            crashlytics.log("Receipt: $receipt")
            crashlytics.recordException(Exception("Checkbox createReceipt failed; $message"))
            return OperationResult(false, "Не вдалося створити чек. $message")
        }

        return checkReceiptStatus(receipt.id)
    }

    private suspend fun checkReceiptStatus(id: String): OperationResult {

        val response = callApi {
            build().getReceipt(id)
        }
        val message = response.getString("message")
        if (message.isNotBlank()) return OperationResult(false, "Помилка отримання чека. $message")

        var status = response.getString("status")

        while (status == "CREATED") {
            withContext(Dispatchers.IO) {
                Thread.sleep(1000)
            }
            val receiptResponse = callApi {
                build().getReceipt(id)
            }
            val msg = receiptResponse.getString("message")
            if (msg.isNotBlank()) return OperationResult(false, "Помилка реєстрації чека. $msg", receiptId = id)
            status = receiptResponse.getString("status")
        }
        if (status == "DONE") {
            return OperationResult(true, receiptId = id)
        }

        return OperationResult(false, "Помилка реєстрації чека. $status", receiptId = id)
    }

    override suspend fun getReceipt(fiscalOptions: FiscalOptions): OperationResult {
        if (fiscalOptions.useTextPrinter) return getReceiptText(fiscalOptions)

        val id = fiscalOptions.orderGuid
        val fileName = "${id}.png"
        if (id.isBlank()) return OperationResult(false, "Не вказано ID чека")

        try {
            val response = build().getReceiptPng(id)

            val file = File(options?.fileDir, fileName)
            saveFileData(response, file)

        } catch (e: Exception) {
            return OperationResult(false, "Помилка отримання чека. ${e.message}")
        }

        val file = File(options?.fileDir, fileName)
        if (file.exists()) {
            return OperationResult(true, fileId = fileName)
        }
        return OperationResult(false, "Помилка отримання чека. Файл не збережено")
    }

    override suspend fun getReceiptText(fiscalOptions: FiscalOptions): OperationResult {
        val id = fiscalOptions.orderGuid
        val fileName = "${id}.txt"
        if (id.isBlank()) return OperationResult(false, "Не вказано ID чека")

        try {
            val response = build().getReceiptTxt(id, fiscalOptions.printAreaWidth)

            val file = File(options?.fileDir, fileName)
            saveFileData(response, file)

        } catch (e: Exception) {
            return OperationResult(false, "Помилка отримання чека. ${e.message}")
        }

        val file = File(options?.fileDir, fileName)
        if (file.exists()) {
            return OperationResult(true, fileId = fileName)
        }
        return OperationResult(false, "Помилка отримання чека. Файл не збережено")
    }

    override suspend fun createServiceReceipt(fiscalOptions: FiscalOptions): OperationResult {
        if (shiftId.isBlank()) return OperationResult(false, "Немає відкритої зміни")
        val receipt = ServiceReceipt(
            id = UUID.randomUUID().toString(),
            payment = Payment(
                        type = "CASH",
                        value = fiscalOptions.value,
                ),
        )
        val response = callApi {
            build().createServiceReceipt(receipt)
        }
        var message = response.getString("message")

        var status = response.getString("status")
        if (status.isBlank()) return OperationResult(false, "Не вдалося створити чек. $message")

        while (status == "CREATED") {
            withContext(Dispatchers.IO) {
                Thread.sleep(1000)
            }
            val receiptResponse = callApi {
                build().getReceipt(receipt.id)
            }
            message = response.getString("message")
            if (message.isNotBlank()) return OperationResult(false, "Помилка реєстрації чека. $message")
            status = receiptResponse.getString("status")
        }
        if (status == "DONE") {
            return OperationResult(true, receiptId = receipt.id)
        }

        return OperationResult(false, "Помилка реєстрації чека. $status")
    }

    private suspend fun saveFileData(response: ResponseBody, file: File) {
        val inputStream = response.byteStream()
        val outputStream = file.outputStream()

        withContext(Dispatchers.IO) {
            val data = ByteArray(4096)
            var count: Int
            while (inputStream.read(data).also { count = it } != -1) {
                outputStream.write(data, 0, count)
            }
            outputStream.flush()
            inputStream.close()
            outputStream.close()
        }
    }

    private suspend fun getShift(): OperationResult {
        val response = callApi {
            build().getCashierShift()
        }
        val message = response.getString("message")
        val status = response.getString("status")
        shiftId = if (status == "OPENED") {
            response.getString("id")
        } else {
            ""
        }
        shiftOpenedAt = convertDate(response.getString("opened_at"))
        return OperationResult(message.isBlank(), message)
    }

    private suspend fun callApi(action: suspend () -> Map<String, Any>?): XMap {
        val response = try {
            action()
        } catch (e: HttpException) {
            readErrorMessage(e)
        } catch (e: ProtocolException) {
            if (e.message?.contains("HTTP 205") == true) {
                mapOf("message" to "")
            } else {
                mapOf("message" to e)
            }
        } catch (_: SocketTimeoutException) {
            mapOf("message" to "Таймаут з'єднання з сервером фіскалізації")
        } catch (e: Exception) {
            mapOf("message" to e)
        }
        return XMap(response ?: mapOf("message" to "Немає відповіді від сервера фіскалізації"))
    }

    private fun readErrorMessage(e: HttpException): Map<String,String> {
        val errorBody = e.response()?.errorBody()?.string()
        return if (errorBody != null) {
            val errorMap = XMap(errorBody)
            mapOf("message" to errorMap.getString("message"))
        } else {
            mapOf("message" to e.message())
        }
    }

    private fun mapPaymentType(type: String): String {
        if (type.contains("CASH")) return "CASH"
        return "CASHLESS"
    }

    private fun convertDate(date: String): String {
        if (date.length < 19) return ""
        return date.replace("T", " ").substring(0, 19)
    }

}