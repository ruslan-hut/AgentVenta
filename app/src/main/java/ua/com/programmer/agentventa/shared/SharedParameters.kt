package ua.com.programmer.agentventa.shared

data class SharedParameters(
    val filter: String = "",
    val groupGuid: String = "",
    val orderGuid: String = "",
    val companyGuid: String = "",
    val company: String = "",
    val storeGuid: String = "",
    val store: String = "",
    val sortByName: Boolean = false,
    val restsOnly: Boolean = false,
    val ignoreBarcodeReads: Boolean = false,
    val clientProducts: Boolean = false,
    val priceType: String = "",
    val currentAccount: String = "",
)
