package ua.com.programmer.agentventa.fiscal

import java.io.File

data class FiscalOptions(
    val fiscalNumber: String = "",
    val cashier: String = "",
    val provider: String = "",
    val deviceId: String = "",
    val orderGuid: String = "",
    val fileDir: File? = null,
    val value: Int = 0,
    val useTextPrinter: Boolean = false,
    val printAreaWidth: Int = 0,
)
