package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.XMap

@Entity(
    tableName = "clients",
    primaryKeys = ["guid","db_guid"]
)
data class Client(
    @ColumnInfo(name = "db_guid") val databaseId: String = "",
    val guid: String,
    val timestamp: Long = 0,
    val code1: String = "",
    val code2: String = "",
    val description: String = "",
    @ColumnInfo(name = "description_lc") val descriptionLc: String = "",
    val notes: String = "",
    val phone: String = "",
    val address: String = "",
    val discount: Double = 0.0,
    val bonus: Double = 0.0,
    @ColumnInfo(name = "price_type") val priceType: String = "",
    @ColumnInfo(name = "is_banned") val isBanned: Int = 0,
    @ColumnInfo(name = "ban_message") val banMessage: String = "",
    @ColumnInfo(name = "group_guid") val groupGuid: String = "",
    @ColumnInfo(name = "is_active") val isActive: Int = 0,
    @ColumnInfo(name = "is_group") val isGroup: Int = 0,
){
    companion object Builder {
        fun build(data: XMap): Client {
            return Client(
                databaseId = data.getDatabaseId(),
                timestamp = data.getTimestamp(),
                guid = data.getString("guid"),
                description = data.getString("description"),
                descriptionLc = data.getString("description").lowercase(),
                code1 = data.getString("code1"),
                code2 = data.getString("code2"),
                notes = data.getString("notes"),
                phone = data.getString("phone"),
                address = data.getString("address"),
                discount = data.getDouble("discount"),
                bonus = data.getDouble("bonus"),
                priceType = data.getString("price_type"),
                isBanned = data.getInt("is_banned"),
                banMessage = data.getString("ban_message"),
                groupGuid = data.getString("group_guid"),
                isActive = data.getInt("is_active"),
                isGroup = data.getInt("is_group")
            )
        }
    }
}

data class LClient(
    val guid: String = "",
    val timestamp: Long = 0,
    val code: String = "",
    val description: String = "",
    val notes: String = "",
    val phone: String = "",
    val address: String = "",
    val discount: Double = 0.0,
    val bonus: Double = 0.0,
    val debt: Double = 0.0,
    val priceType: String = "",
    val isBanned: Boolean = false,
    val banMessage: String = "",
    val groupGuid: String = "",
    val groupName: String? = "",
    val isActive: Boolean = false,
    val isGroup: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

fun Client.isValid(): Boolean {
    return this.guid.isNotEmpty() && this.description.isNotEmpty() && this.databaseId.isNotEmpty()
}

fun LClient.hasLocation(): Boolean {
    return this.latitude != 0.0 || this.longitude != 0.0
}

fun Client.toUi(): LClient {
    return LClient(
        guid = this.guid,
        timestamp = this.timestamp,
        code = this.code1,
        description = this.description,
        notes = this.notes,
        phone = this.phone,
        address = this.address,
        discount = this.discount,
        bonus = this.bonus,
        debt = 0.0,
        priceType = this.priceType,
        isBanned = this.isBanned > 0,
        banMessage = this.banMessage,
        groupGuid = this.groupGuid,
        groupName = null,
        isActive = this.isActive > 0,
        isGroup = this.isGroup > 0,
        latitude = 0.0,
        longitude = 0.0
    )
}