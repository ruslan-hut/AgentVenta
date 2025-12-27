package ua.com.programmer.agentventa.infrastructure.license

import retrofit2.http.POST
import ua.com.programmer.agentventa.data.remote.dto.UserAccountDto
import ua.com.programmer.agentventa.data.local.entity.LogEvent

/**
 * @deprecated This API is deprecated. License management is now handled via WebSocket.
 * @see ua.com.programmer.agentventa.domain.repository.WebSocketRepository
 */
@Deprecated("Use WebSocket for license management")
interface LicenseApi {
    @POST("api/v1/key")
    suspend fun keyInfo(userAccount: UserAccountDto): Map<String, Any>?
    @POST("api/v1/log")
    suspend fun log(event: LogEvent): Map<String, Any>?
}