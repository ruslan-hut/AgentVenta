package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "payment_types", primaryKeys = ["db_guid","payment_type"])
data class PaymentType(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "payment_type") val paymentType: String = "",
    @ColumnInfo(name = "is_fiscal") val isFiscal: Int = 0,
    @ColumnInfo(name = "is_default") val isDefault: Int = 0,
    val description: String = "",
    val timestamp: Long = 0
){
    companion object Builder {
        fun build(data: XMap): PaymentType {
            return PaymentType(
                databaseId = data.getDatabaseId(),
                paymentType = data.getString("payment_type"),
                isFiscal = data.getInt("is_fiscal"),
                isDefault = data.getInt("is_default"),
                description = data.getString("description"),
                timestamp = data.getTimestamp()
            )
        }
    }
}

fun PaymentType.isValid(): Boolean {
    return databaseId.isNotEmpty() && paymentType.isNotEmpty()
}
