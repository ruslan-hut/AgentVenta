package ua.com.programmer.agentventa.data.remote.interceptor

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

        // Locate the token segment by endpoint shape rather than substring
        // matching. Endpoints in HttpClientApi:
        //   get/{type}/{token}{more}        — token at index 2 (may be followed
        //                                      by extra segments from {more})
        //   post/{token}                    — token at index 1 (last)
        //   document/{type}/{guid}/{token}  — token at index 3 (last)
        // check/{id} and print/{guid} carry no token: do not retry.
        val tokenIndex = when (pathSegments[0]) {
            "get" -> 2
            "post" -> 1
            "document" -> 3
            else -> return null
        }
        if (tokenIndex >= pathSegments.size) return null

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
        val newHttpUrl = oldHttpUrl.newBuilder()
            .setPathSegment(tokenIndex, newToken)
            .build()

        return oldRequest.newBuilder()
            .url(newHttpUrl)
            .build()
    }
}