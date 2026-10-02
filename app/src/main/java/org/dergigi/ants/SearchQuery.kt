package org.dergigi.ants

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

val imagePattern = Regex("https://[^\\s<>\"]+\\.(?:png|jpe?g|gif|webp|avif)(?:\\?[^\\s<>\"]*)?", RegexOption.IGNORE_CASE)

data class SearchBranch(val filter: JSONObject, val media: String? = null, val site: String? = null, val renderedOnly: Boolean = false) {
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
    suspend fun parse(input: String): List<SearchBranch> {
        val query = input.trim().removePrefix("nostr:")
        require(query.isNotBlank()) { "Enter a search first." }
        require(query.length <= 2000) { "Please keep searches under 2,000 characters." }
        if ((query.startsWith("https://") || query.startsWith("http://")) && query.none { it.isWhitespace() }) {
            return listOf(SearchBranch(JSONObject().put("limit", 100).put("search", query)))
        }
        require(!query.contains('(') && !query.contains(')')) { "Grouped searches aren't supported yet. Use separate searches joined with OR." }
        val parts = splitOr(query)
        require(parts.size <= 8) { "Use at most eight OR branches." }
        val now = Instant.now()
        return parts.map { branch(it, now) }
    }

    private fun splitOr(query: String): List<String> {
        val tokens = Regex("\"[^\"]*\"|\\S+").findAll(query).map { it.value }.toList()
        require(query.count { it == '"' } % 2 == 0) { "Close the quoted phrase." }
        val out = mutableListOf<String>()
        val current = mutableListOf<String>()
        for (token in tokens) {
            if (token.equals("OR", true)) {
                require(current.isNotEmpty()) { "Add a search on both sides of OR." }
                out.add(current.joinToString(" ")); current.clear()
            } else current.add(token)
        }
        require(current.isNotEmpty()) { "Add a search after OR." }
        out.add(current.joinToString(" "))
        return out
    }

    private suspend fun branch(query: String, now: Instant): SearchBranch {
        val f = JSONObject().put("limit", 100)
        val direct = query.removePrefix("nostr:")
        when {
            direct.startsWith("note1") -> return SearchBranch(f.put("ids", JSONArray().put(Nip19.noteDecode(direct))))
            direct.startsWith("nevent1") -> return SearchBranch(f.put("ids", JSONArray().put(Nip19.neventDecode(direct).eventId)))
            direct.startsWith("naddr1") -> {
                val p = Nip19.naddrDecode(direct)
                return SearchBranch(f.put("authors", JSONArray().put(p.pubkey)).put("kinds", JSONArray().put(p.kind)).put("#d", JSONArray().put(p.identifier)))
            }
            !direct.contains(' ') && (direct.startsWith("npub1") || direct.startsWith("nprofile1")) -> return SearchBranch(f.put("authors", JSONArray().put(resolve(direct))))
            direct.matches(Regex("[0-9a-fA-F]{64}")) -> return SearchBranch(f.put("ids", JSONArray().put(direct.lowercase())))
        }
        val text = mutableListOf<String>()
        var media: String? = null
        var site: String? = null
        fun add(key: String, value: Any) { f.put(key, (f.optJSONArray(key) ?: JSONArray()).put(value)) }
        for (token in Regex("\"[^\"]*\"|\\S+").findAll(query).map { it.value }) {
            val prefix = token.substringBefore(':').lowercase()
            val value = token.substringAfter(':', "")
            when {
                token.startsWith('#') && token.length > 1 -> add("#t", token.drop(1).lowercase())
                prefix == "by" || prefix == "from" -> add("authors", resolve(value))
                prefix == "mentions" -> add("#p", resolve(value))
                prefix == "kind" -> {
                    val kind = value.toIntOrNull()
                    require(kind != null && kind in 0..65535) { "kind: needs a number from 0 to 65535." }
                    add("kinds", kind)
                }
                prefix == "is" -> (kindAliases[value.lowercase()] ?: error("Unknown kind '$value'. Use /kinds to see available shortcuts.")).forEach { add("kinds", it) }
                prefix == "since" || prefix == "until" -> {
                    f.put(prefix, searchDateTimestamp(value, prefix, now))
                }
                prefix == "has" -> {
                    require(value in listOf("image", "video")) { "Use has:image or has:video." }; media = value
                }
                prefix == "site" -> {
                    require(value.isNotBlank()) { "Add a domain after site:." }
                    site = when(value) { "yt" -> "youtube.com"; "gh" -> "github.com"; else -> value }
                    text.add(site)
                }
                prefix == "p" -> { require(value.isNotBlank()) { "Add a name after p:." }; add("kinds", 0); text.add(value) }
                token.contains(':') && !token.startsWith("https://") && !token.startsWith("http://") -> error("Unsupported modifier '$prefix:'. See Search help for this version's syntax.")
                else -> text.add(token)
            }
        }
        if (text.isNotEmpty()) f.put("search", text.joinToString(" "))
        if (f.has("since") && f.has("until")) require(f.getLong("since") <= f.getLong("until")) { "since: must come before until:." }
        return SearchBranch(f, media, site)
    }

    private suspend fun resolve(raw: String): String {
        if (raw.equals("@me", ignoreCase = true)) return currentPubkey ?: error("Use /login before searching with @me.")
        return resolveProfile(raw)
    }
}
