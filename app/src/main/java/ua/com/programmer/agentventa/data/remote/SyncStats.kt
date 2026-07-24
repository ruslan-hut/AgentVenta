package ua.com.programmer.agentventa.data.remote

/**
 * Counters for a single sync run: how many documents went out and how many
 * catalog elements came in. Created by whoever starts the sync (the periodic
 * worker or NetworkRepository), threaded through the transport, and read once
 * the flow completes to decide whether a result notification is warranted.
 *
 * Confined to the one coroutine that runs the sync — not thread-safe by design.
 */
class SyncStats {

    var sent: Int = 0
        private set

    var received: Int = 0
        private set

    fun addSent(count: Int) {
        if (count > 0) sent += count
    }

    fun addReceived(count: Int) {
        if (count > 0) received += count
    }

    val isEmpty: Boolean get() = sent == 0 && received == 0
}
