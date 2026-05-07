package ua.com.programmer.agentventa.infrastructure.logger

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import ua.com.programmer.agentventa.di.CoroutineModule
import ua.com.programmer.agentventa.domain.repository.DebugLogRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point used by [Logger] for fan-out into the debug-log table.
 *
 * - No-ops when [DebugLogToggle] is off, so existing log call sites pay zero
 *   cost beyond a flag read when the feature is disabled.
 * - Persists asynchronously on the IO dispatcher; the calling thread (often a
 *   coroutine inside WS message handlers) is never blocked on DB writes.
 * - Signals [RemoteLogTickle] after each insert. The uploader debounces, so a
 *   burst of inserts only triggers one POST.
 */
@Singleton
class RemoteLogSink @Inject constructor(
    private val repository: DebugLogRepository,
    private val toggle: DebugLogToggle,
    private val tickle: RemoteLogTickle,
    @CoroutineModule.IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun emit(level: String, tag: String, message: String, fields: Map<String, Any?>? = null) {
        if (!toggle.isEnabled()) return
        val fieldsJson = fields?.let { encodeFields(it) } ?: "{}"
        scope.launch {
            try {
                repository.record(level, tag, message, fieldsJson)
                tickle.signal()
            } catch (_: Throwable) {
                // Sink must not throw out of a logging call site.
            }
        }
    }

    private fun encodeFields(fields: Map<String, Any?>): String {
        val obj = JSONObject()
        for ((k, v) in fields) {
            if (v == null) continue
            when (v) {
                is Number, is Boolean, is String -> obj.put(k, v)
                is Collection<*> -> obj.put(k, v.toString())
                else -> obj.put(k, v.toString())
            }
        }
        return obj.toString()
    }
}
