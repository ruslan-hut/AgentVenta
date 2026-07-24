package ua.com.programmer.agentventa.data.remote

/**
 * Per-data-type counters for a single sync run: how many documents of each type
 * went out and how many catalog elements of each `value_id` came in. Created by
 * whoever starts the sync (the periodic worker or NetworkRepository), threaded
 * through the transport, and read once the run completes to produce the single
 * summary line that goes to the log and to the notification shade.
 *
 * Confined to the one coroutine that runs the sync — not thread-safe by design.
 */
class SyncStats {

    private val sentCounters = LinkedHashMap<String, Int>()
    private val receivedCounters = LinkedHashMap<String, Int>()

    /** Documents uploaded, keyed by document type ("order", "cash", …). */
    val sent: Map<String, Int> get() = sentCounters

    /** Catalog elements saved, keyed by `value_id` ("item", "price", …). */
    val received: Map<String, Int> get() = receivedCounters

    val isEmpty: Boolean get() = sentCounters.isEmpty() && receivedCounters.isEmpty()

    fun addSent(type: String, count: Int = 1) {
        add(sentCounters, type, count)
    }

    fun addReceived(counts: Map<String, Int>) {
        for ((type, count) in counts) add(receivedCounters, type, count)
    }

    private fun add(counters: MutableMap<String, Int>, type: String, count: Int) {
        if (count <= 0 || type.isEmpty()) return
        counters[type] = (counters[type] ?: 0) + count
    }
}
