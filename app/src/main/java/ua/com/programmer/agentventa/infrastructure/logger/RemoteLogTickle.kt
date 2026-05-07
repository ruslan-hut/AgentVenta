package ua.com.programmer.agentventa.infrastructure.logger

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conflated single-slot channel used to wake [RemoteLogUploader] when new log
 * rows arrive. Multiple emit calls coalesce into one signal — the uploader will
 * drain everything pending in the DB the next time it loops. CONFLATED is the
 * right choice here: we never want to grow a backlog of "wake up" tokens, only
 * to hint that there's *something* to do.
 */
@Singleton
class RemoteLogTickle @Inject constructor() {

    private val channel = Channel<Unit>(capacity = Channel.CONFLATED)

    fun signal() {
        channel.trySend(Unit)
    }

    fun asFlow(): Flow<Unit> = channel.consumeAsFlow()
}
