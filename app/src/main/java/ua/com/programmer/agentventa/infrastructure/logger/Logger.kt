package ua.com.programmer.agentventa.infrastructure.logger

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.BuildConfig
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
        // Mirror to logcat in debug builds so the device on Android Studio shows
        // every log line live. Stripped from release (no-op behind BuildConfig).
        if (BuildConfig.DEBUG) {
            val line = if (fields != null) "$message $fields" else message
            when (level) {
                "E" -> Log.e(tag, line)
                "W" -> Log.w(tag, line)
                else -> Log.d(tag, line)
            }
        }
        // User-visible log keeps only level/tag/message — the structured fields
        // are dropped. A structured debug line therefore shows up as a bare,
        // detail-less entry (e.g. "ws.batch.start"), which is just noise on the
        // log screen. Route structured debug lines to the remote sink only;
        // warnings, errors and plain (human-readable) messages stay visible.
        if (level != "D" || fields == null) {
            CoroutineScope(dispatcher).launch {
                repository.log(level, tag, message)
            }
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
