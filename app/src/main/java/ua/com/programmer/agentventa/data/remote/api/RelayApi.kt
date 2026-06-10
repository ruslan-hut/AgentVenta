package ua.com.programmer.agentventa.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import ua.com.programmer.agentventa.data.remote.dto.RelayAckData
import ua.com.programmer.agentventa.data.remote.dto.RelayAckRequest
import ua.com.programmer.agentventa.data.remote.dto.RelayEnvelope
import ua.com.programmer.agentventa.data.remote.dto.RelayPullData
import ua.com.programmer.agentventa.data.remote.dto.RelayStatusData
import ua.com.programmer.agentventa.data.remote.dto.RelayUploadData
import ua.com.programmer.agentventa.data.remote.dto.RelayUploadRequest

/**
 * Device-authenticated REST client for the sphynx relay (/api/v1/device).
 * Mirrors the WebSocket transport: status replaces the WS status/pong channel,
 * pull replaces catalog push, ack replaces the WS ack frame, upload replaces
 * the upload_* frames.
 *
 * Auth is the same scheme as the WS connect, passed per-call because the device
 * UUID is the current account's guid: Authorization: Bearer <API_KEY>:<DEVICE_UUID>.
 */
interface RelayApi {

    @GET("api/v1/device/status")
    suspend fun status(
        @Header("Authorization") auth: String,
        // base64url JSON: lets the relay capture description/license so the
        // admin can identify an auto-registered device. Optional.
        @Query("app_parameters") appParameters: String? = null,
    ): RelayEnvelope<RelayStatusData>

    @GET("api/v1/device/pull")
    suspend fun pull(
        @Header("Authorization") auth: String,
        @Query("limit") limit: Int,
    ): RelayEnvelope<RelayPullData>

    @POST("api/v1/device/ack")
    suspend fun ack(
        @Header("Authorization") auth: String,
        @Body body: RelayAckRequest,
    ): RelayEnvelope<RelayAckData>

    @POST("api/v1/device/upload")
    suspend fun upload(
        @Header("Authorization") auth: String,
        @Body body: RelayUploadRequest,
    ): RelayEnvelope<RelayUploadData>
}
