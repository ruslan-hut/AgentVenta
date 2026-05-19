package ua.com.programmer.agentventa.infrastructure.config

import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.Lazy
import okhttp3.Dns
import okhttp3.dnsoverhttps.DnsOverHttps
import ua.com.programmer.agentventa.infrastructure.logger.Logger
import java.net.InetAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Three-tier resolver: system DNS → DoH (Cloudflare, bootstrapped on
 * 1.1.1.1 so the resolver host itself needs no system DNS) → last-known-good
 * IP from SharedPreferences. Motivation: on flaky carrier networks the
 * system resolver returns `UnknownHostException` for tens of seconds while
 * HTTPS to a known IP still works; WebSocket reconnect needs a fresh
 * resolution every time and otherwise sits in a backoff loop until system
 * DNS recovers.
 *
 * Tier semantics:
 *  - Tier 1 system DNS: primary; result stored into [TTL_MS] cache.
 *  - Tier 2 DoH: consulted on tier-1 [UnknownHostException]; result is a
 *    real resolution, so [lastLookupUsedFallback] stays false. Stored into
 *    the cache too.
 *  - Tier 3 cached IP: consulted only when both tier 1 and tier 2 failed.
 *    [lastLookupUsedFallback] flips true so the WS reconnect path can
 *    decide whether to [evict] based on the next connect's error class.
 */
@Singleton
class CachingDns @Inject constructor(
    private val prefs: SharedPreferences,
    private val logger: Logger,
    // Lazy to break the construction cycle: DoH internally owns an OkHttp
    // client; CachingDns is a dependency of every OkHttp client. Lazy
    // defers DoH instantiation until the first system-DNS miss.
    private val doh: Lazy<DnsOverHttps>,
) : Dns {

    @Volatile
    private var fallbackInUse: Boolean = false

    /** True if the most recent lookup served from the prefs cache (tier 3). */
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
            // Tier 2: DoH. Bootstrap IPs mean we never call back into
            // system DNS, so this works exactly when system DNS is broken.
            try {
                val viaDoh = doh.get().lookup(hostname)
                if (viaDoh.isNotEmpty()) {
                    store(hostname, viaDoh.first())
                    logger.w(TAG, "dns.doh.hit", mapOf(
                        "host" to hostname,
                        "ip" to viaDoh.first().hostAddress.orEmpty(),
                    ))
                    fallbackInUse = false
                    return viaDoh
                }
            } catch (dohErr: Exception) {
                logger.w(TAG, "dns.doh.miss", mapOf(
                    "host" to hostname,
                    "error" to (dohErr.message ?: ""),
                ))
            }
            // Tier 3: last-known-good cached IP.
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
