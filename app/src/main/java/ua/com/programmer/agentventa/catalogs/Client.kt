package ua.com.programmer.agentventa.catalogs

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.DataBase
import ua.com.programmer.agentventa.utility.DataBaseItem
import ua.com.programmer.agentventa.utility.Utils

class Client(context: Context, guid: String) {

    private var db: DataBase
    @JvmField
    var code2 = ""
    @JvmField
    var description = ""
    @JvmField
    var clientID: Long = 0
    @JvmField
    var discount = 0.0
    @JvmField
    var bonus = 0.0
    @JvmField
    var priceType = 0
    @JvmField
    var GUID = ""
    @JvmField
    var notes = ""
    @JvmField
    var isBanned = false

    //private final Context mContext;
    private val utils = Utils()
    @JvmField
    var groupName = ""
    var groupGUID = ""
        private set
    private var latitude = 0.0
    private var longitude = 0.0
    @JvmField
    var isGroup = false
    @JvmField
    var code1 = ""
    @JvmField
    var address = ""
    @JvmField
    var phone = ""
    private var banMessage = ""
    private var banMessageDefault: String

    var isNew = true
        private set

    fun setClient(guid: String) {
        val data = if (guid.isEmpty()) {
            DataBaseItem()
        } else {
            db.getClient(guid)
        }
        setClient(data)
    }

    private fun setClient(clientData: DataBaseItem) {
        //if (clientData.hasValues()) {
            isNew = !clientData.hasValues()
            clientID = clientData.getLong("_id")
            description = clientData.getString("description")
            GUID = clientData.getString("guid")
            code1 = clientData.getString("code1")
            code2 = clientData.getString("code2")
            groupName = utils.getString(clientData.getString("groupName"))
            groupGUID = clientData.getString("group_guid")
            isGroup = clientData.getString("is_group") == "1"
            discount = clientData.getDouble("discount")
            bonus = clientData.getDouble("bonus")
            priceType = clientData.getInt("price_type")
            address = utils.getString(clientData.getString("address"))
            phone = utils.getString(clientData.getString("phone"))
            latitude = clientData.getDouble("latitude")
            longitude = clientData.getDouble("longitude")
            notes = clientData.getString("notes")
            isBanned = clientData.getString("is_banned") == "1"
            banMessage = clientData.getString("ban_message")
        //}
    }

    val debt: Double
        get() = db.getClientTotalDebt(GUID)

    fun getBanMessage(): String {
        return banMessage.ifEmpty { banMessageDefault }
    }

    val coordinates: LatLng?
        get() = if (latitude != 0.0 && longitude != 0.0) {
            LatLng(latitude, longitude)
        } else null

    init {
        db = DataBase.getInstance(context)
        setClient(guid)
        banMessageDefault = context.getString(R.string.ban_message)
    }
}