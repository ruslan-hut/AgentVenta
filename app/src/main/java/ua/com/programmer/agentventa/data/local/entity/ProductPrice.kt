package ua.com.programmer.agentventa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "product_prices", primaryKeys = ["product_guid","db_guid","price_type"])
data class ProductPrice(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "product_guid") val productGuid: String = "",
    @ColumnInfo(name = "price_type") val priceType: String = "",
    val timestamp: Long = 0,
    val price: Double = 0.0
){
    companion object Builder {
        fun build(item: XMap): ProductPrice {
            return ProductPrice(
                databaseId = item.getDatabaseId(),
                timestamp = item.getTimestamp(),
                priceType = item.getString("price_type"),
                productGuid = item.getString("item_guid"),
                price = item.getDouble("price")
            )
        }
    }
}

fun ProductPrice.isValid(): Boolean {
    return databaseId.isNotEmpty() && productGuid.isNotEmpty() && priceType.isNotEmpty()
}

data class LPrice(
    val priceType: String = "",
    val price: Double = 0.0,
    val description: String = "",
    val basePrice: Double = 0.0,
    val isCurrent: Boolean = false
)

fun LPrice.markup(): Double {
    return if (basePrice > 0.0) {
        (price - basePrice) / basePrice * 100
    } else {
        0.0
    }
}