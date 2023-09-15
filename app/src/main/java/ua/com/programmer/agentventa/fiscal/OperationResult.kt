package ua.com.programmer.agentventa.fiscal

data class OperationResult(
    val success: Boolean = false,
    val message: String = "",
    val fileId: String = "",
    val receiptId: String = "",
)
