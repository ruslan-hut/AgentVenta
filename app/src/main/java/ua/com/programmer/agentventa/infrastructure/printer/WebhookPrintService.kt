package ua.com.programmer.agentventa.infrastructure.printer

import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ua.com.programmer.agentventa.domain.usecase.order.OrderPrintData
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Service for sending order print data to a webhook endpoint.
 *
 * Supports:
 * - GET and POST methods
 * - Basic authentication
 * - JSON payload with order data
 */
@Singleton
class WebhookPrintService @Inject constructor(
    private val preferences: SharedPreferences,
    private val gson: Gson
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val PREF_WEBHOOK_ENABLED = "webhook_print_enabled"
        private const val PREF_WEBHOOK_URL = "webhook_print_url"
        private const val PREF_WEBHOOK_METHOD = "webhook_print_method"
        private const val PREF_WEBHOOK_USE_AUTH = "webhook_print_use_auth"
        private const val PREF_WEBHOOK_USERNAME = "webhook_print_username"
        private const val PREF_WEBHOOK_PASSWORD = "webhook_print_password"
    }

    /**
     * Check if webhook printing is enabled.
     */
    fun isEnabled(): Boolean {
        return preferences.getBoolean(PREF_WEBHOOK_ENABLED, false)
    }

    /**
     * Check if webhook is properly configured.
     */
    fun isConfigured(): Boolean {
        val url = preferences.getString(PREF_WEBHOOK_URL, "") ?: ""
        return url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))
    }

    /**
     * Get current webhook URL.
     */
    fun getUrl(): String {
        return preferences.getString(PREF_WEBHOOK_URL, "") ?: ""
    }

    /**
     * Get current HTTP method.
     */
    fun getMethod(): String {
        return preferences.getString(PREF_WEBHOOK_METHOD, "POST") ?: "POST"
    }

    /**
     * Check if authentication is enabled.
     */
    fun isAuthEnabled(): Boolean {
        return preferences.getBoolean(PREF_WEBHOOK_USE_AUTH, false)
    }

    /**
     * Get username for basic auth.
     */
    fun getUsername(): String {
        return preferences.getString(PREF_WEBHOOK_USERNAME, "") ?: ""
    }

    /**
     * Get password for basic auth.
     */
    fun getPassword(): String {
        return preferences.getString(PREF_WEBHOOK_PASSWORD, "") ?: ""
    }

    // Settings save methods

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(PREF_WEBHOOK_ENABLED, enabled) }
    }

    fun setUrl(url: String) {
        preferences.edit { putString(PREF_WEBHOOK_URL, url) }
    }

    fun setMethod(method: String) {
        preferences.edit { putString(PREF_WEBHOOK_METHOD, method) }
    }

    fun setAuthEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(PREF_WEBHOOK_USE_AUTH, enabled) }
    }

    fun setUsername(username: String) {
        preferences.edit { putString(PREF_WEBHOOK_USERNAME, username) }
    }

    fun setPassword(password: String) {
        preferences.edit { putString(PREF_WEBHOOK_PASSWORD, password) }
    }

    /**
     * Send order print data to webhook.
     *
     * @param printData Order data to send
     * @return Result with success message or error
     */
    suspend fun sendPrintData(printData: OrderPrintData): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = getUrl()
                if (url.isBlank()) {
                    return@withContext Result.failure(Exception("Webhook URL is not configured"))
                }

                val jsonBody = gson.toJson(printData)
                val method = getMethod()

                val requestBuilder = Request.Builder()

                when (method.uppercase()) {
                    "POST" -> {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val body = jsonBody.toRequestBody(mediaType)
                        requestBuilder.url(url).post(body)
                    }
                    "GET" -> {
                        // For GET, append order guid as query parameter
                        val getUrl = if (url.contains("?")) {
                            "$url&orderGuid=${printData.orderGuid}"
                        } else {
                            "$url?orderGuid=${printData.orderGuid}"
                        }
                        requestBuilder.url(getUrl).get()
                    }
                    else -> {
                        return@withContext Result.failure(Exception("Unsupported HTTP method: $method"))
                    }
                }

                // Add authentication if enabled
                if (isAuthEnabled()) {
                    val username = getUsername()
                    val password = getPassword()
                    if (username.isNotBlank()) {
                        val credentials = Credentials.basic(username, password)
                        requestBuilder.header("Authorization", credentials)
                    }
                }

                // Add content type header
                requestBuilder.header("Content-Type", "application/json")

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    Result.success(responseBody)
                } else {
                    val errorBody = response.body.string()
                    Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Test webhook connection with empty payload.
     */
    suspend fun testConnection(): Result<String> {
        val testData = OrderPrintData(
            orderGuid = "test",
            orderNumber = 0,
            date = "",
            company = "",
            store = "",
            notes = "Test connection",
            isReturn = false,
            total = 0.0,
            discount = 0.0,
            items = emptyList()
        )
        return sendPrintData(testData)
    }
}
