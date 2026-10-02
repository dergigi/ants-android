package org.dergigi.ants

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Session-only discovery cache. No private keys or synthetic metadata events. */
internal class ProfileResolver(
    private val relay: RelaySearch,
    private val sign: suspend (Nip01Event) -> Nip01Event?,
) {
    private data class Cached(val time: Long, val events: List<Nip01Event>)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val verified = ConcurrentHashMap<String, Pair<Long, String?>>()
    fun clear() { cache.clear(); verified.clear() }
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
    private suspend fun nip05(value: String): String? {
        val address = normalizedNip05(value)
        verified[address]?.takeIf { System.currentTimeMillis() - it.first < 300_000 }?.let { return it.second }
        val key = withTimeoutOrNull(3000) { optional {
            val name = address.substringBefore('@'); val domain = address.substringAfter('@')
            require(name.isNotBlank() && '.' in domain && domain.none { it in "/:@?#" })
            val url = "https://$domain/.well-known/nostr.json".toHttpUrl().newBuilder().addQueryParameter("name", name).build()
            val json = JSONObject(request(Request.Builder().url(url).build()))
            Nip19.normalizePubkey(json.getJSONObject("names").optString(name))
        } }
        if (verified.size >= 500) verified.clear()
        verified[address] = System.currentTimeMillis() to key
        return key
    }
    private suspend fun collect(filters: List<JSONObject>, urls: List<String>, timeout: Long = 7000): List<Nip01Event> {
        val events = mutableListOf<Nip01Event>()
        if (filters.isEmpty()) return events
        relay.search(filters.map { SearchBranch(it) }, urls.distinct(), timeout).collect {
            if (it is RelayUpdate.Event) events.add(it.event)
        }
        return events
    }
    private fun metadata(events: List<Nip01Event>) = events.filter { it.kind == 0 && it.isRenderable() }
        .sortedByDescending { it.createdAt }.distinctBy { it.pubkey }
    private suspend fun profiles(keys: List<String>) = metadata(collect(listOf(JSONObject()
        .put("kinds", JSONArray().put(0)).put("authors", JSONArray(keys)).put("limit", keys.size)), listOf("wss://purplepag.es") + generalRelays))

    private suspend fun vertex(query: String, identity: String): List<Nip01Event>? {
        if (query.length <= 3) return null
        val unsigned = Nip01Event.complete(identity, Instant.now().epochSecond, 5315, listOf(
            listOf("param", "search", query), listOf("param", "sort", "personalizedPagerank"),
            listOf("param", "source", identity), listOf("param", "limit", "10"),
            listOf("request_id", java.util.UUID.randomUUID().toString()),
        ), "", "")
        val signed = sign(unsigned) ?: return null
        return withTimeoutOrNull(10_000) { optional {
            val response = request(Request.Builder().url("https://relay.vertexlab.io/api/v1/dvms")
                .post(signed.toJsonString().toRequestBody("application/json".toMediaType())).build())
            val event = checkNotNull(Nip01Event.parse(JSONObject(response)))
            check(event.kind == 6315 && event.verify() && event.tags.any { it.size > 1 && it[0] == "e" && it[1] == signed.id })
            check(event.tags.filter { it.firstOrNull() == "p" }.all { it.getOrNull(1) == identity })
            val results = JSONArray(event.content)
            val keys = (0 until results.length()).mapNotNull { Nip19.normalizePubkey(results.getJSONObject(it).optString("pubkey")) }.distinct().take(10)
            if (keys.isEmpty()) return@optional null
            val found = profiles(keys).associateBy { it.pubkey }
            keys.mapNotNull(found::get).takeIf { it.isNotEmpty() }
        } }
    }
    suspend fun search(query: String, identity: String?, urls: List<String>): List<Nip01Event> {
        val term = query.trim()
        Nip19.normalizePubkey(term.removePrefix("nostr:"))?.let { return profiles(listOf(it)) }
        if ('@' in term || '.' in term && !term.contains(' ')) {
            nip05(term)?.let { return profiles(listOf(it)) }
        }
        val key = "$identity:${urls.joinToString()}:${term.lowercase()}"
        cache[key]?.takeIf { System.currentTimeMillis() - it.time < 300_000 }?.let { return it.events }
        val result = if (identity != null) vertex(term, identity) else null
        val ranked = result ?: fallback(term, identity, urls)
        if (cache.size >= 50) cache.clear()
        cache[key] = Cached(System.currentTimeMillis(), ranked)
        return ranked
    }
    private suspend fun fallback(term: String, identity: String?, urls: List<String>): List<Nip01Event> = coroutineScope {
        val candidates = metadata(collect(listOf(JSONObject().put("kinds", JSONArray().put(0)).put("search", term).put("limit", 200)),
            urls + listOf("wss://purplepag.es", "wss://relay.vertexlab.io"))).take(200)
        if (candidates.isEmpty()) return@coroutineScope emptyList()
        val friends = async {
            if (identity == null) emptySet() else collect(listOf(JSONObject().put("kinds", JSONArray().put(3)).put("authors", JSONArray().put(identity)).put("limit", 1)), generalRelays, 5000)
                .maxByOrNull { it.createdAt }?.tags?.filter { it.size > 1 && it[0] == "p" }?.map { it[1] }?.toSet().orEmpty()
        }
        val checks = ConcurrentHashMap<String, Boolean>()
        val verification = launch {
            withTimeoutOrNull(8000) {
                val slots = Semaphore(8)
                candidates.take(50).map { event -> async { slots.withPermit {
                    val n5 = profileFields(event).nip05
                    if (n5.isNotBlank()) checks[event.pubkey] = nip05(n5) == event.pubkey
                } } }.awaitAll()
            }
        }
        val zaps = async {
            val filters = candidates.take(50).flatMap { event -> listOf(
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
            return profileMatchScore(term, event) + verificationScore + (if (event.pubkey in nuts) 150 else if (event.pubkey in senders) 40 else 0) + if (event.pubkey in followed) 50 else 0
        }
        candidates.sortedWith(compareByDescending<Nip01Event> { score(it) }.thenByDescending { it.pubkey in followed }
            .thenBy { profileFields(it).let { f -> f.display.ifBlank { f.name }.lowercase() } })
    }
    suspend fun resolve(raw: String, identity: String?, urls: List<String>): String {
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
