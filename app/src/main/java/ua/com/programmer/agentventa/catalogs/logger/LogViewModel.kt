package ua.com.programmer.agentventa.catalogs.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import ua.com.programmer.agentventa.repository.LogRepository
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    logRepository: LogRepository
): ViewModel() {

    val logs = logRepository.fetchLogs().asLiveData()

}