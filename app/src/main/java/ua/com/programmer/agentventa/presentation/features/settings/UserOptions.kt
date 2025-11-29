package ua.com.programmer.agentventa.presentation.features.settings

import org.json.JSONObject

data class UserOptions(
    val isEmpty: Boolean,
    val userId: String = "",
    val allowPriceTypeChoose: Boolean = false,
    val allowReturn: Boolean = false,
    val requireDeliveryDate: Boolean = false,
    val locations: Boolean = false,
    val lastLocationTime: Long = 0,
    val sendPushToken: Boolean = false,
    val loadImages: Boolean = false,
    val watchList: String = "",
    val checkOrderLocation: Boolean = false,
    val read: Boolean = false,
    val token: String = "",
    val write: Boolean = false,
    val license: String = "",
    val editLocations: Boolean = false,
    val currency: String = "",
    val printingEnabled: Boolean = false,
    val showClientPriceOnly: Boolean = false,
    val setClientPrice: Boolean = false,
    val differentialUpdates: Boolean = false,
    val clientsLocations: Boolean = false,
    val clientsDirections: Boolean = false,
    val clientsProducts: Boolean = false,
    val defaultClient: String = "",
    val useDemands: Boolean = false,
    val useCompanies: Boolean = false,
    val useStores: Boolean = false,
    val usePackageMark: Boolean = false,
    val fiscalNumber: String = "",
    val fiscalCashier: String = "",
    val fiscalDeviceId: String = "",
    val fiscalProvider: String = "",
)

fun UserOptions.toJson(): String {
    val json = JSONObject()
    json.put("userId", userId)
    json.put("allowPriceTypeChoose", allowPriceTypeChoose)
    json.put("allowReturn", allowReturn)
    json.put("requireDeliveryDate", requireDeliveryDate)
    json.put("locations", locations)
    json.put("lastLocationTime", lastLocationTime)
    json.put("sendPushToken", sendPushToken)
    json.put("loadImages", loadImages)
    json.put("watchList", watchList)
    json.put("checkOrderLocation", checkOrderLocation)
    json.put("read", read)
    json.put("token", token)
    json.put("write", write)
    json.put("license", license)
    json.put("editLocations", editLocations)
    json.put("currency", currency)
    json.put("printingEnabled", printingEnabled)
    json.put("showClientPriceOnly", showClientPriceOnly)
    json.put("setClientPrice", setClientPrice)
    json.put("differentialUpdates", differentialUpdates)
    json.put("clientsLocations", clientsLocations)
    json.put("clientsDirections", clientsDirections)
    json.put("clientsProducts", clientsProducts)
    json.put("defaultClient", defaultClient)
    json.put("useDemands", useDemands)
    json.put("useCompanies", useCompanies)
    json.put("useStores", useStores)
    json.put("usePackageMark", usePackageMark)
    json.put("fiscalNumber", fiscalNumber)
    json.put("fiscalCashier", fiscalCashier)
    json.put("fiscalDeviceId", fiscalDeviceId)
    json.put("fiscalProvider", fiscalProvider)
    return json.toString()
}
