package ua.com.programmer.agentventa.http

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

/**
 * OkHttp Authenticator that handles 401 unauthorized responses by refreshing the token.
 * Works with TokenManager to get new authentication tokens.
 *
 * Note: This class uses a synchronous refresh callback because OkHttp's Authenticator
 * interface requires synchronous operation. The actual token refresh is handled
 * by TokenManager in a controlled, safe manner.
 */
class TokenRefresh @Inject constructor(): Authenticator {

    private var refreshTokenCallback: (() -> String)? = null

    fun setRefreshToken(refresh: () -> String) {
        refreshTokenCallback = refresh
    }

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Only handle 401 Unauthorized responses
        if (response.code != 401) {
            return null
        }

        val oldRequest = response.request
        val oldHttpUrl = oldRequest.url

        val pathSegments = oldHttpUrl.pathSegments
        if (pathSegments.size < 2) return null

        // Don't retry if the failed request was the token refresh itself
        if (pathSegments[pathSegments.size - 2] == "check") return null

        // Refresh the token using the callback
        val newToken = try {
            val callback = refreshTokenCallback ?: return null
            callback()
        } catch (e: Exception) {
            // Token refresh failed, don't retry
            return null
        }

        if (newToken.isBlank()) {
            // No valid token received, don't retry
            return null
        }

        // Create new request with updated token in URL
        val tokenIndex = pathSegments.size - 1
        val newHttpUrl = oldHttpUrl.newBuilder()
            .setPathSegment(tokenIndex, newToken)
            .build()

        return oldRequest.newBuilder()
            .url(newHttpUrl)
            .build()
    }
}