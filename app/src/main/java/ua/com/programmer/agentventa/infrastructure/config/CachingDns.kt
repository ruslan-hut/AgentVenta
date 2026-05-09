package ua.com.programmer.agentventa.infrastructure.config

import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.Dns
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [Dns.SYSTEM] with a SharedPreferences-backed last-known-good IP cache,
 * used only as a fallback when the system resolver throws
 * [UnknownHostException]. Motivation: on this device, screen-on transitions
 * leave fresh DNS lookups failing for tens of seconds while existing TCP
 * connections (HTTPS log uploads) keep working. WebSocket reconnect needs a
 * fresh resolution every time and otherwise sits in a loop until DNS recovers.
 *
 * Trade-offs (see also: backend host could change → stale cached IP):
 *  - Cache is per-host, single IP, with [TTL_MS] expiry.
 *  - Fallback is consulted only on [UnknownHostException]; never as primary.
 *  - On any later connect failure against the cached IP the caller is expected
 *    to call [evict] so we don't pin a dead host.
 */
@Singleton
class CachingDns @Inject constructor(
    private val prefs: SharedPreferences,
    private val logger: Logger,
) : Dns {

    @Volatile
    private var fallbackInUse: Boolean = false

    /** True if the most recent lookup served from the fallback cache. */
    fun lastLookupUsedFallback(): Boolean = fallbackInUse

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val resolved = Dns.SYSTEM.lookup(hostname)
            if (resolved.isNotEmpty()) {
                store(hostname, resolved.first())
            }
            fallbackInUse = false
            resolved
        } catch (e: UnknownHostException) {
            val cached = readFresh(hostname)
            if (cached != null) {
                logger.w(TAG, "dns.fallback.hit", mapOf(
                    "host" to hostname,
                    "ip" to cached.hostAddress.orEmpty(),
                ))
                fallbackInUse = true
                listOf(cached)
            } else {
                logger.w(TAG, "dns.fallback.miss", mapOf(
                    "host" to hostname,
                    "error" to (e.message ?: ""),
                ))
                fallbackInUse = false
                throw e
            }
        }
    }

    /**
     * Drop a cached entry — call after a connect/TLS failure against the
     * fallback IP so a moved host doesn't pin clients to a dead address.
     */
    fun evict(hostname: String) {
        prefs.edit {
            remove(ipKey(hostname))
            remove(tsKey(hostname))
        }
        logger.d(TAG, "dns.fallback.evict", mapOf("host" to hostname))
    }

    private fun store(hostname: String, address: InetAddress) {
        val ip = address.hostAddress ?: return
        prefs.edit {
            putString(ipKey(hostname), ip)
            putLong(tsKey(hostname), System.currentTimeMillis())
        }
    }

    private fun readFresh(hostname: String): InetAddress? {
        val ip = prefs.getString(ipKey(hostname), null) ?: return null
        val ts = prefs.getLong(tsKey(hostname), 0L)
        if (System.currentTimeMillis() - ts > TTL_MS) return null
        return try {
            // getByName on a literal IP does not perform DNS — it parses bytes.
            InetAddress.getByName(ip)
        } catch (_: Throwable) {
            null
        }
    }

    private fun ipKey(host: String) = "$PREFIX${host}_ip"
    private fun tsKey(host: String) = "$PREFIX${host}_ts"

    companion object {
        private const val TAG = "AV-CachingDns"
        private const val PREFIX = "dns_cache_"
        private const val TTL_MS = 24L * 60 * 60 * 1000 // 24h
    }
}
