package org.dergigi.ants

import android.app.Application
import android.net.Uri
import coil3.SingletonImageLoader
import java.util.UUID
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val name: String, val about: String, val picture: String?, val timestamp: Long)
data class SearchState(
    val command: String? = null, val commandMessage: String? = null, val commandBusy: Boolean = false,
    val pubkey: String? = null, val signerRequest: String? = null,
    val query: String = "", val submitted: String = "", val searched: Boolean = false,
    val loading: Boolean = false, val error: String? = null,
    val reactionTargets: Map<String, Nip01Event> = emptyMap(), val loadingReactionTargets: Boolean = false,
    val pageId: Long = 0, val backDepth: Int = 0,
    val scrollIndex: Int = 0, val scrollOffset: Int = 0,
    val followingNewest: Boolean = true, val pendingEvents: List<Nip01Event> = emptyList(),
    val detail: Nip01Event? = null, val detailScroll: Int = 0, val detailRaw: Boolean = false,
    val events: List<Nip01Event> = emptyList(), val profiles: Map<String, Profile> = emptyMap(),
    val statuses: Map<String, String> = emptyMap(), val history: List<String> = emptyList(),
    val saved: List<String> = emptyList(), val relays: List<String> = defaultSearchRelays,
)

class SearchModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("ants", 0)
    private val relay = RelaySearch()
    private val mutable = MutableStateFlow(SearchState(pubkey = preferences.getString("pubkey", null)?.let(Nip19::normalizePubkey), history = load("history"), saved = load("saved"), relays = load("relays").ifEmpty { defaultSearchRelays }))
    val state = mutable.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0
    private var loginAttempt: String? = null
    private var nextPageId = 0L
    private val backStack = ArrayDeque<SearchState>()

    fun rememberScroll(pageId: Long, index: Int, offset: Int) {
        mutable.update { if (it.pageId == pageId) it.copy(scrollIndex = index, scrollOffset = offset) else it }
    }
    fun pauseFollowing(pageId: Long) {
        mutable.update { if (it.pageId == pageId && it.followingNewest) it.copy(followingNewest = false) else it }
    }
    fun followNewest() { mutable.update {
        it.copy(followingNewest = true, events = (it.events + it.pendingEvents).distinctBy { e -> e.id }.sortedByDescending { e -> e.createdAt }.take(500), pendingEvents = emptyList(), scrollIndex = 0, scrollOffset = 0)
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
            profiles = current.profiles, saved = current.saved, history = current.history,
            relays = current.relays, pubkey = current.pubkey, signerRequest = null, commandBusy = false, backDepth = backStack.size,
            commandMessage = if (previous.command == "login") null else previous.commandMessage,
        )
    }
    private fun rememberPage() {
        val page = state.value
        backStack.addLast(page.copy(
            query = if (page.searched) page.submitted else page.query,
            followingNewest = false, signerRequest = null,
            profiles = emptyMap(), saved = emptyList(), history = emptyList(), relays = emptyList(),
        ))
        // Bound retained results: history is a session convenience, not a disk cache.
        fun retainedChars() = backStack.sumOf { page -> (page.events + page.pendingEvents + page.reactionTargets.values).sumOf { event ->
            event.content.length.toLong() + event.tags.sumOf { row -> row.sumOf { it.length.toLong() } }
        } }
        while (backStack.size > 20 || (backStack.size > 1 && retainedChars() > 8_000_000)) backStack.removeFirst()
    }
    private fun load(key: String): List<String> = runCatching {
        val a = JSONArray(preferences.getString(key, "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
    private fun persist(key: String, values: List<String>) { preferences.edit().putString(key, JSONArray(values).toString()).apply() }
    private fun containsSecret(value: String) = Regex("(?i)\\b(?:nsec1|ncryptsec1)[a-z0-9]*").containsMatchIn(value)
    private fun rejectSecret() { mutable.update { it.copy(query = "", error = "Private keys aren't accepted. Use /login with an external signer.") } }
    fun edit(value: String) {
        if (containsSecret(value)) { rejectSecret(); return }
        mutable.update { it.copy(query = value, error = null) }
    }
    fun clearHistory() { persist("history", emptyList()); mutable.update { it.copy(history = emptyList()) } }
    fun toggleSaved(query: String) {
        val value = query.trim(); if (value.isEmpty()) return
        val saved = state.value.saved.let { if (value in it) it - value else (listOf(value) + it).take(100) }
        persist("saved", saved); mutable.update { it.copy(saved = saved) }
    }
    fun setRelays(text: String): String? {
        val urls = text.lines().map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()
        if (urls.isEmpty() || urls.size > 12) return "Add between 1 and 12 relay URLs."
        if (urls.any { url -> runCatching { val uri = Uri.parse(url); uri.scheme != "wss" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null }.getOrDefault(true) }) return "Use secure wss:// relay URLs, one per line."
        persist("relays", urls); mutable.update { it.copy(relays = urls) }; return null
    }
    fun stop() { generation++; searchJob?.cancel(); mutable.update { it.copy(loading = false, commandBusy = false, loadingReactionTargets = false, statuses = it.statuses.mapValues { (_, v) -> if (v in listOf("Connecting", "Searching")) "Stopped" else v }) } }
    fun home() {
        stop()
        backStack.clear()
        mutable.update { it.copy(command = null, commandMessage = null, commandBusy = false, query = "", submitted = "", searched = false, error = null, events = emptyList(), reactionTargets = emptyMap(), loadingReactionTargets = false, statuses = emptyMap(), pageId = ++nextPageId, followingNewest = true, pendingEvents = emptyList(), backDepth = 0, scrollIndex = 0, scrollOffset = 0, detail = null, detailScroll = 0, detailRaw = false) }
    }
    fun search(query: String = state.value.query) {
        val raw = query.trim(); if (raw.isBlank()) return
        if (containsSecret(raw)) { rejectSecret(); return }
        val input = if (raw.startsWith('/')) "/" + raw.drop(1).trim().lowercase() else raw
        val command = if (input.startsWith('/')) input.drop(1) else null
        stop()
        if (input != state.value.submitted || state.value.detail != null) rememberPage()
        val current = ++generation
        mutable.update { it.copy(command = command, commandMessage = null, commandBusy = false, query = input, submitted = input, searched = true, loading = command == null || command == "tutorial", error = null, events = emptyList(), reactionTargets = emptyMap(), loadingReactionTargets = false, statuses = emptyMap(), pageId = ++nextPageId, followingNewest = true, pendingEvents = emptyList(), backDepth = backStack.size, scrollIndex = 0, scrollOffset = 0, detail = null, detailScroll = 0, detailRaw = false) }
        searchJob = viewModelScope.launch {
            try {
                if (command != null && command != "tutorial") {
                    runCommand(command, current)
                    return@launch
                }
                val identity = state.value.pubkey
                val branches = withContext(Dispatchers.IO) { SearchQuery(relay.http, identity).parse(if (command == "tutorial") tutorialPointer else input) }
                if (current != generation) return@launch
                val history = (listOf(input) + state.value.history.filter { it != input }).take(20)
                persist("history", history); mutable.update { it.copy(history = history) }
                val urls = if (branches.any { it.filter.has("search") }) state.value.relays else (state.value.relays + generalRelays).distinct()
                relay.search(branches, urls).flowOn(Dispatchers.IO).collect { update ->
                    if (current != generation) return@collect
                    when (update) {
                        is RelayUpdate.Status -> mutable.update { it.copy(statuses = it.statuses + (update.relay to update.text)) }
                        is RelayUpdate.Event -> {
                            val event = update.event
                            if (event.kind == 0) updateProfile(event)
                            mutable.update {
                                if (it.followingNewest) it.copy(events = (it.events + event).distinctBy { e -> e.id }.sortedByDescending { e -> e.createdAt }.take(500))
                                else if (it.events.any { e -> e.id == event.id }) it
                                else it.copy(pendingEvents = (it.pendingEvents + event).distinctBy { e -> e.id }.take(500))
                            }
                        }
                    }
                }
                if (current != generation) return@launch
                mutable.update { it.copy(loading = false) }
                val targetIds = (state.value.events + state.value.pendingEvents).mapNotNull(::reactionTargetId).distinct().take(100)
                if (targetIds.isNotEmpty()) {
                    mutable.update { it.copy(loadingReactionTargets = true) }
                    val filter = JSONObject().put("ids", JSONArray(targetIds)).put("limit", targetIds.size)
                    relay.search(listOf(SearchBranch(filter)), (state.value.relays + generalRelays).distinct(), 7000).flowOn(Dispatchers.IO).collect { update ->
                        if (current == generation && update is RelayUpdate.Event) mutable.update { it.copy(reactionTargets = it.reactionTargets + (update.event.id to update.event)) }
                    }
                    if (current != generation) return@launch
                    mutable.update { it.copy(loadingReactionTargets = false) }
                }
                val authors = (state.value.events + state.value.pendingEvents + state.value.reactionTargets.values).flatMap { listOfNotNull(it.pubkey, highlightAuthor(it)) }.distinct().filter { it !in state.value.profiles }.take(200)
                if (authors.isNotEmpty()) {
                    val filter = JSONObject().put("kinds", JSONArray().put(0)).put("authors", JSONArray(authors)).put("limit", authors.size)
                    relay.search(listOf(SearchBranch(filter)), listOf("wss://purplepag.es", "wss://relay.damus.io"), 7000).flowOn(Dispatchers.IO).collect {
                        if (current == generation && it is RelayUpdate.Event) updateProfile(it.event)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current == generation) mutable.update { it.copy(loading = false, loadingReactionTargets = false, error = e.message ?: "Search failed. Check your connection and try again.") } }
        }
    }
    private suspend fun runCommand(command: String, current: Int) {
        when (command) {
            "help", "examples", "kinds" -> Unit
            "login" -> requestLogin()
            "logout" -> {
                loginAttempt = null
                preferences.edit().remove("pubkey").remove("signerPackage").apply()
                mutable.update { it.copy(pubkey = null, signerRequest = null, commandMessage = "Logged out. Your saved searches and settings are unchanged.") }
            }
            "clear" -> {
                backStack.clear()
                mutable.update { it.copy(backDepth = 0, profiles = emptyMap(), commandBusy = true, commandMessage = "Clearing cached results, profiles, and images…") }
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
                    if (current == generation) mutable.update { it.copy(commandBusy = false, commandMessage = "Caches cleared. Your account, saved searches, history, settings, and downloaded pictures are unchanged.") }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (current == generation) mutable.update { it.copy(commandBusy = false, commandMessage = "Some caches could not be cleared. Try /clear again.") } }
            }
            else -> mutable.update { it.copy(commandMessage = "Unknown command /$command. Choose a command below.") }
        }
    }

    fun requestLogin() {
        if (state.value.pubkey != null) {
            mutable.update { it.copy(commandMessage = "Already connected. Use /logout to switch accounts.") }; return
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
        preferences.edit().putString("pubkey", key).putString("signerPackage", validPackage).apply()
        mutable.update { it.copy(pubkey = key, signerRequest = null, commandBusy = false, commandMessage = "Connected. You can now search by:@me and mentions:@me.") }
    }

    private fun updateProfile(event: Nip01Event) {
        runCatching {
            val json = JSONObject(event.content)
            val profile = Profile(json.optString("display_name").ifBlank { json.optString("name") }.ifBlank { event.pubkey.take(12) }, json.optString("about"), json.optString("picture").takeIf { it.startsWith("https://") }, event.createdAt)
            mutable.update { state -> if ((state.profiles[event.pubkey]?.timestamp ?: -1) > event.createdAt) state else state.copy(profiles = (state.profiles + (event.pubkey to profile)).entries.toList().takeLast(1000).associate { it.toPair() }) }
        }
    }
    override fun onCleared() { relay.http.dispatcher.cancelAll(); relay.http.connectionPool.evictAll() }
}

fun incomingQuery(value: String): String {
    val uri = Uri.parse(value)
    if (uri.host?.lowercase() in listOf("ants.sh", "www.ants.sh", "search.dergigi.com")) {
        uri.getQueryParameter("q")?.let { return it }
        val parts = uri.pathSegments
        if (parts.size >= 2) return when (parts[0]) { "p" -> "by:${parts[1]}"; "e" -> parts[1]; "t" -> parts[1].split(',', '+', ' ').filter { it.isNotBlank() }.joinToString(" OR ") { "#$it" }; else -> value }
    }
    return value.removePrefix("nostr:")
}
