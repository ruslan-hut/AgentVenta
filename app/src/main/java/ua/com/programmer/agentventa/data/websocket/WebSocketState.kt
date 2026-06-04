package ua.com.programmer.agentventa.data.websocket

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

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
     * Device is pending approval from administrator.
     * No reconnection attempts will be made until device is approved.
     * @param deviceUuid The device UUID awaiting approval
     */
    data class Pending(val deviceUuid: String) : WebSocketState()

    /**
     * License-related error that prevents connection.
     * No reconnection attempts will be made until license issue is resolved.
     * @param errorCode The error code (license_expired, license_not_active, device_limit_reached)
     * @param reason Human-readable reason from server
     */
    data class LicenseError(val errorCode: String, val reason: String) : WebSocketState()

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
 * True once an in-progress connection attempt has settled into a resting
 * outcome: either Connected, or a failure that no further reconnection will
 * change on its own (Pending, LicenseError, non-retryable Error). Connecting,
 * Reconnecting, Disconnected and retryable Error are still in flight.
 * Used by callers that need to wait for a connect() to resolve one way or
 * the other rather than blocking until a fixed timeout.
 */
fun WebSocketState.isSettled(): Boolean =
    this is WebSocketState.Connected ||
    this is WebSocketState.Pending ||
    this is WebSocketState.LicenseError ||
    (this is WebSocketState.Error && !this.canRetry)

/**
 * Suspends until the connection state matches [predicate] or [timeoutMs]
 * elapses. Returns the matching state, or null on timeout. Shared by every
 * caller that needs to await a connection outcome.
 */
suspend fun StateFlow<WebSocketState>.awaitState(
    timeoutMs: Long,
    predicate: (WebSocketState) -> Boolean,
): WebSocketState? = withTimeoutOrNull(timeoutMs.milliseconds) { first(predicate) }

/**
 * Extension to get human-readable state description.
 */
fun WebSocketState.getDescription(): String = when (this) {
    is WebSocketState.Disconnected -> "Disconnected"
    is WebSocketState.Connecting -> "Connecting${if (attempt > 1) " (attempt $attempt)" else ""}"
    is WebSocketState.Connected -> "Connected"
    is WebSocketState.Pending -> "Pending Approval"
    is WebSocketState.LicenseError -> "License Error: $reason"
    is WebSocketState.Error -> "Error: $error"
    is WebSocketState.Reconnecting -> "Reconnecting in ${delayMs / 1000}s (attempt $attempt)"
}
