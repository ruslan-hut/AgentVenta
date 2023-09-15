package ua.com.programmer.agentventa.http

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenRefresh @Inject constructor(): Authenticator {

    private lateinit var refreshToken: () -> String

    fun setRefreshToken(refresh: () -> String) {
        refreshToken = refresh
    }

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code == 401) {
            val oldRequest = response.request
            val oldHttpUrl = oldRequest.url

            val pathSegments = oldHttpUrl.pathSegments
            if (pathSegments.size < 2) return null
            if (pathSegments[pathSegments.size - 2] == "check") return null

            // Refresh the token asynchronously
            val newToken = try {
                refreshToken()
            } catch (e: Exception) {
                return null
            }
            if (newToken.isBlank()) return null

            val tokenIndex = pathSegments.size - 1
            val newHttpUrl = oldHttpUrl.newBuilder()
                .setPathSegment(tokenIndex, newToken)
                .build()

            return oldRequest.newBuilder()
                .url(newHttpUrl)
                .build()
        }
        return null
    }

}