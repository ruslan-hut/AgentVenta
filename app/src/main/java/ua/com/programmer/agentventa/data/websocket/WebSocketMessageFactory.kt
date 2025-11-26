package ua.com.programmer.agentventa.data.websocket

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ua.com.programmer.agentventa.utility.Constants

/**
 * Factory for creating and parsing WebSocket messages.
 * Handles serialization/deserialization using Gson.
 */
object WebSocketMessageFactory {

    private val gson = Gson()

    /**
     * Creates a data message to send to the server.
     */
    fun createDataMessage(
        dataType: String,
        data: String,
        messageId: String = generateMessageId()
    ): String {
        val message = WebSocketMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_DATA,
            dataType = dataType,
            data = data,
            messageId = messageId,
            timestamp = System.currentTimeMillis()
        )
        return gson.toJson(message)
    }

    /**
     * Creates an acknowledgment message.
     */
    fun createAckMessage(messageId: String): String {
        val message = WebSocketMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_ACK,
            messageId = messageId
        )
        return gson.toJson(message)
    }

    /**
     * Creates a ping message for keepalive.
     */
    fun createPingMessage(): String {
        val message = WebSocketMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_PING,
            timestamp = System.currentTimeMillis()
        )
        return gson.toJson(message)
    }

    /**
     * Creates a pong response message.
     */
    fun createPongMessage(): String {
        val message = WebSocketMessage(
            type = Constants.WEBSOCKET_MESSAGE_TYPE_PONG,
            timestamp = System.currentTimeMillis()
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
            null
        }
    }

    /**
     * Parses incoming data message.
     */
    fun parseDataMessage(message: WebSocketMessage): IncomingDataMessage? {
        if (message.type != Constants.WEBSOCKET_MESSAGE_TYPE_DATA) return null
        if (message.messageId == null || message.dataType == null || message.data == null) return null

        return IncomingDataMessage(
            messageId = message.messageId,
            dataType = message.dataType,
            data = message.data,
            timestamp = message.timestamp ?: System.currentTimeMillis()
        )
    }

    /**
     * Parses acknowledgment message.
     */
    fun parseAckMessage(message: WebSocketMessage): AckMessage? {
        if (message.type != Constants.WEBSOCKET_MESSAGE_TYPE_ACK) return null
        if (message.messageId == null) return null

        return AckMessage(messageId = message.messageId)
    }

    /**
     * Parses error message.
     */
    fun parseErrorMessage(message: WebSocketMessage): ErrorMessage? {
        if (message.type != Constants.WEBSOCKET_MESSAGE_TYPE_ERROR) return null
        if (message.error == null) return null

        return ErrorMessage(
            error = message.error,
            messageId = message.messageId
        )
    }

    /**
     * Generates unique message ID.
     */
    private fun generateMessageId(): String {
        return "${System.currentTimeMillis()}-${(0..9999).random()}"
    }
}
