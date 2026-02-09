package ua.com.programmer.agentventa.infrastructure.config

import ua.com.programmer.agentventa.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides access to API keys and backend configuration from BuildConfig (local.properties)
 *
 * Configuration is read from local.properties file and exposed via BuildConfig.
 * This provider centralizes access to:
 * - WebSocket API key
 * - Backend relay server host
 *
 * WebSocket API Key Usage:
 * - Shared across all Android app instances
 * - Used for authenticating with relay server
 * - Token format: Bearer <API_KEY>:<DEVICE_UUID>
 * - Device UUID (UserAccount.guid) identifies individual device/account
 *
 * Backend Host:
 * - Predefined in local.properties (KEY_HOST)
 * - Same backend for all devices (app is dedicated to specific backend)
 * - Used for WebSocket relay connection
 */
@Singleton
class ApiKeyProvider @Inject constructor() {

    /**
     * WebSocket relay server API key
     * Read from local.properties: WEBSOCKET_API_KEY
     *
     * This is a shared authentication key that validates the request comes from
     * the legitimate Android app. Device identification is handled separately
     * via device UUID (UserAccount.guid).
     */
    val webSocketApiKey: String
        get() = BuildConfig.WEBSOCKET_API_KEY

    /**
     * Backend relay server host
     * Read from local.properties: KEY_HOST
     *
     * This is the predefined backend server address for WebSocket connections.
     * Since the app is dedicated to a specific backend, this is configured
     * at build time rather than being user-configurable.
     *
     * Example: "lic.nomadus.net"
     *
     * Note: The secrets-gradle-plugin automatically exposes KEY_HOST from local.properties
     */
    val backendHost: String
        get() = BuildConfig.KEY_HOST

    /**
     * Check if WebSocket API key is configured
     * @return true if API key exists and is not empty
     */
    fun hasWebSocketApiKey(): Boolean {
        return webSocketApiKey.isNotBlank()
    }

    /**
     * Check if backend host is configured
     * @return true if backend host exists and is not empty
     */
    fun hasBackendHost(): Boolean {
        return backendHost.isNotBlank()
    }

    /**
     * Get masked API key for logging (shows only last 4 characters)
     * Example: "****9MG"
     */
    fun getMaskedWebSocketApiKey(): String {
        return if (webSocketApiKey.length > 4) {
            "****${webSocketApiKey.takeLast(4)}"
        } else {
            "****"
        }
    }

    /**
     * Get full WebSocket URL with protocol
     * @return WebSocket URL with wss:// protocol
     * Example: "wss://lic.nomadus.net"
     */
    fun getWebSocketBaseUrl(): String {
        if (backendHost.isEmpty()) return ""

        return when {
            backendHost.startsWith("ws://") || backendHost.startsWith("wss://") -> backendHost
            else -> "wss://$backendHost"
        }
    }
}
