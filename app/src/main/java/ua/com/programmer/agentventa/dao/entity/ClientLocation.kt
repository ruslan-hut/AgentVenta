package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "client_locations", primaryKeys = ["db_guid","client_guid"])
data class ClientLocation(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "client_guid") val clientGuid: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @ColumnInfo(name = "is_modified") val isModified: Int = 0,
    val address: String = ""
){
    companion object Builder {
        fun build(data: XMap): ClientLocation {
            return ClientLocation(
                databaseId = data.getDatabaseId(),
                clientGuid = data.getString("client_guid"),
                latitude = data.getDouble("latitude"),
                longitude = data.getDouble("longitude")
            )
        }
    }
}

fun ClientLocation.isValid(): Boolean {
    return clientGuid.isNotEmpty() && databaseId.isNotEmpty()
}

fun ClientLocation.hasLocation(): Boolean {
    return latitude != 0.0 && longitude != 0.0
}