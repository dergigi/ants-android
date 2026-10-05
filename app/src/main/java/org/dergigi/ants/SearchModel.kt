package org.dergigi.ants

import android.app.Application
import android.net.Uri
import coil3.SingletonImageLoader
import java.util.UUID
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val name: String, val about: String, val picture: String?, val timestamp: Long,
    val nip05: String = "", val bot: Boolean = false)
data class SearchState(
    val command: String? = null, val commandMessage: String? = null, val commandBusy: Boolean = false,
    val pubkey: String? = null, val signerRequest: String? = null,
    val eventSignRequest: EventSignRequest? = null,
    val query: String = "", val submitted: String = "", val translation: String = "", val searched: Boolean = false,
    val loading: Boolean = false, val resolvingProfiles: Boolean = false, val resolvingNip05: Boolean = false, val rankedProfiles: Boolean = false, val error: String? = null,
    val profileFeedAuthor: String? = null, val newestFirst: Boolean = true,
    val loadingParents: Set<String> = emptySet(), val failedParents: Set<String> = emptySet(),
    val quotes: Map<String, Nip01Event> = emptyMap(), val loadingQuotes: Set<String> = emptySet(), val failedQuotes: Set<String> = emptySet(),
    val reactionTargets: Map<String, Nip01Event> = emptyMap(), val loadingReactionTargets: Boolean = false,
    val pageId: Long = 0, val backDepth: Int = 0,
    val scrollIndex: Int = 0, val scrollOffset: Int = 0,
    val followingNewest: Boolean = true, val newerResultIds: Set<String> = emptySet(),
    val detail: Nip01Event? = null, val detailScroll: Int = 0, val detailRaw: Boolean = false,
    val events: List<Nip01Event> = emptyList(), val profiles: Map<String, Profile> = emptyMap(),
    val statuses: Map<String, String> = emptyMap(), val history: List<String> = emptyList(),
    val relays: List<String> = defaultSearchRelays,
)

class SearchModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("ants", 0)
    private val relay = RelaySearch()
    private val outbox = OutboxRouter(relay)
    private val mutable = MutableStateFlow(SearchState(pubkey = preferences.getString("pubkey", null)?.let(Nip19::normalizePubkey), history = load("history"), relays = load("relays").ifEmpty { defaultSearchRelays }))
    val state = mutable.asStateFlow()
    private var searchJob: Job? = null
    private val parentJobs = mutableMapOf<String, Job>()
    private val quoteJobs = mutableMapOf<String, Job>()
    private val quoteSlots = Semaphore(3)
    private var mentionJob: Job? = null
    private val pendingMentions = linkedSetOf<String>()
    private val requestedMentions = mutableSetOf<String>()
    private var generation = 0
    private var loginAttempt: String? = null
    private var signingWaiter: CompletableDeferred<Nip01Event?>? = null
    private var pendingSignature: EventSignRequest? = null
    private var activeSigningId: String? = null

    private val profileSigningMutex = Mutex()
    // Only signature acquisition is serialized; Vertex HTTP requests can overlap.
    private suspend fun signProfileRequest(event: Nip01Event): Nip01Event? =
        profileSigningMutex.withLock { signProfileRequestSerial(event) }

    private suspend fun signProfileRequestSerial(event: Nip01Event): Nip01Event? {
        val packageName = preferences.getString("signerPackage", null) ?: return null
        if (state.value.pubkey != event.pubkey) return null
        // Use previously granted signer permission without opening another app.
        val background = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.query(
                    Uri.parse("content://$packageName.SIGN_EVENT"), arrayOf(event.toJsonString(), "", event.pubkey), null, null, null,
                )?.use { cursor ->
                    if (cursor.getColumnIndex("rejected") >= 0) return@withContext false to null
                    if (!cursor.moveToFirst()) return@use null
                    fun value(name: String) = cursor.getColumnIndex(name).takeIf { it >= 0 }?.let { cursor.getString(it) }
                    true to validatedSignedEvent(event, value("event"), value("result") ?: value("signature"))
                }
            }.getOrNull()
        }
        if (background != null) return background.second
        return withContext(Dispatchers.Main.immediate) {
            if (activeSigningId != null || signingWaiter != null || state.value.pubkey != event.pubkey) return@withContext null
            val request = EventSignRequest(UUID.randomUUID().toString(), event, packageName)
            val waiter = CompletableDeferred<Nip01Event?>()
            pendingSignature = request; signingWaiter = waiter
            mutable.update { it.copy(eventSignRequest = request) }
            try { withTimeoutOrNull(120_000) { waiter.await() } }
            finally {
                if (pendingSignature?.id == request.id) {
                    pendingSignature = null; signingWaiter = null
                    mutable.update { it.copy(eventSignRequest = null) }
                }
            }
        }
    }
    fun eventSigningLaunched(id: String) {
        activeSigningId = id
        mutable.update { if (it.eventSignRequest?.id == id) it.copy(eventSignRequest = null) else it }
    }
    fun finishEventSigning(id: String?, eventJson: String?, signature: String?, rejected: Boolean) {
        if (id == activeSigningId) activeSigningId = null
        val request = pendingSignature ?: return
        if (request.id != id) return
        val waiter = signingWaiter ?: return
        viewModelScope.launch {
            val result = if (rejected) null else withContext(Dispatchers.Default) {
                validatedSignedEvent(request.event, eventJson, signature)
            }
            if (pendingSignature?.id == request.id && state.value.pubkey == request.event.pubkey) waiter.complete(result)
        }
    }
    private val profileResolver = ProfileResolver(relay, ::signProfileRequest, outbox)
    internal val resultFilters = ResultFilterController(state, viewModelScope, profileResolver,
        ResultLanguageAnalyzer(FastTextLanguage(app)::predict))
    private var nextPageId = 0L
    private val backStack = ArrayDeque<SearchState>()

    private var accountProfileJob: Job? = null

    init {
        // Retire device-only saved searches, including data from older versions.
        if (preferences.contains("saved")) preferences.edit().remove("saved").apply()
        refreshAccountProfile()
    }

    private fun refreshAccountProfile() {
        accountProfileJob?.cancel()
        val key = state.value.pubkey ?: return
        accountProfileJob = viewModelScope.launch {
            try {
                val filter = JSONObject().put("kinds", JSONArray().put(0)).put("authors", JSONArray().put(key)).put("limit", 1)
                outbox.search(listOf(SearchBranch(filter)), listOf("wss://purplepag.es", "wss://relay.damus.io"), 7000)
                    .flowOn(Dispatchers.IO).collect { update ->
                        if (state.value.pubkey == key && update is RelayUpdate.Event) updateProfile(update.event)
                    }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The account stays usable with an avatar fallback when offline. */ }
        }
    }

    fun rememberScroll(pageId: Long, index: Int, offset: Int) {
        mutable.update { if (it.pageId == pageId) it.copy(scrollIndex = index, scrollOffset = offset) else it }
    }
    fun pauseFollowing(pageId: Long) {
        mutable.update { if (it.pageId == pageId && it.followingNewest) it.copy(followingNewest = false) else it }
    }
    fun followNewest() { mutable.update {
        it.copy(followingNewest = true, newerResultIds = emptySet(), scrollIndex = 0, scrollOffset = 0)
    } }
    fun toggleSort() { mutable.update {
        val newestFirst = !it.newestFirst
        it.copy(newestFirst = newestFirst, events = orderedResults(it.events, newestFirst, it.profileFeedAuthor),
            followingNewest = true, newerResultIds = emptySet(), scrollIndex = 0, scrollOffset = 0)
    } }
    fun openDetail(event: Nip01Event) { mutable.update { it.copy(detail = event, detailScroll = 0, detailRaw = false) } }
    fun dismissDetail() { mutable.update { it.copy(detail = null, detailScroll = 0, detailRaw = false) } }
    fun rememberDetailScroll(pageId: Long, eventId: String, scroll: Int) {
        mutable.update { if (it.pageId == pageId && it.detail?.id == eventId) it.copy(detailScroll = scroll) else it }
    }
    fun toggleDetailRaw() { mutable.update { it.copy(detailRaw = !it.detailRaw) } }
    fun back() {
        if (state.value.detail != null) { dismissDetail(); return }
        stop()
        val previous = backStack.removeLastOrNull()
        if (previous == null) { home(); return }
        val current = state.value
        mutable.value = previous.copy(
            profiles = current.profiles, history = current.history,
            relays = current.relays, pubkey = current.pubkey, signerRequest = null, eventSignRequest = null, commandBusy = false, backDepth = backStack.size,
            commandMessage = if (previous.command == "login") null else previous.commandMessage,
            loadingParents = emptySet(), loadingQuotes = emptySet(),
        )
    }
    private fun rememberPage() {
        val page = state.value
        backStack.addLast(page.copy(
            query = if (page.searched) page.submitted else page.query,
            followingNewest = false, signerRequest = null, eventSignRequest = null,
            profiles = emptyMap(), history = emptyList(), relays = emptyList(),
        ))
        // Bound retained results: history is a session convenience, not a disk cache.
        fun retainedBytes() = backStack.sumOf { page ->
            (page.events + page.reactionTargets.values + page.quotes.values + listOfNotNull(page.detail))
                .distinctBy { it.id }.sumOf { it.retainedBytes }
        }
        while (backStack.size > 10 || (backStack.isNotEmpty() && retainedBytes() > HISTORY_MEMORY_BUDGET)) backStack.removeFirst()
    }
    private fun load(key: String): List<String> = runCatching {
        val a = JSONArray(preferences.getString(key, "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
    private fun persist(key: String, values: List<String>) { preferences.edit().putString(key, JSONArray(values).toString()).apply() }
    private fun containsSecret(value: String) = containsPrivateNostrKey(value)
    private fun rejectSecret() { mutable.update { it.copy(query = "", error = "Private keys aren't accepted. Use /login with an external signer.") } }
    fun edit(value: String) {
        if (containsSecret(value)) { rejectSecret(); return }
        mutable.update { it.copy(query = nostrIdentifierFromUrl(value) ?: value, error = null) }
    }
    fun clearHistory() { persist("history", emptyList()); mutable.update { it.copy(history = emptyList()) } }
    fun setRelays(text: String): String? {
        val urls = text.lines().map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()
        if (urls.isEmpty() || urls.size > 12) return "Add between 1 and 12 relay URLs."
        if (urls.any { url -> runCatching { val uri = Uri.parse(url); uri.scheme != "wss" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null }.getOrDefault(true) }) return "Use secure wss:// relay URLs, one per line."
        persist("relays", urls); mutable.update { it.copy(relays = urls) }; return null
    }
    fun stop() { generation++; mentionJob?.cancel(); mentionJob = null; pendingMentions.clear(); requestedMentions.clear(); signingWaiter?.cancel(); pendingSignature = null; signingWaiter = null; mutable.update { it.copy(eventSignRequest = null) }; searchJob?.cancel(); parentJobs.values.forEach { it.cancel() }; parentJobs.clear(); quoteJobs.values.forEach { it.cancel() }; quoteJobs.clear(); mutable.update { it.copy(loadingParents = emptySet(), failedQuotes = it.failedQuotes + it.loadingQuotes, loadingQuotes = emptySet()) }; mutable.update { it.copy(loading = false, resolvingProfiles = false, resolvingNip05 = false, commandBusy = false, loadingReactionTargets = false, statuses = it.statuses.mapValues { (_, v) -> if (v in listOf("Connecting", "Searching")) "Stopped" else v }) } }
    fun home() {
        stop()
        backStack.clear()
        mutable.update { it.copy(command = null, commandMessage = null, commandBusy = false, query = "", submitted = "", translation = "", searched = false, error = null, profileFeedAuthor = null, newestFirst = true, events = emptyList(), reactionTargets = emptyMap(), quotes = emptyMap(), loadingQuotes = emptySet(), failedQuotes = emptySet(), loadingParents = emptySet(), failedParents = emptySet(), loadingReactionTargets = false, statuses = emptyMap(), pageId = ++nextPageId, followingNewest = true, newerResultIds = emptySet(), backDepth = 0, scrollIndex = 0, scrollOffset = 0, detail = null, detailScroll = 0, detailRaw = false) }
    }
    fun search(query: String = state.value.query) {
        if (containsSecret(query)) { rejectSecret(); return }
        val raw = incomingQuery(query.trim()); if (raw.isBlank()) return
        if (containsSecret(raw)) { rejectSecret(); return }
        val input = if (raw.startsWith('/')) "/" + raw.drop(1).trim().lowercase() else raw
        val languageResults = resultFilters.results.value
        val languageSettings = resultFilters.settings.value
        val languageSelection = languageSettings.languages.forQuery(input)
        val relayLanguages = if (input == state.value.submitted && languageResults.pageId == state.value.pageId &&
            languageSettings.mode != ResultFilterMode.NEVER && languageSelection.excluded.isNotEmpty() &&
            languageResults.languageCounts.keys.count { it != UNKNOWN_LANGUAGE } > 1)
            languageResults.languageCounts.keys.filter { it != UNKNOWN_LANGUAGE && it !in languageSelection.excluded }.toSet()
        else emptySet()
        val newestFirst = if (input == state.value.submitted) state.value.newestFirst else true
        val command = if (input.startsWith('/')) input.drop(1) else null
        stop()
        if (input != state.value.submitted || state.value.detail != null) rememberPage()
        val current = ++generation
        val queryTime = java.time.Instant.now()
        val preview = queryPreview(input, state.value.pubkey, queryTime)
        mutable.update { it.copy(command = command, commandMessage = null, commandBusy = false, query = input, submitted = input, translation = preview, searched = true, loading = command == null || command == "tutorial", resolvingProfiles = false, resolvingNip05 = false, rankedProfiles = false, error = null, profileFeedAuthor = null, newestFirst = newestFirst, events = emptyList(), reactionTargets = emptyMap(), quotes = emptyMap(), loadingQuotes = emptySet(), failedQuotes = emptySet(), loadingParents = emptySet(), failedParents = emptySet(), loadingReactionTargets = false, statuses = emptyMap(), pageId = ++nextPageId, followingNewest = true, newerResultIds = emptySet(), backDepth = backStack.size, scrollIndex = 0, scrollOffset = 0, detail = null, detailScroll = 0, detailRaw = false) }
        searchJob = viewModelScope.launch {
            try {
                if (command != null && command != "tutorial") {
                    runCommand(command, current)
                    return@launch
                }
                val identity = state.value.pubkey
                val nip05Progress = Nip05LookupProgress { active ->
                    mutable.update { if (current == generation) it.copy(resolvingNip05 = active) else it }
                }
                val resolved = mutableMapOf<String, String>()
                suspend fun showProfileLookup() = withContext(Dispatchers.Main.immediate) {
                    if (current == generation) mutable.update { it.copy(resolvingProfiles = true) }
                }
                val parsed = withContext(Dispatchers.IO + nip05Progress) {
                    SearchQuery(identity, resolveContacts = {
                        showProfileLookup()
                        profileResolver.contacts(identity, state.value.relays)
                    }) { name ->
                        showProfileLookup()
                        profileResolver.resolve(name, identity, state.value.relays).also { key ->
                            withContext(Dispatchers.Main.immediate) {
                                if (current == generation && command == null) {
                                    resolved[name] = key
                                    mutable.update { it.copy(translation = queryPreview(input, identity, queryTime, resolved)) }
                                }
                            }
                        }
                    }.parse(if (command == "tutorial") tutorialPointer else input, queryTime)
                }
                if (current != generation) return@launch
                mutable.update { it.copy(resolvingProfiles = false, resolvingNip05 = false, translation = if (command != null) input else parsed.joinToString("\nOR ") { branch -> branch.queryTranslation() }) }
                val branches = parsed.mapNotNull { it.forRenderedResults() }
                require(branches.isNotEmpty()) { "No valid event kinds to search. Use kind:0 through kind:65535." }
                if (current != generation) return@launch
                val history = (listOf(input) + state.value.history.filter { it != input }).take(20)
                persist("history", history); mutable.update { it.copy(history = history) }
                val urls = if (branches.any { it.filter.has("search") }) state.value.relays else (state.value.relays + generalRelays).distinct()
                val profileBranches = branches.filter { it.filter.has("search") && it.filter.optJSONArray("kinds")?.let { kinds -> kinds.length() == 1 && kinds.optInt(0) == 0 } == true }
                val ordinaryBranches = branches - profileBranches.toSet()
                val profileOnly = branches.all { branch ->
                    branch.filter.optJSONArray("kinds")?.let { it.length() == 1 && it.optInt(0) == 0 } == true
                }
                if (profileBranches.isNotEmpty()) mutable.update { it.copy(resolvingProfiles = true) }
                for (branch in profileBranches) {
                    val profiles = withContext(Dispatchers.IO + nip05Progress) { profileResolver.search(branch.filter.getString("search"), identity, urls).filter(branch::accepts) }
                    if (current != generation) return@launch
                    updateProfiles(profiles)
                    mutable.update { it.copy(events = boundedEvents(latestProfileEvents(it.events + profiles)), rankedProfiles = ordinaryBranches.isEmpty()) }
                }
                mutable.update { it.copy(resolvingProfiles = false, resolvingNip05 = false) }
                if (ordinaryBranches.isNotEmpty()) outbox.searchPlan(languageSearchPlan(ordinaryBranches, relayLanguages), state.value.relays).batched().flowOn(Dispatchers.IO).collect { updates ->
                    if (current != generation) return@collect
                    receiveSearchUpdates(updates, current, profileOnly)
                }
                if (current != generation) return@launch
                // Wait for the profile search to finish: a transient first match
                // must not turn a multi-profile search into someone's feed.
                val singleProfile = state.value.events.singleOrNull()?.takeIf { profileOnly && it.kind == 0 }
                if (singleProfile != null) {
                    mutable.update { it.copy(profileFeedAuthor = singleProfile.pubkey, rankedProfiles = false, statuses = emptyMap()) }
                    val feed = SearchBranch(JSONObject()
                        .put("authors", JSONArray().put(singleProfile.pubkey))
                        .put("kinds", JSONArray(defaultSearchKinds.filter { it != 0 }))
                        .put("limit", 500), renderedOnly = true)
                    outbox.search(listOf(feed), (state.value.relays + generalRelays).distinct())
                        .batched().flowOn(Dispatchers.IO).collect { updates ->
                            receiveSearchUpdates(updates, current)
                        }
                }
                if (current != generation) return@launch
                mutable.update { it.copy(loading = false, resolvingProfiles = false, resolvingNip05 = false) }
                val targetIds = state.value.events.mapNotNull(::reactionTargetId).distinct().take(100)
                if (targetIds.isNotEmpty()) {
                    mutable.update { it.copy(loadingReactionTargets = true) }
                    val filter = JSONObject().put("ids", JSONArray(targetIds)).put("limit", targetIds.size)
                    outbox.search(listOf(SearchBranch(filter, renderedOnly = true)), (state.value.relays + generalRelays).distinct(), 7000).flowOn(Dispatchers.IO).collect { update ->
                        if (current == generation && update is RelayUpdate.Event) mutable.update { it.copy(reactionTargets = retainContextEvent(it.reactionTargets, update.event.id, update.event)) }
                    }
                    if (current != generation) return@launch
                    mutable.update { it.copy(loadingReactionTargets = false) }
                }
                val snapshot = state.value
                val authors = withContext(Dispatchers.Default) {
                    // Fetch result authors first so bridge/bot filters cover the entire retained result set.
                    (snapshot.events.asSequence().map { it.pubkey } +
                        (snapshot.events + snapshot.reactionTargets.values).asSequence()
                            .flatMap { listOfNotNull(it.pubkey, highlightAuthor(it)) + linkedProfileKeys(it) })
                        .distinct().filter { it !in snapshot.profiles }.take(700).toList()
                }
                if (current != generation) return@launch
                if (authors.isNotEmpty()) {
                    val filter = JSONObject().put("kinds", JSONArray().put(0)).put("authors", JSONArray(authors)).put("limit", authors.size)
                    outbox.search(listOf(SearchBranch(filter)), listOf("wss://purplepag.es", "wss://relay.damus.io"), 7000).batched().flowOn(Dispatchers.IO).collect { updates ->
                        if (current == generation) updateProfiles(updates.filterIsInstance<RelayUpdate.Event>().map { it.event })
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current == generation) mutable.update { it.copy(loading = false, resolvingProfiles = false, resolvingNip05 = false, loadingReactionTargets = false, error = e.message ?: "Search failed. Check your connection and try again.") } }
        }
    }
    private fun latestProfileEvents(events: List<Nip01Event>): List<Nip01Event> =
        events.groupBy { it.pubkey }.values.map { versions -> versions.maxWith(compareBy<Nip01Event> { it.createdAt }.thenBy { it.id }) }

    private suspend fun receiveSearchUpdates(updates: List<RelayUpdate>, current: Int, profileOnly: Boolean = false) {
        if (current != generation) return
        val pageId = state.value.pageId
        val events = updates.filterIsInstance<RelayUpdate.Event>().map { it.event }
        val statuses = updates.filterIsInstance<RelayUpdate.Status>().associate { it.relay to it.text }
        updateProfiles(events.filter { it.kind == 0 })
        withContext(Dispatchers.Default) {
            mutable.update { previous ->
                if (previous.pageId != pageId || current != generation) previous
                else {
                    val shown = previous.events.map { it.id }.toHashSet()
                    val candidates = (previous.events + events).distinctBy { it.id }
                    val merged = when {
                        previous.profileFeedAuthor != null -> {
                            val profile = previous.events.firstOrNull { it.kind == 0 && it.pubkey == previous.profileFeedAuthor }
                            listOfNotNull(profile) + candidates.filter { it.kind != 0 && it.pubkey == previous.profileFeedAuthor }
                                .sortedByDescending { it.createdAt }.take(500)
                        }
                        profileOnly -> latestProfileEvents(candidates).sortedByDescending { it.createdAt }.take(500)
                        else -> candidates.sortedByDescending { it.createdAt }.take(500)
                    }
                    val bounded = boundedEvents(merged, count = if (previous.profileFeedAuthor == null) 500 else 501)
                    val retained = bounded.map { it.id }.toHashSet()
                    // Stable keys preserve reading position as new items arrive.
                    previous.copy(events = if (profileOnly) bounded else orderedResults(bounded, previous.newestFirst, previous.profileFeedAuthor), newerResultIds = if (previous.followingNewest) emptySet()
                        else (previous.newerResultIds + events.map { it.id }.filterNot(shown::contains)).intersect(retained),
                        statuses = previous.statuses + statuses)
                }
            }
        }
    }

    private suspend fun runCommand(command: String, current: Int) {
        when (command) {
            "help", "examples", "kinds", "history" -> Unit
            "login" -> requestLogin()
            "logout" -> {
                profileResolver.clear(); outbox.clear(); ProfileIndicatorLookup.clear()
                loginAttempt = null
                accountProfileJob?.cancel()
                preferences.edit().remove("pubkey").remove("signerPackage").apply()
                mutable.update { it.copy(pubkey = null, signerRequest = null, commandMessage = "Logged out.") }
            }
            "clear" -> {
                profileResolver.clear(); outbox.clear(); ProfileIndicatorLookup.clear()
                resultFilters.clearVerification()
                backStack.clear()
                mutable.update { it.copy(backDepth = 0, profiles = emptyMap(), commandBusy = true, commandMessage = "Clearing cache…") }
                try {
                    val app = getApplication<Application>()
                    val loader = SingletonImageLoader.get(app)
                    loader.memoryCache?.clear()
                    withContext(Dispatchers.IO) {
                        loader.diskCache?.clear()
                        relay.http.cache?.evictAll()
                        val shared = app.cacheDir.resolve("shared-images")
                        check(!shared.exists() || shared.deleteRecursively()) { "Some temporary images could not be cleared." }
                    }
                    if (current == generation) mutable.update { it.copy(commandBusy = false, commandMessage = "Cache cleared.") }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (current == generation) mutable.update { it.copy(commandBusy = false, commandMessage = "Some caches could not be cleared. Try /clear again.") } }
            }
            else -> mutable.update { it.copy(commandMessage = "Unknown command /$command.") }
        }
    }

    fun requestLogin() {
        if (state.value.pubkey != null) {
            mutable.update { it.copy(commandMessage = "Connected.") }; return
        }
        if (loginAttempt != null) return
        val request = UUID.randomUUID().toString()
        loginAttempt = request
        mutable.update { it.copy(signerRequest = request, commandBusy = true, commandMessage = "Approve the connection in your Android signer.") }
    }
    fun signerRequestLaunched(id: String) {
        mutable.update { if (it.signerRequest == id) it.copy(signerRequest = null) else it }
    }
    fun finishLogin(value: String?, packageName: String?, responseId: String?, error: String? = null) {
        val attempt = loginAttempt ?: return
        if (responseId != null && responseId != attempt) return
        loginAttempt = null
        val key = value?.let(Nip19::normalizePubkey)
        val validPackage = packageName?.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) }
        if (error != null || key == null || validPackage == null) {
            mutable.update { it.copy(signerRequest = null, commandBusy = false, commandMessage = error ?: "The signer returned an invalid account. Please try again.") }
            return
        }
        profileResolver.clear(); outbox.clear(); ProfileIndicatorLookup.clear()
        preferences.edit().putString("pubkey", key).putString("signerPackage", validPackage).apply()
        mutable.update { it.copy(pubkey = key, signerRequest = null, commandBusy = false, commandMessage = "Connected.") }
        refreshAccountProfile()
    }

    internal fun loadMentionProfiles(keys: List<String>) {
        val missing = keys.mapNotNull(Nip19::normalizePubkey)
            .filter { it !in state.value.profiles && it !in requestedMentions }
            .distinct().take((500 - requestedMentions.size).coerceAtLeast(0))
        requestedMentions.addAll(missing)
        pendingMentions.addAll(missing)
        if (pendingMentions.isEmpty() || mentionJob?.isActive == true) return
        val current = generation
        mentionJob = viewModelScope.launch {
            try {
                delay(100) // Coalesce cards entering the viewport into one metadata request.
                while (pendingMentions.isNotEmpty() && current == generation) {
                    val authors = pendingMentions.take(30)
                    pendingMentions.removeAll(authors.toSet())
                    val filter = JSONObject().put("kinds", JSONArray().put(0)).put("authors", JSONArray(authors)).put("limit", authors.size)
                    outbox.search(listOf(SearchBranch(filter)), (listOf("wss://purplepag.es") + generalRelays).distinct(), 7000)
                        .batched().flowOn(Dispatchers.IO).collect { updates ->
                            if (current == generation) updateProfiles(updates.filterIsInstance<RelayUpdate.Event>().map { it.event })
                        }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the clickable key fallback if metadata is unavailable. */ }
            finally { if (current == generation) mentionJob = null }
        }
    }

    internal fun loadQuote(reference: QuoteReference) {
        val key = reference.key
        if (key in quoteJobs || key in state.value.quotes) return
        val current = generation
        if (state.value.quotes.size + quoteJobs.size >= 100) {
            mutable.update { it.copy(failedQuotes = it.failedQuotes + key) }; return
        }
        mutable.update { it.copy(loadingQuotes = it.loadingQuotes + key, failedQuotes = it.failedQuotes - key) }
        quoteJobs[key] = viewModelScope.launch {
            try {
                quoteSlots.withPermit {
                    val cached = withContext(Dispatchers.Default) {
                        val snapshot = state.value
                        (snapshot.events + snapshot.reactionTargets.values + snapshot.quotes.values)
                            .filter(reference::matches).maxByOrNull { it.createdAt }
                    }
                    var found = cached
                    if (found == null) {
                        val hints = reference.relays.filter { url -> runCatching {
                            val uri = Uri.parse(url)
                            uri.scheme == "wss" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
                        }.getOrDefault(false) }.take(2)
                        val branch = SearchBranch(reference.filter, relayHints = hints, outboxAuthors = reference.authors).forRenderedResults()
                        if (branch != null) outbox.search(listOf(branch), state.value.relays, 7000)
                            .flowOn(Dispatchers.IO).collect { update ->
                                if (update is RelayUpdate.Event && (found == null || update.event.createdAt > found!!.createdAt)) found = update.event
                            }
                    }
                    if (current != generation) return@withPermit
                    val event = found ?: return@withPermit
                    mutable.update { it.copy(quotes = retainContextEvent(it.quotes, key, event)) }
                    val authors = withContext(Dispatchers.Default) { (listOfNotNull(event.pubkey, highlightAuthor(event)) + linkedProfileKeys(event)).distinct().take(20) }
                    val filter = JSONObject().put("kinds", JSONArray().put(0)).put("authors", JSONArray(authors)).put("limit", authors.size)
                    outbox.search(listOf(SearchBranch(filter)), listOf("wss://purplepag.es", "wss://relay.damus.io"), 5000)
                        .batched().flowOn(Dispatchers.IO).collect { updates ->
                            if (current == generation) updateProfiles(updates.filterIsInstance<RelayUpdate.Event>().map { it.event })
                        }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep an explicit retry action for unavailable quotes. */ }
            finally {
                if (current == generation) {
                    quoteJobs.remove(key)
                    mutable.update { it.copy(loadingQuotes = it.loadingQuotes - key,
                        failedQuotes = if (key in it.quotes) it.failedQuotes - key else it.failedQuotes + key) }
                }
            }
        }
    }

    fun loadParent(id: String) {
        if (!id.matches(Regex("[0-9a-f]{64}")) || id in parentJobs || id in state.value.reactionTargets) return
        val cached = state.value.events.firstOrNull { it.id == id }
        if (cached != null) {
            mutable.update { it.copy(reactionTargets = retainContextEvent(it.reactionTargets, id, cached)) }
            return
        }
        val current = generation
        mutable.update { it.copy(loadingParents = it.loadingParents + id, failedParents = it.failedParents - id) }
        parentJobs[id] = viewModelScope.launch {
            try {
                val filter = JSONObject().put("ids", JSONArray().put(id)).put("limit", 1)
                outbox.search(listOf(SearchBranch(filter, renderedOnly = true)), (state.value.relays + generalRelays).distinct(), 8000)
                    .flowOn(Dispatchers.IO).collect { update ->
                        if (current == generation && update is RelayUpdate.Event) mutable.update {
                            it.copy(reactionTargets = retainContextEvent(it.reactionTargets, id, update.event))
                        }
                    }
                val parent = state.value.reactionTargets[id]
                val authors = withContext(Dispatchers.Default) { parent?.let { (listOfNotNull(it.pubkey, highlightAuthor(it)) + linkedProfileKeys(it)).distinct().filter { key -> key !in state.value.profiles } }.orEmpty() }
                if (current == generation && authors.isNotEmpty()) {
                    val profileFilter = JSONObject().put("kinds", JSONArray().put(0)).put("authors", JSONArray(authors)).put("limit", authors.size)
                    outbox.search(listOf(SearchBranch(profileFilter)), listOf("wss://purplepag.es", "wss://relay.damus.io"), 7000)
                        .flowOn(Dispatchers.IO).collect { if (current == generation && it is RelayUpdate.Event) updateProfile(it.event) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Render a retry action for an unavailable parent. */ }
            finally {
                if (current == generation) {
                    parentJobs.remove(id)
                    mutable.update { it.copy(loadingParents = it.loadingParents - id,
                        failedParents = if (id in it.reactionTargets) it.failedParents - id else it.failedParents + id) }
                }
            }
        }
    }

    private suspend fun updateProfile(event: Nip01Event) = updateProfiles(listOf(event))
    private suspend fun updateProfiles(events: List<Nip01Event>) {
        if (events.isEmpty()) return
        val pageId = state.value.pageId
        withContext(Dispatchers.Default) {
            val profiles = events.mapNotNull { event -> runCatching {
                val json = JSONObject(event.content)
                val fields = profileFields(event)
                event.pubkey to Profile(fields.display.ifBlank { fields.name }.ifBlank { event.pubkey.take(12) }.take(256), json.optString("about").take(4000), json.optString("picture").takeIf { it.startsWith("https://") && it.length <= 2048 }, event.createdAt,
                    nip05 = fields.nip05, bot = json.opt("bot") == true || json.opt("is_bot") == true || ContentAnalysis.hasBotHint(json.optString("about")))
            }.getOrNull() }
            mutable.update { state ->
                if (state.pageId != pageId) state else {
                    val merged = state.profiles.toMutableMap()
                    for ((key, profile) in profiles) if ((merged[key]?.timestamp ?: -1) <= profile.timestamp) merged[key] = profile
                    state.copy(profiles = merged.entries.toList().takeLast(1000).associate { it.toPair() })
                }
            }
        }
    }
    override fun onCleared() {
        super.onCleared()
        NetworkCleanup.close(relay.http)
    }
}

fun incomingQuery(value: String): String {
    if (containsPrivateNostrKey(value)) return value
    nostrIdentifierFromUrl(value)?.let { return it }
    val uri = Uri.parse(value)
    if (uri.host?.lowercase() in listOf("ants.sh", "www.ants.sh", "search.dergigi.com")) {
        uri.getQueryParameter("q")?.let { return it }
        val parts = uri.pathSegments
        if (parts.size >= 2) return when (parts[0]) { "p" -> "by:${parts[1]}"; "e" -> parts[1]; "t" -> parts[1].split(',', '+', ' ').filter { it.isNotBlank() }.joinToString(" OR ") { "#$it" }; else -> value }
    }
    return value.removePrefix("nostr:")
}
