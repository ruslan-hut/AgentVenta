package ua.com.programmer.agentventa.infrastructure.license

import retrofit2.http.POST
import ua.com.programmer.agentventa.data.remote.dto.UserAccountDto
import ua.com.programmer.agentventa.data.local.entity.LogEvent

interface LicenseApi {
    @POST("api/v1/key")
    suspend fun keyInfo(userAccount: UserAccountDto): Map<String, Any>?
    @POST("api/v1/log")
    suspend fun log(event: LogEvent): Map<String, Any>?
}