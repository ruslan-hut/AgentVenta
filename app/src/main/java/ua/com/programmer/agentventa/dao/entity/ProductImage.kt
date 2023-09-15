package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "product_images", primaryKeys = ["db_guid","product_guid","guid"])
data class ProductImage(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "product_guid") val productGuid: String = "",
    val guid: String,
    val url: String = "",
    val timestamp: Long = 0,
    val description: String = "",
    val type: String = "",
    val isDefault: Int = 0
){
    companion object Builder {
        fun build(data: XMap): ProductImage {
            return ProductImage(
                databaseId = data.getDatabaseId(),
                productGuid = data.getString("item_guid"),
                guid = data.getString("image_guid"),
                url = data.getString("url"),
                timestamp = data.getTimestamp(),
                description = data.getString("description"),
                type = data.getString("type"),
                isDefault = data.getInt("default")
            )
        }
    }
}

fun ProductImage.isValid(): Boolean {
    return productGuid.isNotEmpty() && databaseId.isNotEmpty()
}