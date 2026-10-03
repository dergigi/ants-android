package org.dergigi.ants

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal data class AdvertisedRelays(val read: List<String>, val write: List<String>)

internal fun secureRelayUrl(value: String): String? = runCatching {
    val uri = URI(value.trim())
    require(uri.scheme.equals("wss", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null)
    require(uri.port == -1 || uri.port in 1..65535)
    // Preserve case-sensitive paths and query strings; normalize only the origin.
    val host = uri.host.lowercase().let { if (':' in it && !it.startsWith('[')) "[$it]" else it }
    "wss://$host${if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"}${uri.rawPath.orEmpty().let { if (it == "/") "" else it }}${uri.rawQuery?.let { "?$it" }.orEmpty()}"
}.getOrNull()

internal fun advertisedRelays(event: Nip01Event): AdvertisedRelays {
    fun urls(marker: String) = event.tags.asSequence().filter {
        it.firstOrNull() == "r" && (it.getOrNull(2).isNullOrBlank() || it.getOrNull(2) == marker)
    }.mapNotNull { it.getOrNull(1)?.let(::secureRelayUrl) }.distinct().take(4).toList()
    return AdvertisedRelays(urls("read"), urls("write"))
}

/** NIP-65 routing. Metadata lookup uses the transport directly, never this router. */
internal class OutboxRouter(private val relay: RelaySearch) {
    private data class Entry(val relays: AdvertisedRelays, val expires: Long)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val discoveryLock = Mutex()
    private val revision = AtomicInteger()
    fun clear() { revision.incrementAndGet(); cache.clear() }

    fun search(branches: List<SearchBranch>, searchRelays: List<String>, duration: Long = 14000) = flow {
        emitAll(relay.searchRoutes(routes(branches, searchRelays), duration))
    }.flowOn(Dispatchers.IO)

    private fun keys(branch: SearchBranch, field: String): List<String> = branch.filter.optJSONArray(field)?.let { array ->
        (0 until array.length()).mapNotNull { Nip19.normalizePubkey(array.optString(it)) }
    }.orEmpty()

    private suspend fun discover(keys: List<String>, fallback: List<String>): Map<String, AdvertisedRelays> = discoveryLock.withLock {
        val epoch = revision.get()
        val missing = keys.filter { (cache[it]?.expires ?: 0L) <= SystemClock.elapsedRealtime() }
        if (missing.isNotEmpty()) {
            val latest = mutableMapOf<String, Nip01Event>()
            val filter = JSONObject().put("kinds", JSONArray().put(10002)).put("authors", JSONArray(missing)).put("limit", missing.size * 2)
            try {
                relay.search(listOf(SearchBranch(filter)), (listOf("wss://purplepag.es") + generalRelays + fallback).distinct().take(8), 4000).collect { update ->
                    if (update is RelayUpdate.Event) {
                        val event = update.event
                        val previous = latest[event.pubkey]
                        if (previous == null || event.createdAt > previous.createdAt ||
                            (event.createdAt == previous.createdAt && event.id < previous.id)) latest[event.pubkey] = event
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* A missing relay list never blocks fallback queries. */ }
            if (epoch == revision.get()) {
                val now = SystemClock.elapsedRealtime()
                missing.forEach { key ->
                    val event = latest[key]
                    cache[key] = Entry(event?.let(::advertisedRelays) ?: AdvertisedRelays(emptyList(), emptyList()),
                        now + if (event == null) 60_000 else 600_000)
                }
                while (cache.size > 256) cache.keys.firstOrNull()?.let(cache::remove) ?: break
            }
        }
        keys.mapNotNull { key -> cache[key]?.let { key to it.relays } }.toMap()
    }

    private suspend fun routes(branches: List<SearchBranch>, searchRelays: List<String>): Map<String, List<SearchBranch>> {
        val configured = searchRelays.mapNotNull(::secureRelayUrl).distinct().take(12)
        val fallback = (generalRelays + configured).distinct()
        val structured = branches.filterNot { it.filter.has("search") }
        val people = structured.flatMap { keys(it, "authors") + keys(it, "#p") + it.outboxAuthors }.distinct().take(32)
        val lists = if (people.isEmpty()) emptyMap() else discover(people, configured)
        val routes = linkedMapOf<String, MutableList<SearchBranch>>()
        val discovered = linkedSetOf<String>()
        fun add(url: String, branch: SearchBranch) { routes.getOrPut(url) { mutableListOf() }.add(branch) }
        branches.forEach { branch ->
            if (branch.filter.has("search")) configured.forEach { add(it, branch) }
            else {
                val preferred = (keys(branch, "authors") + branch.outboxAuthors).flatMap { lists[it]?.write.orEmpty() } +
                    keys(branch, "#p").flatMap { lists[it]?.read.orEmpty() } + branch.relayHints.mapNotNull(::secureRelayUrl).take(2)
                preferred.distinct().forEach { url ->
                    if (url in fallback || url in discovered || discovered.size < 16) {
                        if (url !in fallback) discovered += url
                        add(url, branch)
                    }
                }
                fallback.forEach { add(it, branch) }
            }
        }
        return routes.mapValues { (_, filters) -> filters.distinct() }
    }
}
