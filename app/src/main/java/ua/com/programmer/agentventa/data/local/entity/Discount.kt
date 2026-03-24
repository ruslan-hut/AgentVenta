package ua.com.programmer.agentventa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "discounts", primaryKeys = ["db_guid", "client_guid", "product_guid"])
data class Discount(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "client_guid") val clientGuid: String = "",
    @ColumnInfo(name = "product_guid") val productGuid: String = "",
    val discount: Double = 0.0,
    val timestamp: Long = 0,
){
    companion object Builder {
        fun build(item: XMap): Discount {
            return Discount(
                databaseId = item.getDatabaseId(),
                timestamp = item.getTimestamp(),
                clientGuid = item.getString("client_guid"),
                productGuid = item.getString("item_guid"),
                discount = item.getDouble("discount")
            )
        }
    }
}

fun Discount.isValid(): Boolean {
    return databaseId.isNotEmpty()
}

data class LDiscount(
    val productGuid: String = "",
    val description: String = "",
    val discount: Double = 0.0,
    val isGroup: Boolean = false,
)

data class DiscountMatch(
    val clientGuid: String = "",
    val productGuid: String = "",
    val discount: Double = 0.0,
)
