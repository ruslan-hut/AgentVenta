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
    @ColumnInfo(name = "relay_server") val relayServer: String = "",
    @ColumnInfo(name = "use_websocket") val useWebSocket: Boolean = true // Default to WebSocket (relay mode)
){
    companion object Builder {
        fun buildDemo(): UserAccount {
            return UserAccount(
                guid = UUID.randomUUID().toString(),
                description = "Demo",
                dataFormat = Constants.SYNC_FORMAT_HTTP,
                dbServer = "hoot.com.ua",
                dbName = "simple",
                dbUser = "Agent",
                dbPassword = "112233",
                useWebSocket = false,
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

// Keeps the legacy use_websocket flag consistent with data_format, which is the
// single source of truth for transport since the WebSocket layer was removed:
//   HTTP_service -> direct 1C (manual), use_websocket=false
//   anything else -> relay REST (auto),  use_websocket=true
// The column is retained only as the edit screen's auto/manual switch state.
fun UserAccount.sanitizeConnectionSettings(): UserAccount {
    val auto = dataFormat != Constants.SYNC_FORMAT_HTTP
    return if (useWebSocket != auto) copy(useWebSocket = auto) else this
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
    return dbServer == "hoot.com.ua" && dbName == "simple" && (dbUser == "Агент" || dbUser == "Agent") && dbPassword == "112233"
}

// Sync transport for an account. The data_format column is the discriminator:
//   HTTP_service -> LEGACY_HTTP (direct 1C, self-hosted, and the demo account)
//   anything else -> RELAY_REST (REST against the sphynx relay)
// The WebSocket transport was removed; legacy WebSocket_relay / empty values are
// normalized to REST_relay by MIGRATION_28_29, and the catch-all here routes any
// non-HTTP account to REST regardless so an un-migrated row still syncs.
enum class SyncTransport { RELAY_REST, LEGACY_HTTP }

fun UserAccount.syncTransport(): SyncTransport =
    if (dataFormat == Constants.SYNC_FORMAT_HTTP) SyncTransport.LEGACY_HTTP
    else SyncTransport.RELAY_REST

// True when this account exchanges data over the relay REST API (/api/v1/device).
// Everything that is not direct-1C HTTP is relay REST. Approval/license is gated
// via GET /api/v1/device/status.
fun UserAccount.isRelayRest(): Boolean = dataFormat != Constants.SYNC_FORMAT_HTTP