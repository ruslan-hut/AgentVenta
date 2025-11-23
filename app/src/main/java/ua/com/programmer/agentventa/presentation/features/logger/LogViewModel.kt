package ua.com.programmer.agentventa.presentation.features.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.domain.repository.LogRepository
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository
): ViewModel() {

    val logs = logRepository.fetchLogs().asLiveData()

    fun shareLogs(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val logs = logRepository.readLogs()
            val logsString = Gson().toJson(logs)
            onResult(logsString)
        }
    }

}