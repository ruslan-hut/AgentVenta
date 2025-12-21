package ua.com.programmer.agentventa.data.websocket

/**
 * Represents a message awaiting acknowledgment from the server.
 * Used for retry logic and ensuring reliable message delivery.
 */
data class PendingMessage(
    val messageId: String,
    val dataType: String,
    val data: String,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val documentGuid: String? = null  // For document uploads, tracks the document GUID
) {
    /**
     * Checks if this message has exceeded maximum retry attempts.
     */
    fun hasExceededRetries(): Boolean = retryCount >= maxRetries

    /**
     * Creates a new instance with incremented retry count.
     */
    fun incrementRetry(): PendingMessage = copy(retryCount = retryCount + 1)

    /**
     * Checks if message is too old (older than 24 hours).
     */
    fun isExpired(): Boolean {
        val ageMs = System.currentTimeMillis() - timestamp
        val maxAgeMs = 24 * 60 * 60 * 1000L // 24 hours
        return ageMs > maxAgeMs
    }
}

/**
 * Result of sending a message through WebSocket.
 */
sealed class SendResult {
    /**
     * Message was sent successfully and is awaiting acknowledgment.
     */
    data class Sent(val messageId: String) : SendResult()

    /**
     * Message was acknowledged by the server.
     */
    data class Acknowledged(val messageId: String) : SendResult()

    /**
     * Message sending failed.
     */
    data class Failed(val messageId: String, val error: String, val canRetry: Boolean = true) : SendResult()

    /**
     * Message sending is in progress (queued or being sent).
     */
    data class Pending(val messageId: String) : SendResult()
}
