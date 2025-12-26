package ua.com.programmer.agentventa.data.websocket

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import ua.com.programmer.agentventa.utility.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Factory for creating and parsing WebSocket messages.
 * Handles serialization/deserialization using Gson.
 *
 * Message format follows API specification:
 * - ISO 8601 timestamps
 * - Payload envelope for all data
 * - Status field in server messages
 */
object WebSocketMessageFactory {

    private val gson = Gson()

    // ISO 8601 timestamp formatter (UTC) - compatible with API 23+
    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Creates a generic WebSocket message.
     * Used for non-data messages like sync_settings.
     *
     * API Format:
     * {
     *   "type": "sync_settings",
     *   "message_id": "msg-12345",
     *   "timestamp": "2025-01-15T10:30:00Z",
     *   "payload": { /* JSON object */ }
     * }
     *
     * @param type Message type (sync_settings, custom_command, etc.)
     * @param payload JSON string containing the payload
     * @param messageId Unique message identifier
     */
    fun createMessage(
        type: String,
        payload: String,
        messageId: String = generateMessageId()
    ): String {
        // Parse payload string to JsonObject
        val payloadObject = try {
            JsonParser.parseString(payload).asJsonObject
        } catch (_: Exception) {
            JsonObject()
        }

        val message = WebSocketMessage(
            type = type,
            messageId = messageId,
            timestamp = getCurrentTimestamp(),
            payload = payloadObject
        )

        return gson.toJson(message)
    }

    /**
     * Creates a data message to send to the server.
     *
     * API Format:
     * {
     *   "type": "data",
     *   "message_id": "msg-12345",
     *   "timestamp": "2025-01-15T10:30:00Z",
     *   "payload": {
     *     "data_type": "order",
     *     "data": { /* JSON object */ }
     *   }
     * }
     *
     * @param dataType Type of data (order, cash, settings, etc.)
     * @param data JSON object or string (will be parsed to JsonObject)
     * @param messageId Unique message identifier
     */
    fun createDataMessage(
        dataType: String,
        data: String,
        messageId: String = generateMessageId()
    ): String {
        // Parse data string to JsonObject
        val dataObject = try {
            JsonParser.parseString(data).asJsonObject
        } catch (_: Exception) {
            // If parsing fails, wrap string in a JSON object
            JsonObject().apply {
                addProperty("value", data)
            }
        }

        // Create payload
        val payload = JsonObject().apply {
            addProperty("data_type", dataType)
            add("data", dataObject)
        }

        // Create message
        val message = WebSocketMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_DATA,
            messageId = messageId,
            timestamp = getCurrentTimestamp(),
            payload = payload
        )

        return gson.toJson(message)
    }

    /**
     * Creates an acknowledgment message.
     *
     * API Format:
     * {
     *   "type": "ack",
     *   "message_id": "msg-12345",
     *   "timestamp": "2025-01-15T10:30:00Z",
     *   "payload": {
     *     "status": "received"
     *   }
     * }
     */
    fun createAckMessage(messageId: String): String {
        val payload = JsonObject().apply {
            addProperty("status", "received")
        }

        val message = WebSocketMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_ACK,
            messageId = messageId,
            timestamp = getCurrentTimestamp(),
            payload = payload
        )
        return gson.toJson(message)
    }

    /**
     * Creates a ping message for keepalive.
     *
     * API Format:
     * {
     *   "type": "ping",
     *   "message_id": "ping-12345",
     *   "timestamp": "2025-01-15T10:30:00Z",
     *   "payload": {}
     * }
     */
    fun createPingMessage(): String {
        val message = WebSocketMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_PING,
            messageId = generateMessageId(),
            timestamp = getCurrentTimestamp(),
            payload = JsonObject()
        )
        return gson.toJson(message)
    }

    /**
     * Creates a pong response message.
     *
     * API Format:
     * {
     *   "type": "pong",
     *   "message_id": "pong-12345",
     *   "timestamp": "2025-01-15T10:30:00Z",
     *   "payload": {}
     * }
     */
    fun createPongMessage(): String {
        val message = WebSocketMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_PONG,
            messageId = generateMessageId(),
            timestamp = getCurrentTimestamp(),
            payload = JsonObject()
        )
        return gson.toJson(message)
    }

    /**
     * Parses incoming WebSocket message text.
     * @return Parsed message or null if parsing fails
     */
    fun parseMessage(text: String): WebSocketMessage? {
        return try {
            gson.fromJson(text, WebSocketMessage::class.java)
        } catch (e: JsonSyntaxException) {
            android.util.Log.e("WSMessageFactory", "JSON parse error: ${e.message}")
            null
        } catch (e: Exception) {
            android.util.Log.e("WSMessageFactory", "Parse error: ${e.message}")
            null
        }
    }

    /**
     * Parses incoming data message.
     *
     * Simplified unified format from server:
     * {
     *   "type": "data",
     *   "message_id": "msg-12345",
     *   "timestamp": "2025-01-15T10:30:00Z",
     *   "status": "approved",
     *   "payload": [
     *     {
     *       "value_id": "clients|goods|options|...",
     *       // ... object fields
     *     }
     *   ]
     * }
     *
     * Legacy format (still supported):
     * {
     *   "payload": {
     *     "data_type": "settings",
     *     "data": { /* JSON object */ }
     *   }
     * }
     */
    fun parseDataMessage(message: WebSocketMessage): IncomingDataMessage? {
        if (message.type != Constants.WEBSOCKET_MESSAGE_TYPE_DATA) return null
        if (message.messageId == null) return null
        if (message.payload == null) return null

        return try {
            // Check if payload is direct array (new format)
            if (message.payload.isJsonArray) {
                // New simplified format: payload is array directly
                IncomingDataMessage(
                    messageId = message.messageId,
                    dataType = Constants.WEBSOCKET_DATA_TYPE_CATALOG, // Default to catalog for array format
                    data = JsonObject().apply {
                        add("data", message.payload.asJsonArray)
                    },
                    timestamp = message.timestamp ?: getCurrentTimestamp(),
                    status = message.status
                )
            } else if (message.payload.isJsonObject) {
                val payloadObject = message.payload.asJsonObject

                // Legacy object format with data_type field
                val dataType = payloadObject.get("data_type")?.asString ?: return null
                val dataElement = payloadObject.get("data") ?: return null

                val data = when {
                    dataElement.isJsonArray -> {
                        JsonObject().apply {
                            add("data", dataElement.asJsonArray)
                        }
                    }
                    dataElement.isJsonObject -> dataElement.asJsonObject
                    else -> return null
                }

                IncomingDataMessage(
                    messageId = message.messageId,
                    dataType = dataType,
                    data = data,
                    timestamp = message.timestamp ?: getCurrentTimestamp(),
                    status = message.status
                )
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("WSMessageFactory", "Error parsing data message: ${e.message}")
            null
        }
    }

    /**
     * Parses acknowledgment message.
     *
     * Expected format from server:
     * {
     *   "type": "ack",
     *   "message_id": "msg-12345",
     *   "timestamp": "2025-01-15T10:30:00Z",
     *   "status": "approved",
     *   "payload": {
     *     "status": "received"
     *   }
     * }
     */
    fun parseAckMessage(message: WebSocketMessage): AckMessage? {
        if (message.type != Constants.WEBSOCKET_MESSAGE_TYPE_ACK) return null
        if (message.messageId == null) return null

        return AckMessage(
            messageId = message.messageId,
            status = message.status
        )
    }

    /**
     * Parses error message.
     *
     * Expected format from server:
     * {
     *   "type": "error",
     *   "message_id": "",
     *   "timestamp": "2025-01-15T10:30:00Z",
     *   "status": "pending",
     *   "payload": {
     *     "error": "Device is pending approval"
     *   }
     * }
     */
    fun parseErrorMessage(message: WebSocketMessage): ErrorMessage? {
        if (message.type != Constants.WEBSOCKET_MESSAGE_TYPE_ERROR) return null
        if (message.payload == null) return null

        return try {
            if (!message.payload.isJsonObject) return null

            val payloadObject = message.payload.asJsonObject
            val error = payloadObject.get("error")?.asString ?: return null
            val reason = payloadObject.get("reason")?.asString

            ErrorMessage(
                error = error,
                messageId = message.messageId?.takeIf { it.isNotEmpty() },
                status = message.status,
                reason = reason
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates unique message ID.
     */
    private fun generateMessageId(): String {
        return "${System.currentTimeMillis()}-${(0..9999).random()}"
    }

    /**
     * Gets current timestamp in ISO 8601 format (UTC).
     * Format: 2025-01-15T10:30:00Z
     */
    private fun getCurrentTimestamp(): String {
        return timestampFormatter.format(Date())
    }
}
