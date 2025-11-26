package ua.com.programmer.agentventa.data.websocket

import com.google.gson.annotations.SerializedName

/**
 * Base WebSocket message structure for communication with relay server.
 * All messages follow this format for consistent parsing.
 */
data class WebSocketMessage(
    @SerializedName("type")
    val type: String,

    @SerializedName("data")
    val data: String? = null,

    @SerializedName("data_type")
    val dataType: String? = null,

    @SerializedName("message_id")
    val messageId: String? = null,

    @SerializedName("timestamp")
    val timestamp: Long? = null,

    @SerializedName("error")
    val error: String? = null
)

/**
 * Outgoing data message from device to accounting system.
 * Used to send orders, cash receipts, locations, etc.
 */
data class OutgoingDataMessage(
    @SerializedName("data_type")
    val dataType: String,

    @SerializedName("data")
    val data: String,

    @SerializedName("message_id")
    val messageId: String = generateMessageId()
)

/**
 * Incoming data message from accounting system to device.
 * Contains catalog updates, configuration changes, etc.
 */
data class IncomingDataMessage(
    @SerializedName("message_id")
    val messageId: String,

    @SerializedName("data_type")
    val dataType: String,

    @SerializedName("data")
    val data: String,

    @SerializedName("timestamp")
    val timestamp: Long
)

/**
 * Acknowledgment message for received data.
 */
data class AckMessage(
    @SerializedName("message_id")
    val messageId: String
)

/**
 * Error message from server.
 */
data class ErrorMessage(
    @SerializedName("error")
    val error: String,

    @SerializedName("message_id")
    val messageId: String? = null
)

/**
 * Generates unique message ID for outgoing messages.
 */
private fun generateMessageId(): String {
    return "${System.currentTimeMillis()}-${(0..9999).random()}"
}
