package org.dergigi.ants

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

val imagePattern = Regex("https://[^\\s<>\"]+\\.(?:png|jpe?g|gif|webp|avif)(?:\\?[^\\s<>\"]*)?", RegexOption.IGNORE_CASE)

data class SearchBranch(val filter: JSONObject, val media: String? = null, val site: String? = null, val renderedOnly: Boolean = false,
    val relayHints: List<String> = emptyList(), val outboxAuthors: List<String> = emptyList()) {
    fun accepts(event: Nip01Event): Boolean {
        if (renderedOnly && !event.isRenderable()) return false
        fun values(key: String): List<String> = filter.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        if (values("ids").isNotEmpty() && event.id !in values("ids")) return false
        if (values("authors").isNotEmpty() && event.pubkey !in values("authors")) return false
        if (values("kinds").isNotEmpty() && event.kind.toString() !in values("kinds")) return false
        if (filter.has("since") && event.createdAt < filter.getLong("since")) return false
        if (filter.has("until") && event.createdAt > filter.getLong("until")) return false
        for (key in filter.keys()) if (key.startsWith("#") && event.tags.none { it.size > 1 && it[0] == key.drop(1) && it[1] in values(key) }) return false
        if (media == "image" && !imagePattern.containsMatchIn(event.content) && event.tagValue("image") == null) return false
        if (media == "video" && eventVideos(event).isEmpty()) return false
        if (site != null && !event.content.contains(site, ignoreCase = true)) return false
        return true
    }
}

class SearchQuery(private val currentPubkey: String? = null, private val resolveProfile: suspend (String) -> String) {
    suspend fun parse(input: String, now: Instant = Instant.now()): List<SearchBranch> {
        val direct = input.trim().removePrefix("nostr:")
        require(direct.isNotBlank()) { "Enter a search first." }
        require(input.length <= 2000) { "Please keep searches under 2,000 characters." }
        val f = JSONObject().put("limit", 100)
        if (direct.none { it.isWhitespace() || it in "()\"" }) when {
            direct.startsWith("note1") -> return listOf(SearchBranch(f.put("ids", JSONArray().put(Nip19.noteDecode(direct)))))
            direct.startsWith("nevent1") -> {
                val pointer = Nip19.neventDecode(direct)
                return listOf(SearchBranch(f.put("ids", JSONArray().put(pointer.eventId)), relayHints = pointer.relays, outboxAuthors = listOfNotNull(pointer.author)))
            }
            direct.startsWith("naddr1") -> {
                val p = Nip19.naddrDecode(direct)
                return listOf(SearchBranch(f.put("authors", JSONArray().put(p.pubkey)).put("kinds", JSONArray().put(p.kind)).put("#d", JSONArray().put(p.identifier)), relayHints = p.relays))
            }
            !direct.contains(' ') && (direct.startsWith("npub1") || direct.startsWith("nprofile1")) -> return listOf(SearchBranch(f.put("kinds", JSONArray().put(0)).put("search", Nip19.npubEncode(resolve(direct)))))
            direct.matches(Regex("[0-9a-fA-F]{64}")) -> return listOf(SearchBranch(f.put("ids", JSONArray().put(direct.lowercase()))))
        }
        // Validate every branch before starting any network-dependent identity lookup.
        val plans = queryLeaves(input).map { compileQueryBranch(it, now) }
        val resolved = mutableMapOf<String, String>()
        suspend fun key(value: String): String = resolved[value] ?: resolve(value).also { resolved[value] = it }
        return plans.map { plan ->
            var authors: Set<String>? = null
            for (clause in plan.authors) {
                val next = clause.map { key(it) }.toSet()
                authors = authors?.intersect(next) ?: next
                require(authors.isNotEmpty()) { "Contradictory author filters. Use OR for alternatives." }
            }
            authors?.let { plan.filter.put("authors", JSONArray(it.toList())) }
            plan.mentions?.let { clause -> plan.filter.put("#p", JSONArray(clause.map { key(it) }.distinct())) }
            SearchBranch(plan.filter)
        }
    }

    private suspend fun resolve(raw: String): String {
        if (raw.equals("@me", ignoreCase = true)) return currentPubkey ?: error("Use /login before searching with @me.")
        require(!raw.equals("@contacts", true)) { "@contacts searches aren't supported on Android yet." }
        return resolveProfile(raw)
    }
}

internal data class QueryPlan(val filter: JSONObject, val authors: List<List<String>>, val mentions: List<String>?)

internal fun compileQueryBranch(leaves: List<QueryNode>, now: Instant): QueryPlan {
    val filter = JSONObject().put("limit", 100)
    val text = mutableListOf<String>()
    val authors = mutableListOf<List<String>>()
    var mentions: List<String>? = null
    var profile: String? = null
    val extensions = linkedMapOf<String, String>()
    fun values(n: QueryNode): List<String> {
        val values = n.value.split(',').map(String::trim)
        if (values.any(String::isEmpty)) n.fail("List values cannot be empty")
        return values.distinct()
    }
    fun intersect(key: String, values: List<Any>, n: QueryNode) {
        val previous = filter.optJSONArray(key)
        val result = if (previous == null) values else values.filter { value -> (0 until previous.length()).any { previous.get(it) == value } }
        if (result.isEmpty()) n.fail("Contradictory filters; use OR for alternatives")
        filter.put(key, JSONArray(result))
    }
    for (n in leaves) {
        val value = n.value
        if (n.field.isNotEmpty() && value.isBlank()) n.fail("${n.field}: needs a value")
        when (n.field) {
            "" -> if (!n.quoted && value.startsWith('#') && value.length > 1) {
                val tag = value.drop(1).lowercase()
                if (filter.has("#t") && filter.getJSONArray("#t").getString(0) != tag) n.fail("Use OR between hashtags")
                filter.put("#t", JSONArray().put(tag))
            } else text.add(if (n.quoted) JSONObject.quote(value) else value)
            "kind" -> intersect("kinds", values(n).map {
                if (!it.matches(Regex("[0-9]+")) || it.toIntOrNull() !in 0..65535) n.fail("kind: needs integers from 0 to 65535")
                it.toInt()
            }, n)
            "by", "from" -> authors.add(values(n))
            "mentions" -> { if (mentions != null) n.fail("Use one mentions: list or OR between mentions filters"); mentions = values(n) }
            "since", "until" -> {
                val date = try { searchDateTimestamp(value, n.field, now) } catch (e: IllegalArgumentException) { n.fail(e.message.orEmpty()) }
                val old = if (filter.has(n.field)) filter.getLong(n.field) else date
                filter.put(n.field, if (n.field == "since") maxOf(old, date) else minOf(old, date))
            }
            "a", "license" -> intersect("#${n.field}", listOf(value), n)
            "p" -> { if (profile != null) n.fail("Use OR between profile searches"); profile = value; intersect("kinds", listOf(0), n) }
            else -> {
                val valid = value.none(Char::isWhitespace) && when (n.field) {
                    "include" -> value == "spam"
                    "language" -> value.matches(Regex("[a-zA-Z]{2}"))
                    "sentiment" -> value in listOf("negative", "neutral", "positive")
                    "nsfw" -> value in listOf("true", "false")
                    "domain" -> true
                    else -> false
                }
                if (!valid) n.fail("Invalid ${n.field}: value")
                if (extensions[n.field]?.let { it != value } == true) n.fail("Conflicting ${n.field}: values")
                extensions[n.field] = value
            }
        }
    }
    if (filter.has("since") && filter.has("until") && filter.getLong("since") > filter.getLong("until")) leaves.first().fail("since: must not be after until:")
    if (profile != null && extensions.isNotEmpty()) leaves.first().fail("NIP-50 extensions cannot be combined with p:")
    val search = (listOfNotNull(profile) + text + extensions.map { "${it.key}:${it.value}" }).joinToString(" ")
    if (search.isNotBlank()) filter.put("search", search)
    return QueryPlan(filter, authors, mentions)
}
