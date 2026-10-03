package org.dergigi.ants

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal data class ProfileIndicators(val verified: Boolean? = null, val sentZap: Boolean = false, val sentNutzap: Boolean = false)

/** Visible-card lookups only, bounded and cached in memory. Metadata claims are not verification. */
internal object ProfileIndicatorLookup {
    private val relay = RelaySearch()
    private val resolver = ProfileResolver(relay, sign = { null })
    private val slots = Semaphore(3)
    private data class Cached(val at: Long, val indicators: ProfileIndicators)
    private val cache = ConcurrentHashMap<String, Cached>()
    fun clear() { cache.clear(); resolver.clear() }

    suspend fun load(pubkey: String, nip05: String, hasLightning: Boolean): ProfileIndicators = withContext(Dispatchers.IO) {
        val key = "$pubkey:$nip05:$hasLightning"
        fun cached() = cache[key]?.takeIf { System.currentTimeMillis() - it.at < 300_000 }?.indicators
        cached() ?: slots.withPermit {
            cached() ?: coroutineScope {
                val verified = async { if (nip05.isBlank()) null else resolver.verifyNip05(nip05, pubkey) }
                var zap = false
                var nutzap = false
                if (hasLightning) {
                    val filters = listOf(
                        SearchBranch(JSONObject().put("kinds", JSONArray().put(9735)).put("#P", JSONArray().put(pubkey)).put("limit", 1)),
                        SearchBranch(JSONObject().put("kinds", JSONArray().put(9321)).put("authors", JSONArray().put(pubkey)).put("limit", 1)),
                    )
                    relay.search(filters, generalRelays, 5000).collect { update ->
                        if (update is RelayUpdate.Event) {
                            if (update.event.kind == 9735) zap = true
                            if (update.event.kind == 9321) nutzap = true
                        }
                    }
                }
                ProfileIndicators(verified.await(), zap, nutzap).also {
                    if (cache.size >= 256) cache.clear()
                    cache[key] = Cached(System.currentTimeMillis(), it)
                }
            }
        }
    }
}
