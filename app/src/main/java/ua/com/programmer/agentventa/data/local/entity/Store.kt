package ua.com.programmer.agentventa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "stores", primaryKeys = ["db_guid","guid"])
data class Store(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "guid") val guid: String = "",
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "is_default") val isDefault: Int = 0,
    val timestamp: Long = 0,
){
    companion object Builder {
        fun build(data: XMap): Store {
            return Store(
                databaseId = data.getDatabaseId(),
                guid = data.getString("guid"),
                description = data.getString("description"),
                isDefault = data.getInt("is_default"),
                timestamp = data.getTimestamp(),
            )
        }
    }
}

fun Store.isValid(): Boolean {
    return databaseId.isNotEmpty() && guid.isNotEmpty()
}