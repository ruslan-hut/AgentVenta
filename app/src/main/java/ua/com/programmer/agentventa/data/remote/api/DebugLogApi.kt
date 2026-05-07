package ua.com.programmer.agentventa.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import ua.com.programmer.agentventa.data.remote.dto.DebugLogUploadDto

interface DebugLogApi {

    @POST("api/v1/device/logs")
    suspend fun uploadLogs(
        @Header("Authorization") auth: String,
        @Body body: DebugLogUploadDto
    ): Response<Unit>
}
