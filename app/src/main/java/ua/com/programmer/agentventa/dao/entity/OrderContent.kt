package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_content")
data class OrderContent(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Int = 0,
    @ColumnInfo(name = "order_guid") val orderGuid: String,
    @ColumnInfo(name = "product_guid") val productGuid: String,
    @ColumnInfo(name = "unit_code") val unitCode: String = "",
    val quantity: Double = 0.0,
    val weight: Double = 0.0,
    val price: Double = 0.0,
    val sum: Double = 0.0,
    val discount: Double = 0.0,
    @ColumnInfo(name = "is_demand") val isDemand: Int = 0,
    @ColumnInfo(name = "is_packed") val isPacked: Int = 0,
)

data class LOrderContent(
    val id: Int = 0,
    val orderGuid: String = "",
    val productGuid: String = "",
    val code: String = "",
    val description: String = "",
    val groupName: String = "",
    val unit: String = "",
    val quantity: Double = 0.0,
    val weight: Double = 0.0,
    val price: Double = 0.0,
    val sum: Double = 0.0,
    val discount: Double = 0.0,
    val isDemand: Boolean = false,
    val isPacked: Boolean = false,
)

fun LOrderContent.getQuantityFormatted() : String {
    if (quantity == 0.0) return ""
    return if (quantity % 1 == 0.0) String.format("%.0f", quantity)
    else String.format("%.3f", quantity)
}

fun LOrderContent.getPriceFormatted() : String {
    return if (price == 0.0) "0.00"
    else String.format("%.2f", price)
}

fun LOrderContent.getSumFormatted() : String {
    return if (sum == 0.0) "0.00"
    else String.format("%.2f", sum)
}

fun LOrderContent.toMap(): Map<String,Any> {
    return mapOf(
        "order_guid" to orderGuid,
        "item_guid" to productGuid,
        "code1" to code,
        "code2" to code,
        "description" to description,
        "unit_code" to unit,
        "quantity" to quantity,
        "weight" to weight,
        "price" to price,
        "sum" to sum,
        "sum_discount" to discount,
        "is_demand" to isDemand,
        "is_packed" to isPacked
    )
}