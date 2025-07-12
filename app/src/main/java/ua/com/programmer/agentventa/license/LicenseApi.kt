package ua.com.programmer.agentventa.license

import retrofit2.http.POST
import ua.com.programmer.agentventa.dao.cloud.CUserAccount
import ua.com.programmer.agentventa.dao.entity.LogEvent

interface LicenseApi {
    @POST("api/v1/key")
    suspend fun keyInfo(userAccount: CUserAccount): Map<String, Any>?
    @POST("api/v1/log")
    suspend fun log(event: LogEvent): Map<String, Any>?
}