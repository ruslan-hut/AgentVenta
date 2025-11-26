package ua.com.programmer.agentventa.data.websocket

/**
 * Represents the current state of the WebSocket connection.
 * Used for UI updates and connection management logic.
 */
sealed class WebSocketState {
    /**
     * WebSocket is disconnected and not attempting to connect.
     */
    object Disconnected : WebSocketState()

    /**
     * WebSocket is attempting to establish connection.
     * @param attempt Current reconnection attempt number (1-based)
     */
    data class Connecting(val attempt: Int = 1) : WebSocketState()

    /**
     * WebSocket is successfully connected and authenticated.
     * @param deviceUuid The device UUID used for this connection
     */
    data class Connected(val deviceUuid: String) : WebSocketState()

    /**
     * WebSocket connection failed.
     * @param error Error message describing the failure
     * @param canRetry Whether automatic reconnection will be attempted
     */
    data class Error(val error: String, val canRetry: Boolean = true) : WebSocketState()

    /**
     * WebSocket is reconnecting after connection loss.
     * @param delayMs Delay in milliseconds before next attempt
     * @param attempt Current reconnection attempt number
     */
    data class Reconnecting(val delayMs: Int, val attempt: Int) : WebSocketState()
}

/**
 * Extension to check if WebSocket is in a connected state.
 */
fun WebSocketState.isConnected(): Boolean = this is WebSocketState.Connected

/**
 * Extension to check if WebSocket is actively trying to connect.
 */
fun WebSocketState.isConnecting(): Boolean =
    this is WebSocketState.Connecting || this is WebSocketState.Reconnecting

/**
 * Extension to get human-readable state description.
 */
fun WebSocketState.getDescription(): String = when (this) {
    is WebSocketState.Disconnected -> "Disconnected"
    is WebSocketState.Connecting -> "Connecting${if (attempt > 1) " (attempt $attempt)" else ""}"
    is WebSocketState.Connected -> "Connected"
    is WebSocketState.Error -> "Error: $error"
    is WebSocketState.Reconnecting -> "Reconnecting in ${delayMs / 1000}s (attempt $attempt)"
}
