package ua.com.programmer.agentventa.dao.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.Constants

@Entity(tableName = "user_accounts", primaryKeys = ["guid"])
data class UserAccount(
    val guid: String,
    @ColumnInfo(name = "is_current") val isCurrent: Int = 0,
    @ColumnInfo(name = "extended_id") val extendedId: Int = 0,
    val description: String = "",
    val license: String = "",
    @ColumnInfo(name = "data_format") val dataFormat: String = "",
    @ColumnInfo(name = "db_server") val dbServer: String = "",
    @ColumnInfo(name = "db_name") val dbName: String = "",
    @ColumnInfo(name = "db_user") val dbUser: String = "",
    @ColumnInfo(name = "db_password") val dbPassword: String = "",
    val token: String = "",
    val options: String = ""
)

fun UserAccount.equalTo(account: UserAccount): Boolean {
    return this.guid == account.guid &&
            this.isCurrent == account.isCurrent &&
            this.description == account.description &&
            this.license == account.license &&
            this.dataFormat == account.dataFormat &&
            this.dbServer == account.dbServer &&
            this.dbName == account.dbName &&
            this.dbUser == account.dbUser &&
            this.dbPassword == account.dbPassword
}

fun UserAccount.isValidForHttpConnection(): Boolean {
    return dataFormat == Constants.SYNC_FORMAT_HTTP &&
            this.dbServer.isNotEmpty()
}

fun UserAccount.getBaseUrl(): String {
    var url = ""
    if (dataFormat == Constants.SYNC_FORMAT_HTTP) {
        if (dbServer.isNotBlank()) {
            url = if (dbServer.contains("http://") || dbServer.contains("https://")) {
                dbServer
            }else{
                "http://$dbServer"
            }
            if (!url.endsWith("/")) url = "$url/"
            if (dbName.isNotBlank()) {
                url = "$url$dbName/hs/dex/"
            }
        }
    }
    return url
}

fun UserAccount.connectionSettingsChanged(account: UserAccount): Boolean {
    return this.guid != account.guid ||
            this.dbServer != account.dbServer ||
            this.dbName != account.dbName ||
            this.dbUser != account.dbUser ||
            this.dbPassword != account.dbPassword
}

// Returns truncated guid for log or UI
fun UserAccount.getGuid(): String {
    return if (guid.length > 7) guid.subSequence(0,8).toString() else ""
}

// Returns truncated license number for log or UI
fun UserAccount.getLicense(): String {
    return if (license.length > 5) license.subSequence(0,6).toString() else ""
}