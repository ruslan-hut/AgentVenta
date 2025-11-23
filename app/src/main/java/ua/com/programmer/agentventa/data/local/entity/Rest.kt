package ua.com.programmer.agentventa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "rests", primaryKeys = ["db_guid","company_guid","store_guid","product_guid"])
data class Rest(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "company_guid") val companyGuid: String = "",
    @ColumnInfo(name = "store_guid") val storeGuid: String = "",
    @ColumnInfo(name = "product_guid") val productGuid: String = "",
    val quantity: Double = 0.0,
    val timestamp: Long = 0,
){
    companion object Builder {
        fun build(data: XMap): Rest {
            return Rest(
                databaseId = data.getDatabaseId(),
                companyGuid = data.getString("company_guid"),
                storeGuid = data.getString("store_guid"),
                productGuid = data.getString("product_guid"),
                quantity = data.getDouble("quantity"),
                timestamp = data.getTimestamp(),
            )
        }
    }
}

fun Rest.isValid(): Boolean {
    return databaseId.isNotEmpty()
            && companyGuid.isNotEmpty()
            && storeGuid.isNotEmpty()
            && productGuid.isNotEmpty()
}