package ua.com.programmer.agentventa.dao.entity

import android.location.Location
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ua.com.programmer.agentventa.utility.Constants
import java.util.Locale

@Entity(tableName = "orders", indices = [Index(value = ["guid"], unique = true)])
data class Order(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Int = 0,
    @ColumnInfo(name = "db_guid") var databaseId: String = "",
    val date: String = "",
    val time: Long = 0,
    @ColumnInfo(name = "time_saved") var timeSaved: Long = 0,
    val number: Int = 0,
    val guid: String,
    @ColumnInfo(name = "company_guid") val companyGuid: String = "",
    @ColumnInfo(name = "company") val company: String = "",
    @ColumnInfo(name = "store_guid") val storeGuid: String = "",
    @ColumnInfo(name = "store") val store: String = "",
    @ColumnInfo(name = "delivery_date") var deliveryDate: String = "",
    var notes: String = "",
    @ColumnInfo(name = "client_guid") var clientGuid: String? = "",
    @ColumnInfo(name = "client_code2") var clientCode2: String? = "",
    @ColumnInfo(name = "client_description") var clientDescription: String? = "",
    var discount: Double = 0.0,
    @ColumnInfo(name = "price_type") var priceType: String = "",
    @ColumnInfo(name = "payment_type") var paymentType: String = "",
    @ColumnInfo(name = "is_fiscal") val isFiscal: Int = 0,
    var status: String = "",
    var price: Double = 0.0,
    var quantity: Double = 0.0,
    var weight: Double = 0.0,
    @ColumnInfo(name = "discount_value") var discountValue: Double = 0.0,
    @ColumnInfo(name = "next_payment") var nextPayment: Double = 0.0,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var distance: Double = 0.0,
    @ColumnInfo(name = "location_time") var locationTime: Long = 0,
    @ColumnInfo(name = "rest_type") var restType: String = "",
    @ColumnInfo(name = "is_return") var isReturn: Int = 0,
    @ColumnInfo(name = "is_processed") var isProcessed: Int = 0,
    @ColumnInfo(name = "is_sent") var isSent: Int = 0
)

fun Order.isCash() : Boolean = paymentType == "CASH"

// Used in a list for UI presentation
data class DocumentTotals(
    val documents: Int = 0,
    val returns: Int = 0,
    val weight: Double = 0.0,
    val sum: Double = 0.0,
    val discount: Double = 0.0,
    val sumReturn: Double = 0.0,
    val quantity: Double = 0.0
)

fun Order.setClient(client: LClient){
    clientGuid = client.guid
    clientCode2 = client.code
    clientDescription = client.description
    priceType = client.priceType
    discount = client.discount

    // calculate distance to client
    this.updateDistance(client.latitude, client.longitude)
}

// updates distance to client from current saved document location
// input parameters are client location
fun Order.updateDistance(latitude: Double, longitude: Double) {
    this.distance = 0.0
    if (latitude == 0.0 && longitude == 0.0) return
    if (this.hasLocation()) {
        val lc = Location("")
        lc.latitude = latitude
        lc.longitude = longitude
        this.distance = lc.distanceTo(this.getLocation()).toDouble()
    }
}

fun Order.hasLocation(): Boolean {
    return this.latitude != 0.0 || this.longitude != 0.0
}

fun Order.getLocation(): Location {
    val location = Location("")
    location.latitude = this.latitude
    location.longitude = this.longitude
    return location
}

// get distance text in km or m
fun Order.getDistanceText(): String {
    if (this.distance == 0.0 && this.hasLocation()) {
        return "!"
    }
    return if (this.distance > 1000) {
        String.format(Locale.getDefault(),"%.1f", this.distance / 1000) + " km"
    } else {
        String.format(Locale.getDefault(),"%.0f", this.distance) + " m"
    }
}

fun Order.toMap(account: String, content: List<Map<String,Any>>): Map<String,Any> {
    val map = mutableMapOf<String,Any>()
    map["type"] = Constants.DOCUMENT_ORDER
    map["userID"] = account
    map["date"] = date
    map["time"] = time
    map["time_saved"] = timeSaved
    map["number"] = number
    map["guid"] = guid
    map["delivery_date"] = deliveryDate
    map["notes"] = notes
    map["company_guid"] = companyGuid
    map["company"] = company
    map["store_guid"] = storeGuid
    map["store"] = store
    map["client_guid"] = clientGuid ?: ""
    map["client_id"] = clientCode2 ?: ""
    map["client_description"] = clientDescription ?: ""
    map["discount"] = discount
    map["price_type"] = priceType
    map["payment_type"] = paymentType
    map["is_fiscal"] = isFiscal
    map["status"] = status
    map["price"] = price
    map["quantity"] = quantity
    map["weight"] = weight
    map["discount_value"] = discountValue
    map["next_payment"] = nextPayment
    map["latitude"] = latitude
    map["longitude"] = longitude
    map["distance"] = distance
    map["location_time"] = locationTime
    map["rest_type"] = restType
    map["is_return"] = isReturn
    map["is_processed"] = isProcessed
    map["is_sent"] = isSent
    map["items"] = content
    return map
}