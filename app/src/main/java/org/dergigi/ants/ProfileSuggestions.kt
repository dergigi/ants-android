package org.dergigi.ants

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class SuggestedProfile(val pubkey: String, val profile: Profile, val username: String = "")

internal fun matchingProfiles(term: String, profiles: Collection<SuggestedProfile>, contacts: Set<String>): List<SuggestedProfile> {
    val query = term.removePrefix("@").lowercase(Locale.ROOT)
    fun score(person: SuggestedProfile): Int {
        val names = listOf(person.profile.name, person.username, person.profile.nip05).map { it.lowercase(Locale.ROOT) }
        return when {
            names.any { it == query } -> 3
            names.any { it.startsWith(query) } -> 2
            names.any { query in it } -> 1
            else -> 0
        }
    }
    return profiles.distinctBy { it.pubkey }.filter { query.isNotEmpty() && score(it) > 0 }
        .sortedWith(compareByDescending<SuggestedProfile> { it.pubkey in contacts }
            .thenByDescending { score(it) }.thenBy { it.profile.name.lowercase(Locale.ROOT) }.thenBy { it.pubkey }).take(8)
}

/** Autocomplete deliberately avoids signed Vertex requests and per-person verification probes. */
internal class ProfileSuggestions(
    private val relay: RelaySearch,
    private val contacts: suspend (String, List<String>) -> List<String>,
) {
    private val cache = ProfileLookupCache<List<SuggestedProfile>>(40, lifetime = { if (it.isEmpty()) 30_000 else 300_000 },
        weight = { rows -> rows.sumOf { 256L + it.profile.name.length * 2 + it.profile.nip05.length * 2 + (it.profile.picture?.length ?: 0) * 2 } })
    private var knownContacts: Pair<String, Set<String>>? = null
    private var revision = 0
    private val known = LinkedHashMap<String, SuggestedProfile>()
    fun clear() { revision++; cache.clear(); knownContacts = null; known.clear() }

    fun suggestions(term: String, identity: String?, urls: List<String>, profiles: Map<String, Profile>) = flow {
        val epoch = revision
        val local = profiles.map { SuggestedProfile(it.key, it.value) }
        fun ranked(extra: List<SuggestedProfile> = emptyList()) = matchingProfiles(term,
            (extra + known.values + local).sortedByDescending { it.profile.timestamp }, knownContacts?.takeIf { it.first == identity }?.second.orEmpty())
        emit(ranked())
        if (term.removePrefix("@").length < 2 || term.length > 80 || term.startsWith("npub1") || term.startsWith("nprofile1")) return@flow
        delay(450)
        coroutineScope {
            val follows = async {
                if (identity == null) emptySet() else try { contacts(identity, urls).toSet() }
                catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet() }
            }
            val routes = (urls.take(2) + "wss://purplepag.es").distinct()
            suspend fun lookup(keys: Set<String>?): List<SuggestedProfile> = cache.get("$term:${if (keys == null) "global" else identity}:${routes.joinToString()}") {
                val filters = (keys?.toList()?.chunked(1000) ?: listOf(null)).map { batch ->
                    JSONObject().put("kinds", JSONArray().put(0)).put("search", term.removePrefix("@")).put("limit", 30).apply {
                        if (batch != null) put("authors", JSONArray(batch))
                    }
                }
                val events = mutableMapOf<String, Nip01Event>()
                relay.search(filters.map { SearchBranch(it) }, routes, 4000).collect { update ->
                    if (update is RelayUpdate.Event && update.event.createdAt > (events[update.event.pubkey]?.createdAt ?: -1)) events[update.event.pubkey] = update.event
                }
                events.values.mapNotNull { event -> runCatching {
                    val fields = profileFields(event)
                    val json = JSONObject(event.content)
                    SuggestedProfile(event.pubkey, Profile(fields.display.ifBlank { fields.name }.ifBlank { event.pubkey.take(12) }.take(256), "",
                        json.optString("picture").takeIf { it.startsWith("https://") && it.length <= 2048 }, event.createdAt, fields.nip05.take(256)), fields.name.take(256))
                }.getOrNull() }
            }
            val remote = lookup(null)
            if (epoch != revision) return@coroutineScope
            emit(ranked(remote))
            val keys = follows.await()
            if (epoch != revision) return@coroutineScope
            if (identity != null) knownContacts = identity to keys
            val contactMatches = if (keys.isNotEmpty()) lookup(keys) else emptyList()
            if (epoch != revision) return@coroutineScope
            val fetched = contactMatches + remote
            fetched.forEach { known[it.pubkey] = it }
            while (known.size > 500) known.remove(known.keys.first())
            emit(ranked(fetched))
        }
    }.catch { error -> if (error is CancellationException) throw error }
}
