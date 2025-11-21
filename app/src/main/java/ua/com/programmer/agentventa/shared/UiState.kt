package ua.com.programmer.agentventa.shared

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Sealed class for UI state representation.
 * Use for loading/success/error states.
 */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val exception: Throwable? = null) : UiState<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): String? = (this as? Error)?.message
}

/**
 * Channel-based event emitter for one-time UI events.
 * Events are consumed only once and survive configuration changes.
 *
 * Usage in ViewModel:
 * ```
 * private val _events = EventChannel<MyEvent>()
 * val events = _events.flow
 *
 * fun doSomething() {
 *     _events.send(MyEvent.ShowToast("Done"))
 * }
 * ```
 *
 * Usage in Fragment:
 * ```
 * viewLifecycleOwner.lifecycleScope.launch {
 *     viewModel.events.collect { event ->
 *         when (event) {
 *             is MyEvent.ShowToast -> showToast(event.message)
 *         }
 *     }
 * }
 * ```
 */
class EventChannel<T> {
    private val channel = Channel<T>(Channel.BUFFERED)
    val flow: Flow<T> = channel.receiveAsFlow()

    fun send(event: T) {
        channel.trySend(event)
    }

    suspend fun emit(event: T) {
        channel.send(event)
    }
}

/**
 * Common UI events that can be reused across ViewModels.
 */
sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : UiEvent()
    data class Navigate(val destination: String, val args: Map<String, Any> = emptyMap()) : UiEvent()
    data object NavigateBack : UiEvent()
    data class Error(val message: String) : UiEvent()
}

/**
 * Document-specific UI events.
 */
sealed class DocumentEvent {
    data class SaveSuccess(val guid: String) : DocumentEvent()
    data class SaveError(val message: String) : DocumentEvent()
    data object DeleteSuccess : DocumentEvent()
    data class NavigateToPage(val page: Int) : DocumentEvent()
}

/**
 * Extension to create a StateFlow with initial value.
 */
fun <T> mutableStateFlow(initialValue: T): MutableStateFlow<T> = MutableStateFlow(initialValue)

/**
 * Helper class for managing mutable/immutable StateFlow pairs.
 */
class StateFlowHolder<T>(initialValue: T) {
    private val _state = MutableStateFlow(initialValue)
    val state: StateFlow<T> = _state.asStateFlow()

    var value: T
        get() = _state.value
        set(value) { _state.value = value }

    fun update(transform: (T) -> T) {
        _state.value = transform(_state.value)
    }
}
