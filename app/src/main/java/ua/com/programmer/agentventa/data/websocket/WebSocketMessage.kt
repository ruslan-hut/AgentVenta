package ua.com.programmer.agentventa.data.websocket

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Base WebSocket message structure for communication with relay server.
 * All messages follow the API specification format with payload envelope.
 *
 * API Format:
 * {
 *   "type": "data|ack|ping|pong|error",
 *   "message_id": "msg-12345",
 *   "timestamp": "2025-01-15T10:30:00Z",
 *   "status": "pending|approved|denied",  // Server-to-device only
 *   "payload": {
 *     // Message-specific payload
 *   }
 * }
 */
data class WebSocketMessage(
    @SerializedName("type")
    val type: String,

    @SerializedName("message_id")
    val messageId: String? = null,

    @SerializedName("timestamp")
    val timestamp: String? = null,  // ISO 8601 format

    @SerializedName("status")
    val status: String? = null,  // Device status: pending, approved, denied

    @SerializedName("payload")
    val payload: JsonObject? = null
)

/**
 * Payload for data messages.
 * Contains data_type and actual data object.
 */
data class DataPayload(
    @SerializedName("data_type")
    val dataType: String,

    @SerializedName("data")
    val data: JsonObject  // Actual JSON object, not string
)

/**
 * Payload for ACK messages.
 */
data class AckPayload(
    @SerializedName("status")
    val status: String = "received"
)

/**
 * Payload for error messages.
 */
data class ErrorPayload(
    @SerializedName("error")
    val error: String
)

/**
 * Incoming data message from accounting system to device.
 * Contains catalog updates, configuration changes, etc.
 */
data class IncomingDataMessage(
    val messageId: String,
    val dataType: String,
    val data: JsonObject,  // Raw JSON object for flexible parsing
    val timestamp: String,
    val status: String?  // Device status from server
)

/**
 * Acknowledgment message for received data.
 */
data class AckMessage(
    val messageId: String,
    val status: String?  // Device status from server
)

/**
 * Error message from server.
 */
data class ErrorMessage(
    val error: String,
    val messageId: String?,
    val status: String?  // Device status - check for "pending"
)

/**
 * Generates unique message ID for outgoing messages.
 */
private fun generateMessageId(): String {
    return "${System.currentTimeMillis()}-${(0..9999).random()}"
}
