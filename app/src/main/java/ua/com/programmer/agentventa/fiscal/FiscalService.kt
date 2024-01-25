package ua.com.programmer.agentventa.fiscal

interface FiscalService {
    val serviceId: String
    fun currentState(): FiscalState
    suspend fun init(fiscalOptions: FiscalOptions): OperationResult
    suspend fun cashierLogin(fiscalOptions: FiscalOptions): OperationResult
    suspend fun cashierLogout(fiscalOptions: FiscalOptions): OperationResult
    suspend fun checkStatus(fiscalOptions: FiscalOptions): OperationResult
    suspend fun openShift(fiscalOptions: FiscalOptions): OperationResult
    suspend fun closeShift(fiscalOptions: FiscalOptions): OperationResult
    suspend fun createXReport(fiscalOptions: FiscalOptions): OperationResult
    suspend fun createReceipt(fiscalOptions: FiscalOptions): OperationResult
    suspend fun getReceipt(fiscalOptions: FiscalOptions): OperationResult
    suspend fun getReceiptText(fiscalOptions: FiscalOptions): OperationResult
    suspend fun createServiceReceipt(fiscalOptions: FiscalOptions): OperationResult
}