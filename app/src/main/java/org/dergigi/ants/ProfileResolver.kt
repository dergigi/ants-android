package org.dergigi.ants

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

/** Session-only discovery cache. No private keys or synthetic metadata events. */
internal class ProfileResolver(
    private val relay: RelaySearch,
    private val sign: suspend (Nip01Event) -> Nip01Event?,
    private val outbox: OutboxRouter? = null,
) {
    private fun eventWeight(events: List<Nip01Event>): Long = events.sumOf { event ->
        event.retainedBytes
    }
    private val searchCache = ProfileLookupCache<List<Nip01Event>>(50,
        lifetime = { if (it.isEmpty()) 30_000L else 300_000L }, weight = ::eventWeight)
    private val vertexCache = ProfileLookupCache<List<Nip01Event>?>(50,
        lifetime = { if (it.isNullOrEmpty()) 30_000L else 300_000L }, weight = { eventWeight(it.orEmpty()) })
    private val metadataCache = ProfileLookupCache<List<Nip01Event>>(100,
        lifetime = { if (it.isEmpty()) 30_000L else 300_000L }, weight = ::eventWeight)
    private val contactsCache = ProfileLookupCache<List<String>>(8,
        lifetime = { if (it.isEmpty()) 30_000L else 300_000L }, weight = { it.size * 192L })
    private val resolvedCache = ProfileLookupCache<String>(200)
    private val verified = ConcurrentHashMap<String, Pair<Long, String?>>()
    fun clear() { searchCache.clear(); vertexCache.clear(); metadataCache.clear(); resolvedCache.clear(); contactsCache.clear(); verified.clear() }
    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private suspend fun request(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = relay.http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    check(it.isSuccessful)
                    val source = checkNotNull(it.body).source()
                    source.request(1_000_001)
                    check(source.buffer.size <= 1_000_000)
                    source.readUtf8()
                } }
                if (!continuation.isCancelled) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
    suspend fun verifyNip05(value: String, pubkey: String): Boolean? = nip05(value)?.let { it == pubkey }

    private suspend fun nip05(value: String): String? {
        val address = normalizedNip05(value)
        verified[address]?.takeIf { System.currentTimeMillis() - it.first < 300_000 }?.let { return it.second }
        val progress = coroutineContext[Nip05LookupProgress]
        progress?.start()
        val key = try { withTimeoutOrNull(3000) { optional {
            val name = address.substringBefore('@')
            val url = nip05LookupUrl(address)
            val json = JSONObject(request(Request.Builder().url(url).build()))
            Nip19.normalizePubkey(json.getJSONObject("names").optString(name))
        } }
        } finally { progress?.finish() }
        if (verified.size >= 500) verified.clear()
        verified[address] = System.currentTimeMillis() to key
        return key
    }
    private suspend fun collect(filters: List<JSONObject>, urls: List<String>, timeout: Long = 7000): List<Nip01Event> {
        val events = mutableListOf<Nip01Event>()
        if (filters.isEmpty()) return events
        val branches = filters.map { SearchBranch(it) }
        (outbox?.search(branches, urls.distinct(), timeout) ?: relay.search(branches, urls.distinct(), timeout)).collect {
            if (it is RelayUpdate.Event) events.add(it.event)
        }
        return events
    }
    suspend fun contacts(identity: String?, urls: List<String>): List<String> {
        val owner = identity ?: error("Use /login before searching with @contacts.")
        return contactsCache.get("$owner:${urls.distinct().sorted().joinToString()}") {
            val filter = JSONObject().put("kinds", JSONArray().put(3)).put("authors", JSONArray().put(owner)).put("limit", 1)
            val latest = latestContactList(collect(listOf(filter), urls + generalRelays, 7000), owner)
                ?: error("Couldn't load your public follow list. Check your relays and retry.")
            contactPubkeys(latest).also {
                require(it.size <= MAX_SEARCH_CONTACTS) { "Your follow list exceeds the $MAX_SEARCH_CONTACTS-contact search limit." }
            }
        }
    }

    private fun metadata(events: List<Nip01Event>) = events.filter { it.kind == 0 && it.isRenderable() }
        .sortedByDescending { it.createdAt }.distinctBy { it.pubkey }
    private suspend fun profiles(keys: List<String>): List<Nip01Event> {
        val normalized = keys.distinct().sorted()
        if (normalized.isEmpty()) return emptyList()
        return metadataCache.get(normalized.joinToString(",")) {
            metadata(collect(listOf(JSONObject().put("kinds", JSONArray().put(0))
                .put("authors", JSONArray(normalized)).put("limit", normalized.size)), listOf("wss://purplepag.es") + generalRelays))
        }
    }

    private suspend fun vertex(query: String, identity: String): List<Nip01Event>? =
        vertexCache.get("$identity:${query.trim().lowercase()}") { fetchVertex(query, identity) }

    private suspend fun fetchVertex(query: String, identity: String): List<Nip01Event>? {
        if (query.length <= 3) return null
        val unsigned = Nip01Event.complete(identity, Instant.now().epochSecond, 5315, listOf(
            listOf("param", "search", query), listOf("param", "sort", "personalizedPagerank"),
            listOf("param", "source", identity), listOf("param", "limit", "10"),
            listOf("request_id", java.util.UUID.randomUUID().toString()),
        ), "", "")
        val signed = sign(unsigned) ?: return null
        // Only the Vertex request uses this deadline. Outbox discovery and
        // metadata hydration have their own budgets and must not discard rank.
        val keys = withTimeoutOrNull(10_000) { optional {
            val response = request(Request.Builder().url("https://relay.vertexlab.io/api/v1/dvms")
                .post(signed.toJsonString().toRequestBody("application/json".toMediaType())).build())
            val event = checkNotNull(Nip01Event.parse(JSONObject(response)))
            check(event.kind == 6315 && event.verify() && event.tags.any { it.size > 1 && it[0] == "e" && it[1] == signed.id })
            check(event.tags.filter { it.firstOrNull() == "p" }.all { it.getOrNull(1) == identity })
            val results = JSONArray(event.content)
            (0 until results.length()).mapNotNull { Nip19.normalizePubkey(results.getJSONObject(it).optString("pubkey")) }.distinct().take(10)
        } } ?: return null
        if (keys.isEmpty()) return null
        val found = profiles(keys).associateBy { it.pubkey }
        return keys.mapNotNull(found::get).takeIf { it.isNotEmpty() }
    }
    suspend fun search(query: String, identity: String?, urls: List<String>): List<Nip01Event> {
        val term = query.trim()
        val key = "$identity:${urls.distinct().sorted().joinToString()}:${term.lowercase()}"
        return searchCache.get(key) {
            val direct = Nip19.normalizePubkey(term.removePrefix("nostr:"))
            val address = if (direct == null && ('@' in term || '.' in term && !term.contains(' '))) nip05(term) else null
            if (direct != null || address != null) profiles(listOf(checkNotNull(direct ?: address)))
            else (if (identity != null) vertex(term, identity) else null) ?: fallback(term, identity, urls)
        }
    }
    private suspend fun fallback(term: String, identity: String?, urls: List<String>): List<Nip01Event> = coroutineScope {
        val candidates = metadata(collect(listOf(JSONObject().put("kinds", JSONArray().put(0)).put("search", term).put("limit", 200)),
            urls + listOf("wss://purplepag.es", "wss://relay.vertexlab.io"))).take(200)
        if (candidates.isEmpty()) return@coroutineScope emptyList()
        val matchScores = candidates.associate { it.pubkey to profileMatchScore(term, it) }
        // Relays commonly return newest metadata first. Spend the bounded
        // verification budget on relevant matches, not recently edited accounts.
        val probes = candidates.sortedWith(compareByDescending<Nip01Event> { matchScores[it.pubkey] ?: 0 }
            .thenByDescending { authorMatchScore(term, it) }.thenBy { it.pubkey }).take(50)
        val friends = async {
            if (identity == null) emptySet() else collect(listOf(JSONObject().put("kinds", JSONArray().put(3)).put("authors", JSONArray().put(identity)).put("limit", 1)), generalRelays, 5000)
                .maxByOrNull { it.createdAt }?.tags?.filter { it.size > 1 && it[0] == "p" }?.map { it[1] }?.toSet().orEmpty()
        }
        val checks = ConcurrentHashMap<String, Boolean>()
        val verification = launch {
            withTimeoutOrNull(8000) {
                val slots = Semaphore(8)
                probes.map { event -> async { slots.withPermit {
                    val n5 = profileFields(event).nip05
                    if (n5.isNotBlank()) nip05(n5)?.let { resolved -> checks[event.pubkey] = resolved == event.pubkey }
                } } }.awaitAll()
            }
        }
        val zaps = async {
            val filters = probes.flatMap { event -> listOf(
                JSONObject().put("kinds", JSONArray().put(9321)).put("authors", JSONArray().put(event.pubkey)).put("limit", 1),
                JSONObject().put("kinds", JSONArray().put(9735)).put("#P", JSONArray().put(event.pubkey)).put("limit", 1),
            ) }
            // Keep relay requests small enough for commonly configured filter limits.
            val activitySlots = Semaphore(3)
            filters.chunked(10).map { batch -> async { activitySlots.withPermit { collect(batch, generalRelays, 5000) } } }.awaitAll().flatten()
        }
        verification.join()
        val followed = friends.await(); val activity = zaps.await()
        val nuts = activity.filter { it.kind == 9321 }.map { it.pubkey }.toSet()
        val senders = activity.filter { it.kind == 9735 }.mapNotNull { it.tagValue("P") }.toSet()
        fun score(event: Nip01Event): Int {
            val verificationScore = when (checks[event.pubkey]) {
                true -> 100 + if (normalizedNip05(profileFields(event).nip05).startsWith("_@")) 50 else 0
                false -> -150
                null -> 0
            }
            return (matchScores[event.pubkey] ?: 0) + verificationScore + (if (event.pubkey in nuts) 150 else if (event.pubkey in senders) 40 else 0) + if (event.pubkey in followed) 50 else 0
        }
        candidates.sortedWith(compareByDescending<Nip01Event> { score(it) }.thenByDescending { it.pubkey in followed }
            .thenBy { profileFields(it).let { f -> f.display.ifBlank { f.name }.lowercase() } })
    }
    suspend fun resolve(raw: String, identity: String?, urls: List<String>): String =
        resolvedCache.get("$identity:${urls.distinct().sorted().joinToString()}:${raw.trim().lowercase()}") {
            resolveUncached(raw, identity, urls)
        }

    private suspend fun resolveUncached(raw: String, identity: String?, urls: List<String>): String {
        val value = raw.removePrefix("nostr:").removePrefix("@")
        Nip19.normalizePubkey(value)?.let { return it }
        if ('@' in value || '.' in value) return nip05(value) ?: error("Couldn't resolve $raw.")
        val candidates = search(value, identity, urls).filter { authorMatchScore(value, it) > 0 }
            .sortedWith(compareByDescending<Nip01Event> { authorMatchScore(value, it) }.thenByDescending { it.createdAt })
        for (batch in candidates.take(32).chunked(8)) {
            val matches = coroutineScope { batch.map { event -> async { event.takeIf { profileFields(it).nip05.isNotBlank() && nip05(profileFields(it).nip05) == it.pubkey } } }.awaitAll() }
            matches.firstOrNull { it != null }?.let { return it.pubkey }
        }
        return candidates.firstOrNull()?.pubkey ?: error("No profile found for $raw.")
    }
}
