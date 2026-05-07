package ua.com.programmer.agentventa.infrastructure.logger

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.di.CoroutineModule
import ua.com.programmer.agentventa.domain.repository.LogRepository
import javax.inject.Inject

/**
 * Single facade used by the rest of the app for log lines.
 *
 * Two sinks: the existing [LogRepository] (backs the user-visible log screen)
 * and [RemoteLogSink] (debug-log-table → uploader). Existing two-arg call sites
 * (`logger.d(tag, msg)`) keep working unchanged. New WebSocket call sites use
 * the structured overload (`logger.d(tag, msg, mapOf(...))`) so log analysis on
 * the server can filter on `fields.message_id`, `fields.value_id`, etc.
 */
class Logger @Inject constructor(
    private val repository: LogRepository,
    private val remoteLogSink: RemoteLogSink,
    @CoroutineModule.IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    fun d(tag: String, message: String) {
        write("D", tag, message, null)
    }

    fun w(tag: String, message: String) {
        write("W", tag, message, null)
    }

    fun e(tag: String, message: String) {
        write("E", tag, message, null)
    }

    fun d(tag: String, message: String, fields: Map<String, Any?>?) {
        write("D", tag, message, fields)
    }

    fun w(tag: String, message: String, fields: Map<String, Any?>?) {
        write("W", tag, message, fields)
    }

    fun e(tag: String, message: String, fields: Map<String, Any?>?) {
        write("E", tag, message, fields)
    }

    private fun write(level: String, tag: String, message: String, fields: Map<String, Any?>?) {
        // User-visible log: existing behavior, unchanged.
        CoroutineScope(dispatcher).launch {
            repository.log(level, tag, message)
        }
        // Remote sink: no-op when toggle is off, so the cost is one volatile
        // read for every log line outside debugging windows.
        remoteLogSink.emit(level, tag, message, fields)
    }

    fun cleanUp() {
        CoroutineScope(dispatcher).launch {
            val deleted = repository.cleanUp()
            if (deleted > 0) d("AV-Logger", "Deleted $deleted old log events")
        }
    }
}
