package ua.com.programmer.agentventa.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.websocket.DocumentAck
import ua.com.programmer.agentventa.data.websocket.IncomingDataMessage
import ua.com.programmer.agentventa.data.websocket.SendResult
import ua.com.programmer.agentventa.data.websocket.WebSocketState

/**
 * Repository interface for WebSocket communication with relay server.
 * Manages connection lifecycle, message sending/receiving, and state tracking.
 */
interface WebSocketRepository {

    /**
     * Current WebSocket connection state.
     * Use this StateFlow to observe connection status changes in UI.
     */
    val connectionState: StateFlow<WebSocketState>

    /**
     * Flow of incoming data messages from the server.
     * Emits when new data arrives from accounting system.
     */
    val incomingMessages: Flow<IncomingDataMessage>

    /**
     * Flow of document acknowledgments from the server.
     * Emits when server confirms receipt of uploaded documents.
     * Subscribe to this to mark documents as sent in local database.
     */
    val documentAcks: SharedFlow<DocumentAck>

    /**
     * Connects to the relay server using the provided user account.
     * Automatically handles reconnection with exponential backoff.
     *
     * @param account UserAccount with relay server configuration
     * @return true if connection initiated successfully, false if already connected
     */
    suspend fun connect(account: UserAccount): Boolean

    /**
     * Disconnects from the relay server.
     * Cancels any pending reconnection attempts.
     */
    suspend fun disconnect()

    /**
     * Sends data to the accounting system through the relay server.
     *
     * @param dataType Type of data being sent (order, cash, location, etc.)
     * @param data JSON string containing the data
     * @return Flow emitting send result status
     */
    suspend fun sendData(dataType: String, data: String): Flow<SendResult>

    /**
     * Sends a generic WebSocket message.
     * Used for non-data messages like sync_settings, custom commands, etc.
     *
     * @param type Message type (sync_settings, custom_command, etc.)
     * @param payload JSON string containing the message payload
     * @return Flow emitting send result status
     */
    suspend fun sendMessage(type: String, payload: String): Flow<SendResult>

    /**
     * Manually triggers a reconnection attempt.
     * Useful for "retry" button in UI.
     *
     * @return true if reconnection initiated, false if already connected
     */
    suspend fun reconnect(): Boolean

    /**
     * Checks if currently connected to the relay server.
     *
     * @return true if connection is established and active
     */
    fun isConnected(): Boolean

    /**
     * Gets count of pending messages awaiting acknowledgment.
     *
     * @return number of unacknowledged messages
     */
    fun getPendingMessageCount(): Int

    /**
     * Retries sending all failed messages.
     * Useful for bulk retry after connection restoration.
     *
     * @return number of messages queued for retry
     */
    suspend fun retryFailedMessages(): Int

    /**
     * Clears all pending messages.
     * Use with caution - only for manual intervention.
     */
    suspend fun clearPendingMessages()
}
