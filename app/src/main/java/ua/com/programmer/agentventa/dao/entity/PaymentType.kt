package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "payment_types", primaryKeys = ["db_guid","payment_type"])
data class PaymentType(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "payment_type") val paymentType: String = "",
    @ColumnInfo(name = "is_fiscal") val isFiscal: Int = 0,
    @ColumnInfo(name = "is_default") val isDefault: Int = 0,
    val description: String = "",
    val timestamp: Long = 0
)
