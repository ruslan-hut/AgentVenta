package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "price_types", primaryKeys = ["db_guid","price_type"])
data class PriceType(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "price_type") val priceType: String = "",
    val description: String = "",
    val timestamp: Long = 0
)
