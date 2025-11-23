package ua.com.programmer.agentventa.infrastructure.logger

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.di.CoroutineModule
import ua.com.programmer.agentventa.domain.repository.LogRepository
import javax.inject.Inject

class Logger @Inject constructor(
    private val repository: LogRepository,
    @CoroutineModule.IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    fun d(tag: String, message: String) {
        CoroutineScope(dispatcher).launch {
            repository.log("D", tag, message)
        }
    }

    fun w(tag: String, message: String) {
        CoroutineScope(dispatcher).launch {
            repository.log("W", tag, message)
        }
    }

    fun e(tag: String, message: String) {
        CoroutineScope(dispatcher).launch {
            repository.log("E", tag, message)
        }
    }

    fun cleanUp() {
        CoroutineScope(dispatcher).launch {
            val deleted = repository.cleanUp()
            if (deleted > 0) d("Logger", "Deleted $deleted old log events")
        }
    }

}