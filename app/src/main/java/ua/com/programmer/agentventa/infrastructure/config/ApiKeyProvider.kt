package ua.com.programmer.agentventa.infrastructure.config

import ua.com.programmer.agentventa.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides access to API keys stored in BuildConfig (from local.properties)
 *
 * API keys are read from local.properties file and exposed via BuildConfig.
 * This provider centralizes API key access and validation.
 *
 * WebSocket API Key Usage:
 * - Shared across all Android app instances
 * - Used for authenticating with relay server
 * - Token format: Bearer <API_KEY>:<DEVICE_UUID>
 * - Device UUID (UserAccount.guid) identifies individual device/account
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
        get() = BuildConfig.WEBSOCKET_API_KEY ?: ""

    /**
     * Check if WebSocket API key is configured
     * @return true if API key exists and is not empty
     */
    fun hasWebSocketApiKey(): Boolean {
        return webSocketApiKey.isNotBlank()
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
}
