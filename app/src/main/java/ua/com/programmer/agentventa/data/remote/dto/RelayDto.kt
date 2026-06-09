package ua.com.programmer.agentventa.data.remote.dto

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * DTOs for the sphynx relay device-REST API. Field names match the Go
 * response.Response envelope and the device handler DTOs verbatim.
 */

data class RelayEnvelope<T>(
    @SerializedName("data") val data: T? = null,
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("status_message") val statusMessage: String? = null,
)

// GET /api/v1/device/status
data class RelayStatusData(
    @SerializedName("device_uuid") val deviceUuid: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("can_transfer") val canTransfer: Boolean = false,
    @SerializedName("license_number") val licenseNumber: String? = null,
    @SerializedName("license_error") val licenseError: String? = null,
    @SerializedName("license_error_reason") val licenseErrorReason: String? = null,
    @SerializedName("app_params") val appParams: JsonObject? = null,
)

// GET /api/v1/device/pull
data class RelayPullData(
    @SerializedName("count") val count: Int = 0,
    @SerializedName("messages") val messages: List<RelayPullMessage> = emptyList(),
)

data class RelayPullMessage(
    @SerializedName("message_id") val messageId: String = "",
    @SerializedName("batch_timestamp") val batchTimestamp: Long = 0,
    // Each item is a raw catalog object (value_id + fields) or the
    // batch_complete sentinel ({type, timestamp}).
    @SerializedName("items") val items: List<JsonObject> = emptyList(),
)

// POST /api/v1/device/ack
data class RelayAckRequest(
    @SerializedName("message_ids") val messageIds: List<String>,
)

data class RelayAckData(
    @SerializedName("acked") val acked: Int = 0,
)

// POST /api/v1/device/upload
data class RelayUploadRequest(
    @SerializedName("documents") val documents: List<RelayUploadDocument>,
)

data class RelayUploadDocument(
    @SerializedName("type") val type: String,
    @SerializedName("document_guid") val documentGuid: String? = null,
    @SerializedName("data") val data: Map<String, Any>,
)

data class RelayUploadData(
    @SerializedName("accepted") val accepted: Int = 0,
    @SerializedName("results") val results: List<RelayUploadResult> = emptyList(),
)

data class RelayUploadResult(
    @SerializedName("document_guid") val documentGuid: String? = null,
    @SerializedName("type") val type: String = "",
    @SerializedName("queued") val queued: Boolean = false,
    @SerializedName("error") val error: String? = null,
)
