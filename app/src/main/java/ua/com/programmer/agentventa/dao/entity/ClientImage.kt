package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(tableName = "client_images", primaryKeys = ["db_guid","client_guid","guid"])
data class ClientImage(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    @ColumnInfo(name = "client_guid") val clientGuid: String = "",
    val guid: String,
    val url: String = "",
    val description: String = "",
    val timestamp: Long = 0,
    @ColumnInfo(name = "is_local") val isLocal: Int = 0,
    @ColumnInfo(name = "is_sent") val isSent: Int = 0,
    @ColumnInfo(name = "is_default") val isDefault: Int = 0,
){
    companion object Builder {
        fun build(data: XMap): ClientImage {
            return ClientImage(
                databaseId = data.getDatabaseId(),
                clientGuid = data.getString("client_guid"),
                guid = data.getString("image_guid"),
                url = data.getString("url"),
                isLocal = 0,
                isSent = 0,
                isDefault = data.getInt("is_default")
            )
        }
    }
}

fun ClientImage.fileName(): String {
    return "${this.clientGuid}#${this.guid}.jpg"
}

// convert data to map
fun ClientImage.toMap(): Map<String,Any> {
    return mapOf(
        "db_guid" to databaseId,
        "client_guid" to clientGuid,
        "image_guid" to guid,
        "url" to url,
        "description" to description,
        "is_default" to isDefault,
        "timestamp" to timestamp,
        "type" to "client_image"
    )
}