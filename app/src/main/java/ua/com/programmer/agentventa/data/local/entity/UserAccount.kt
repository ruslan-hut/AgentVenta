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
    @ColumnInfo(name = "sync_email") val syncEmail: String = "",
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
//
// Backend host is predefined in BuildConfig (from local.properties KEY_HOST)
// The relayServer field is kept for backward compatibility but is not required
//
// IMPORTANT: WebSocket ALWAYS connects for license management and device status.
// The useWebSocket flag only controls DATA EXCHANGE method (HTTP vs WebSocket).
// Device must be approved via WebSocket before any data sync (HTTP or WebSocket).
fun UserAccount.isValidForWebSocketConnection(): Boolean {
    return guid.isNotEmpty()
}

// Constructs WebSocket URL for connection using predefined backend host
// Uses UserAccount.guid as device UUID for server identification
//
// Backend Host:
// - Predefined in local.properties (KEY_HOST)
// - Exposed via BuildConfig.BACKEND_HOST
// - Same backend for all devices (app is dedicated to specific backend)
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
//
// IMPORTANT: This function now requires ApiKeyProvider to get the backend host.
// Use getWebSocketUrl(apiKeyProvider) instead.
// For backward compatibility, falls back to relayServer field if available.
fun UserAccount.getWebSocketUrl(): String {
    // Use relayServer for backward compatibility if set
    // In production, backend host comes from BuildConfig
    val host = relayServer.ifEmpty { return "" }

    var url = host
    // Ensure proper protocol
    if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
        url = "wss://$url"
    }
    // Remove trailing slash if present
    if (url.endsWith("/")) {
        url = url.dropLast(1)
    }

    // Construct WebSocket endpoint
    // Authentication is handled via Authorization header (see WebSocketRepositoryImpl)
    // Backend will identify the 1C base by looking up the license linked to this UUID
    return "$url/ws/device"
}

// Constructs WebSocket URL using predefined backend host from ApiKeyProvider
// This is the preferred method for building WebSocket URLs.
//
// @param backendHost The backend host from ApiKeyProvider (e.g., "lic.nomadus.net")
// @return Full WebSocket URL (e.g., "wss://lic.nomadus.net/ws/device")
fun UserAccount.getWebSocketUrl(backendHost: String): String {
    if (backendHost.isEmpty()) return ""

    var url = backendHost
    // Ensure proper protocol
    if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
        url = "wss://$url"
    }
    // Remove trailing slash if present
    if (url.endsWith("/")) {
        url = url.dropLast(1)
    }

    // Construct WebSocket endpoint
    return "$url/ws/device"
}

// Determines if this account should use WebSocket instead of HTTP
// This is now the preferred way to check connection mode throughout the app
fun UserAccount.shouldUseWebSocket(): Boolean {
    return useWebSocket
}