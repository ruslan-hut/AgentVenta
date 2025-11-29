package ua.com.programmer.agentventa.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import ua.com.programmer.agentventa.utility.Constants
import java.util.UUID

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
    val options: String = "",
    @ColumnInfo(name = "relay_server") val relayServer: String = ""
){
    companion object Builder {
        fun buildDemo(): UserAccount {
            return UserAccount(
                guid = UUID.randomUUID().toString(),
                description = "Demo",
                dataFormat = Constants.SYNC_FORMAT_HTTP,
                dbServer = "hoot.com.ua",
                dbName = "simple",
                dbUser = "Агент",
                dbPassword = "112233",
            )
        }
        fun buildEmpty(): UserAccount {
            return UserAccount(
                guid = "",
                description = "<?>",
                dataFormat = Constants.SYNC_FORMAT_HTTP,
            )
        }
    }
}

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

fun UserAccount.isDemo(): Boolean {
    return dbServer == "hoot.com.ua" && dbName == "simple" && dbUser == "Агент" && dbPassword == "112233"
}

// WebSocket connection validation
// NOTE: License is NOT required for connection - it's stored for display only.
// Backend identifies the 1C base by device UUID (guid), not by license number.
// The backend maintains the mapping: device_uuid -> license_number -> 1C_base
fun UserAccount.isValidForWebSocketConnection(): Boolean {
    return dataFormat == Constants.SYNC_FORMAT_WEBSOCKET &&
            relayServer.isNotEmpty() &&
            guid.isNotEmpty()
}

// Constructs WebSocket URL for connection
// Uses UserAccount.guid as device UUID for server identification
//
// Authentication Flow:
// - API key from local.properties (shared across all app instances)
// - Token format: Authorization: Bearer <API_KEY>:<DEVICE_UUID>
// - API key validates request is from legitimate Android app
// - Device UUID (guid) identifies individual device/account
//
// NOTE: License number is NOT sent in the URL or as authentication data.
// The backend links device UUIDs to license numbers (and therefore to 1C bases) server-side.
// License is only received from backend and stored locally for display/reference purposes.
fun UserAccount.getWebSocketUrl(): String {
    if (relayServer.isEmpty()) return ""

    var url = relayServer
    // Ensure proper protocol
    if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
        url = "wss://$url"
    }
    // Remove trailing slash if present
    if (url.endsWith("/")) {
        url = url.dropLast(1)
    }

    // Construct WebSocket endpoint with device UUID only
    // Authentication is handled via Authorization header (see WebSocketRepositoryImpl)
    // Backend will identify the 1C base by looking up the license linked to this UUID
    return "$url/ws/device?uuid=$guid"
}

// Determines if this account should use WebSocket instead of HTTP
fun UserAccount.shouldUseWebSocket(): Boolean {
    return dataFormat == Constants.SYNC_FORMAT_WEBSOCKET && relayServer.isNotEmpty()
}