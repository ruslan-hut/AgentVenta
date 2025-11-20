package ua.com.programmer.agentventa.http

import ua.com.programmer.agentventa.dao.entity.UserAccount

/**
 * Manages authentication tokens for API requests.
 * Extracted from NetworkRepositoryImpl to follow Single Responsibility Principle
 * and eliminate dangerous runBlocking calls.
 */
interface TokenManager {

    /**
     * Result of token refresh operation.
     */
    sealed class TokenResult {
        data class Success(val token: String, val canRead: Boolean) : TokenResult()
        data class Error(val message: String, val exception: Exception? = null) : TokenResult()
    }

    /**
     * Configure the token manager with current account.
     * @param account User account with credentials
     */
    fun configure(account: UserAccount?)

    /**
     * Get current token synchronously.
     * Used by interceptors that need immediate token access.
     * @return Current token or empty string
     */
    fun getCurrentToken(): String

    /**
     * Refresh the authentication token.
     * Uses proper coroutines, no blocking calls.
     *
     * @param tag Identifier for logging purposes
     * @return TokenResult with new token or error
     */
    suspend fun refreshToken(tag: String = "default"): TokenResult

    /**
     * Refresh token synchronously for OkHttp Authenticator.
     * This is needed because OkHttp's Authenticator.authenticate() is synchronous.
     * Uses a separate coroutine scope to avoid blocking the calling thread.
     *
     * @param tag Identifier for logging purposes
     * @return New token or empty string on error
     */
    fun refreshTokenSync(tag: String = "authenticator"): String

    /**
     * Clear token on connection error.
     * @return true if cleared successfully
     */
    suspend fun clearToken(): Boolean

    /**
     * Reset token refresh counter.
     * Should be called at the start of new operations.
     */
    fun resetCounter()

    /**
     * Check if max token refresh attempts reached.
     * @return true if limit reached
     */
    fun isRefreshLimitReached(): Boolean
}
