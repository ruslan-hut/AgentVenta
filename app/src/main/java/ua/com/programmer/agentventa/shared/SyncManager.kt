package ua.com.programmer.agentventa.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.http.Result
import ua.com.programmer.agentventa.repository.NetworkRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync operation events (one-time).
 */
sealed class SyncEvent {
    data class Progress(val message: String) : SyncEvent()
    data class Success(val message: String) : SyncEvent()
    data class Error(val message: String) : SyncEvent()
}

/**
 * Manages data synchronization operations.
 * Extracted from SharedViewModel for single responsibility.
 */
@Singleton
class SyncManager @Inject constructor(
    private val networkRepository: NetworkRepository
) {
    private val _updateState = MutableStateFlow<Result?>(null)
    val updateState: StateFlow<Result?> = _updateState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _progressMessage = MutableStateFlow("")
    val progressMessage: StateFlow<String> = _progressMessage.asStateFlow()

    private val _syncEvents = EventChannel<SyncEvent>()
    val syncEvents = _syncEvents.flow

    fun callDiffSync(scope: CoroutineScope, afterSync: () -> Unit) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _progressMessage.value = ""
        scope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.updateDifferential().collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        handleSyncResult(result)
                    }
                }
            }
            afterSync()
        }
    }

    fun callFullSync(scope: CoroutineScope, afterSync: () -> Unit) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _progressMessage.value = ""
        scope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.updateAll().collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        handleSyncResult(result)
                    }
                }
            }
            afterSync()
        }
    }

    fun callPrintDocument(scope: CoroutineScope, guid: String, cacheDir: File?, afterSync: (Boolean) -> Unit) {
        if (_isRefreshing.value) return afterSync(false)
        if (cacheDir == null) return afterSync(false)
        _isRefreshing.value = true
        _progressMessage.value = ""
        scope.launch {
            withContext(Dispatchers.IO) {
                networkRepository.getPrintData(guid, cacheDir).collect { result ->
                    withContext(Dispatchers.Main) {
                        _updateState.value = result
                        if (result is Result.Success || result is Result.Error) {
                            _isRefreshing.value = false
                            afterSync(result is Result.Success)
                        }
                    }
                }
            }
        }
    }

    private fun handleSyncResult(result: Result) {
        when (result) {
            is Result.Success -> {
                _isRefreshing.value = false
                _syncEvents.send(SyncEvent.Success(result.message))
            }
            is Result.Error -> {
                _isRefreshing.value = false
                _syncEvents.send(SyncEvent.Error(result.message))
            }
            is Result.Progress -> {
                _syncEvents.send(SyncEvent.Progress(result.message))
            }
        }
    }

    fun addProgressText(text: String) {
        val current = _progressMessage.value
        _progressMessage.value = if (current.isBlank()) text else "$current\n$text"
    }
}
