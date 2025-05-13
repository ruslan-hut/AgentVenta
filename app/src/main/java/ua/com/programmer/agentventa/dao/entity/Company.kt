package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "companies", primaryKeys = ["db_guid","guid"])
data class Company(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "guid") val guid: String = "",
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "is_default") val isDefault: Int = 0,
    val timestamp: Long = 0,
){
    companion object Builder {
        fun build(data: XMap): Company {
            return Company(
                databaseId = data.getDatabaseId(),
                guid = data.getString("guid"),
                description = data.getString("description"),
                isDefault = data.getInt("is_default"),
                timestamp = data.getTimestamp(),
            )
        }
    }
}

fun Company.isValid(): Boolean {
    return databaseId.isNotEmpty() && guid.isNotEmpty()
}