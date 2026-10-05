package org.dergigi.ants

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class IdentityClaim(val pubkey: String, val nip05: String)
internal data class FilteredResults(val pageId: Long = -1, val events: List<Nip01Event> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
internal class ResultFilterController(
    state: StateFlow<SearchState>,
    scope: CoroutineScope,
    private val resolver: ProfileResolver,
) {
    private val mutableSettings = MutableStateFlow(ContentFilterSettings())
    val settings = mutableSettings.asStateFlow()
    private val verified = MutableStateFlow<Map<IdentityClaim, Boolean>>(emptyMap())
    private val factsCache = object : LinkedHashMap<String, ContentFacts>(600, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ContentFacts>?): Boolean = size > 600
    }
    private data class Input(val pageId: Long, val events: List<Nip01Event>, val profiles: Map<String, Profile>,
        val query: String, val command: String?, val newestFirst: Boolean, val settings: ContentFilterSettings,
        val verified: Map<IdentityClaim, Boolean>)

    // Filter a retained result set off the UI thread; never mutate or discard fetched events.
    val results = combine(state, settings, verified) { state, settings, verified ->
        Input(state.pageId, state.events, state.profiles, state.submitted, state.command, state.newestFirst, settings, verified)
    }.distinctUntilChanged().mapLatest { input ->
        withContext(Dispatchers.Default) {
            val settings = input.settings
            if (input.command != null) return@withContext FilteredResults(input.pageId, input.events)
            val readable = if (settings.mode != ResultFilterMode.NEVER && settings.hideEncrypted)
                input.events.filterNot { it.encryptedContent } else input.events
            if (!settings.enabled(input.events.size)) return@withContext FilteredResults(input.pageId, readable)
            val emojiDisabled = settings.emojiAutoDisabled(input.query)
            val fuzzy = settings.resultFilter.trim().takeIf { settings.fuzzyEnabled && it.isNotEmpty() }?.let(::ResultFuzzyFilter)
            val matches = readable.mapNotNull { event ->
                ensureActive()
                val profile = input.profiles[event.pubkey]
                val isVerified = profile?.let { input.verified[IdentityClaim(event.pubkey, it.nip05)] == true } ?: false
                val facts = factsCache.getOrPut(event.id) { ContentAnalysis.facts(event.content) }
                if (!ContentAnalysis.accepts(facts, profile, settings, emojiDisabled, isVerified)) null
                else if (fuzzy == null) event to 0.0 else fuzzy.score(event.content)?.let { event to it }
            }
            val events = if (fuzzy == null) matches.map { it.first } else {
                val profiles = matches.filter { it.first.kind == 0 }.sortedBy { it.second }.map { it.first }
                val notes = matches.filter { it.first.kind != 0 }.map { it.first }
                profiles + (if (input.newestFirst) notes.sortedByDescending { it.createdAt } else notes.sortedBy { it.createdAt })
            }
            FilteredResults(input.pageId, events)
        }
    }.stateIn(scope, SharingStarted.Eagerly, FilteredResults())

    init {
        scope.launch {
            combine(state, settings) { state, settings ->
                if (state.command != null || !settings.verifiedOnly || !settings.enabled(state.events.size)) emptyList()
                else state.events.take(50).mapNotNull { event ->
                    state.profiles[event.pubkey]?.nip05?.takeIf { it.isNotBlank() }?.let { IdentityClaim(event.pubkey, it) }
                }.distinct()
            }.distinctUntilChanged().collectLatest { claims ->
                // The web checks the first 50 results. Reuse Android's resolver cache, four at a time.
                for (batch in claims.filter { it !in verified.value }.chunked(4)) coroutineScope {
                    batch.map { claim -> async {
                        val valid = try { resolver.verifyNip05(claim.nip05, claim.pubkey) == true }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { false }
                        verified.update { (it + (claim to valid)).entries.toList().takeLast(500).associate { entry -> entry.toPair() } }
                    } }.awaitAll()
                }
            }
        }
    }

    fun update(settings: ContentFilterSettings) { mutableSettings.value = settings }
    fun clearVerification() { verified.value = emptyMap() }
}
