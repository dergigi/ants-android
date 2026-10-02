package org.dergigi.ants

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset

// Keep ordinary discovery aligned with web ants; explicit kinds and event IDs
// remain unrestricted, so reactions and other event kinds are still searchable.
private val defaultContentKinds = listOf(1, 20, 21, 22, 9802, 39089)
private fun SearchBranch.withDefaultKinds(): SearchBranch {
    if (!filter.has("kinds") && !filter.has("ids")) filter.put("kinds", JSONArray(defaultContentKinds))
    return this
}

private val kinds = mapOf("note" to 1, "notes" to 1, "profile" to 0, "article" to 30023, "highlight" to 9802, "code" to 1337, "reaction" to 7, "repost" to 6, "zap" to 9735, "picture" to 20, "video" to 21)
val imagePattern = Regex("https://[^\\s<>\"]+\\.(?:png|jpe?g|gif|webp|avif)(?:\\?[^\\s<>\"]*)?", RegexOption.IGNORE_CASE)
private val videoPattern = Regex("https?://[^\\s]+\\.(?:mp4|webm|mov)", RegexOption.IGNORE_CASE)

data class SearchBranch(val filter: JSONObject, val media: String? = null, val site: String? = null) {
    fun accepts(event: Nip01Event): Boolean {
        fun values(key: String): List<String> = filter.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        if (values("ids").isNotEmpty() && event.id !in values("ids")) return false
        if (values("authors").isNotEmpty() && event.pubkey !in values("authors")) return false
        if (values("kinds").isNotEmpty() && event.kind.toString() !in values("kinds")) return false
        if (filter.has("since") && event.createdAt < filter.getLong("since")) return false
        if (filter.has("until") && event.createdAt > filter.getLong("until")) return false
        for (key in filter.keys()) if (key.startsWith("#") && event.tags.none { it.size > 1 && it[0] == key.drop(1) && it[1] in values(key) }) return false
        if (media == "image" && !imagePattern.containsMatchIn(event.content) && event.tagValue("image") == null) return false
        if (media == "video" && !videoPattern.containsMatchIn(event.content)) return false
        if (site != null && !event.content.contains(site, ignoreCase = true)) return false
        return true
    }
}

class SearchQuery(private val http: OkHttpClient) {
    fun parse(input: String): List<SearchBranch> {
        val query = input.trim().removePrefix("nostr:")
        require(query.isNotBlank()) { "Enter a search first." }
        require(query.length <= 2000) { "Please keep searches under 2,000 characters." }
        if ((query.startsWith("https://") || query.startsWith("http://")) && query.none { it.isWhitespace() }) {
            return listOf(SearchBranch(JSONObject().put("limit", 100).put("search", query)).withDefaultKinds())
        }
        require(!query.contains('(') && !query.contains(')')) { "Grouped searches aren't supported yet. Use separate searches joined with OR." }
        val parts = splitOr(query)
        require(parts.size <= 8) { "Use at most eight OR branches." }
        return parts.map { branch(it).withDefaultKinds() }
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

    private fun branch(query: String): SearchBranch {
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
                prefix == "is" -> add("kinds", kinds[value.lowercase()] ?: error("Unknown kind '$value'. Try is:note, is:article, is:highlight, or kind:123."))
                prefix == "since" || prefix == "until" -> {
                    val date = runCatching { LocalDate.parse(value) }.getOrElse { error("Use $prefix:YYYY-MM-DD.") }
                    f.put(prefix, date.atStartOfDay().toEpochSecond(ZoneOffset.UTC) + if (prefix == "until") 86399 else 0)
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

    private fun resolve(raw: String): String {
        val value = raw.removePrefix("nostr:").removePrefix("@")
        Nip19.normalizePubkey(value)?.let { return it }
        val identifier = when (value.lowercase()) { "dergigi" -> "_@dergigi.com"; "fiatjaf" -> "_@fiatjaf.com"; else -> value }
        val name = if ('@' in identifier) identifier.substringBefore('@') else "_"
        val domain = identifier.substringAfter('@', identifier)
        require(domain.contains('.') && !domain.contains('/') && name.isNotBlank()) { "Use an npub, hex key, or NIP-05 address with by: / mentions:." }
        val url = "https://$domain/.well-known/nostr.json".toHttpUrl().newBuilder().addQueryParameter("name", name).build()
        return http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            require(response.isSuccessful) { "Couldn't resolve $identifier (HTTP ${response.code})." }
            val body = response.body ?: error("Empty NIP-05 response.")
            require(body.contentLength() <= 1_000_000) { "NIP-05 response too large." }
            val content = body.source().readUtf8( minOf(1_000_001L, body.source().apply { request(1_000_001) }.buffer.size))
            require(content.length <= 1_000_000) { "NIP-05 response too large." }
            val key = JSONObject(content).getJSONObject("names").optString(name)
            Nip19.normalizePubkey(key) ?: error("No NIP-05 key found for $identifier.")
        }
    }
}
