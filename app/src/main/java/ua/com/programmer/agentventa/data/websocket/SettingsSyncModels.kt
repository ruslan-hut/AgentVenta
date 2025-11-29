package ua.com.programmer.agentventa.data.websocket

import com.google.gson.annotations.SerializedName

/**
 * Settings data for WebSocket synchronization.
 * Now includes full UserAccount data, not just options.
 */
data class SettingsData(
    @SerializedName("user_email")
    val userEmail: String,

    @SerializedName("device_uuid")
    val deviceUuid: String,

    @SerializedName("description")
    val description: String = "",

    @SerializedName("license")
    val license: String = "",

    @SerializedName("data_format")
    val dataFormat: String = "",

    @SerializedName("db_server")
    val dbServer: String = "",

    @SerializedName("db_name")
    val dbName: String = "",

    @SerializedName("db_user")
    val dbUser: String = "",

    @SerializedName("db_password")
    val dbPassword: String = "",

    @SerializedName("token")
    val token: String = "",

    @SerializedName("relay_server")
    val relayServer: String = "",

    @SerializedName("use_websocket")
    val useWebSocket: Boolean = true,

    @SerializedName("options")
    val options: SettingsOptions,

    @SerializedName("found")
    val found: Boolean? = null,  // Only in response

    @SerializedName("updated_at")
    val updatedAt: String? = null  // Only in response
)

/**
 * Settings options structure matching UserOptions.
 * Only includes fields that should be synced via WebSocket.
 */
data class SettingsOptions(
    @SerializedName("write")
    val write: Boolean = false,

    @SerializedName("read")
    val read: Boolean = false,

    @SerializedName("loadImages")
    val loadImages: Boolean = false,

    @SerializedName("useCompanies")
    val useCompanies: Boolean = false,

    @SerializedName("useStores")
    val useStores: Boolean = false,

    @SerializedName("clientsLocations")
    val clientsLocations: Boolean = false,

    @SerializedName("fiscalProvider")
    val fiscalProvider: String = "",

    @SerializedName("fiscalProviderId")
    val fiscalProviderId: String = "",

    @SerializedName("fiscalDeviceId")
    val fiscalDeviceId: String = "",

    @SerializedName("fiscalCashierPin")
    val fiscalCashierPin: String = "",

    // Additional fields from UserOptions that might be synced
    @SerializedName("allowPriceTypeChoose")
    val allowPriceTypeChoose: Boolean = false,

    @SerializedName("allowReturn")
    val allowReturn: Boolean = false,

    @SerializedName("requireDeliveryDate")
    val requireDeliveryDate: Boolean = false,

    @SerializedName("locations")
    val locations: Boolean = false,

    @SerializedName("editLocations")
    val editLocations: Boolean = false,

    @SerializedName("checkOrderLocation")
    val checkOrderLocation: Boolean = false,

    @SerializedName("printingEnabled")
    val printingEnabled: Boolean = false,

    @SerializedName("showClientPriceOnly")
    val showClientPriceOnly: Boolean = false,

    @SerializedName("setClientPrice")
    val setClientPrice: Boolean = false,

    @SerializedName("clientsDirections")
    val clientsDirections: Boolean = false,

    @SerializedName("clientsProducts")
    val clientsProducts: Boolean = false,

    @SerializedName("useDemands")
    val useDemands: Boolean = false,

    @SerializedName("usePackageMark")
    val usePackageMark: Boolean = false,

    @SerializedName("currency")
    val currency: String = "",

    @SerializedName("defaultClient")
    val defaultClient: String = ""
)

/**
 * Result of settings sync operation
 */
sealed class SettingsSyncResult {
    data class Success(val settings: SettingsData) : SettingsSyncResult()
    data class NotFound(val userEmail: String) : SettingsSyncResult()
    data class Error(val message: String) : SettingsSyncResult()
}
