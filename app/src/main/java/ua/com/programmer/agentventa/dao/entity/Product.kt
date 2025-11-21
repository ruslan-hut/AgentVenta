package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import ua.com.programmer.agentventa.utility.Constants
import ua.com.programmer.agentventa.utility.XMap

@Entity(
    tableName = "products",
    primaryKeys = ["guid", "db_guid"],
    indices = [
        Index(value = ["db_guid"]),
        Index(value = ["db_guid", "group_guid"]),      // For group filtering
        Index(value = ["db_guid", "is_group"]),        // For groups vs items
        Index(value = ["db_guid", "barcode"]),         // For barcode lookup
        Index(value = ["db_guid", "description_lc"])   // For search queries
    ]
)
data class Product (
    @ColumnInfo(name = "db_guid") var databaseId: String = "",
    val guid: String,
    val timestamp: Long = 0,
    val code1: String = "", // for representation in UI and user search
    val code2: String = "", // for documents data, product identify
    @ColumnInfo(name = "vendor_code") val vendorCode: String = "",
    @ColumnInfo(name = "vendor_status") val vendorStatus: String = "",
    val status: String = "",
    val barcode: String = "",
    val sorting: Int = 0,
    val description: String = "",
    @ColumnInfo(name = "description_lc") val descriptionLc: String = "",
    val price: Double = 0.0,
    @ColumnInfo(name = "min_price") val minPrice: Double = 0.0,
    @ColumnInfo(name = "base_price") val basePrice: Double = 0.0,
    val quantity: Double = 0.0,
    @ColumnInfo(name = "package_only") val packageOnly: Int = 0,
    @ColumnInfo(name = "package_value") val packageValue: Double = 0.0,
    val weight: Double = 0.0,
    val unit: String = "",
    @ColumnInfo(name = "rest_type") val restType: String = "",
    val indivisible: Int = 0,
    @ColumnInfo(name = "group_guid") val groupGuid: String = "",
    @ColumnInfo(name = "is_active") val isActive: Int = 0,
    @ColumnInfo(name = "is_group") val isGroup: Int = 0,
){
    companion object Builder {
        fun build(item: XMap): Product {
            return Product(
                databaseId = item.getDatabaseId(),
                timestamp = item.getTimestamp(),
                guid = item.getString("guid"),
                description = item.getString("description"),
                descriptionLc = item.getString("description").lowercase(),
                code1 = item.getString("code1"),
                code2 = item.getString("code2"),
                barcode = item.getString("barcode"),
                unit = item.getString("unit"),
                sorting = item.getInt("sorting"),
                price = item.getDouble("price"),
                minPrice = item.getDouble("min_price"),
                basePrice = item.getDouble("base_price"),
                quantity = item.getDouble("quantity"),
                weight = item.getDouble("weight"),
                packageOnly = item.getInt("package_only"),
                packageValue = item.getDouble("package_value"),
                indivisible = item.getInt("indivisible"),
                vendorCode = item.getString("vendor_code"),
                vendorStatus = item.getString("vendor_status"),
                groupGuid = item.getString("group_guid"),
                restType = item.getString("rest_type"),
                isActive = item.getInt("is_active"),
                isGroup = item.getInt("is_group")
            )
        }
    }
}

// UI representation of a product
data class LProduct(
    val guid: String = "",
    val code: String = "",
    val vendorCode: String = "",
    val description: String = "",
    val unit: String = "",
    val unitType: String = "",
    val groupName: String? = "",
    val rest: Double = 0.0,
    val quantity: Double = 0.0,
    val weight: Double = 0.0,
    val price: Double = 0.0,
    val priceType: String = "",
    val basePrice: Double = 0.0,
    val minPrice: Double = 0.0,
    val orderPrice: Double = 0.0,
    val sum: Double = 0.0,
    val packageValue: Double = 0.0,
    val packageOnly: Boolean = false,
    val indivisible: Boolean = false,
    val isGroup: Boolean = false,
    val isActive: Boolean = false,
    val isPacked: Boolean = false,
    val isDemand: Boolean = false,
    val modeSelect: Boolean = false,
    val imageUrl: String? = "",
    val imageGuid: String? = "",
)

fun Product.isValid(): Boolean {
    return description.isNotBlank() && guid.isNotBlank() && databaseId.isNotBlank()
}

fun LProduct.convertQuantityPerDefaultUnit(qty: Double, unitType: String) : Double {
    return when (unitType) {
        Constants.UNIT_PACKAGE -> if (packageValue == 0.0) qty else qty * packageValue
        Constants.UNIT_WEIGHT -> if (weight == 0.0) qty else qty / weight
        else -> qty
    }
}

fun LProduct.convertPricePerDefaultUnit(prc: Double, unitType: String) : Double {
    return when (unitType) {
        Constants.UNIT_PACKAGE -> if (packageValue == 0.0) prc else prc / packageValue
        Constants.UNIT_WEIGHT -> if (weight == 0.0) prc else prc * weight
        else -> prc
    }
}

fun LProduct.hasImageData() : Boolean {
    return !imageUrl.isNullOrBlank() || !imageGuid.isNullOrBlank()
}
