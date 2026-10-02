package org.dergigi.ants

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*

internal data class SearchLink(val start: Int, val end: Int, val query: String, val url: String? = null)
private val hexId = Regex("[0-9a-fA-F]{64}")
private val pointer = Regex("(?i)(?<![a-z0-9])(?:nostr:|@)?(?:npub1|nprofile1|note1|nevent1|naddr1)[a-z0-9]+")
private val hashtag = Regex("(?<![\\p{L}\\p{N}_/#])#[\\p{L}\\p{N}_]+")
private val legacyMention = Regex("#\\[(\\d+)\\]")

internal fun pointerQuery(raw: String): String? = runCatching {
    val value = raw.removePrefix("nostr:").removePrefix("NOSTR:").removePrefix("@")
    when {
        value.startsWith("npub1", true) -> "by:${Nip19.npubEncode(Nip19.npubDecode(value))}"
        value.startsWith("nprofile1", true) -> "by:${Nip19.npubEncode(Nip19.nprofileDecode(value).pubkey)}"
        value.startsWith("note1", true) -> Nip19.noteEncode(Nip19.noteDecode(value))
        value.startsWith("nevent1", true) -> { Nip19.neventDecode(value); value.lowercase() }
        value.startsWith("naddr1", true) -> { Nip19.naddrDecode(value); value.lowercase() }
        else -> null
    }
}.getOrNull()

internal fun addressQuery(value: String): String? = runCatching {
    val parts = value.split(':', limit = 3)
    require(parts.size == 3)
    val kind = parts[0].toInt(); require(kind in 0..65535)
    val author = Nip19.normalizePubkey(parts[1]) ?: return null
    Nip19.naddrEncode(NaddrPointer(parts[2], author, kind))
}.getOrNull()

internal fun referenceQuery(type: String, value: String): String? = when (type) {
    "p" -> Nip19.normalizePubkey(value)?.let { "by:${Nip19.npubEncode(it)}" }
    "e", "q" -> if (hexId.matches(value)) Nip19.noteEncode(value.lowercase()) else pointerQuery(value) ?: addressQuery(value)
    "a" -> addressQuery(value)
    else -> null
}

internal fun urlQuery(url: String): String {
    val uri = Uri.parse(url)
    if (uri.host?.lowercase() in listOf("ants.sh", "www.ants.sh", "search.dergigi.com")) return incomingQuery(url)
    if (uri.host?.lowercase() in listOf("njump.me", "njump.to", "nostr.com", "primal.net")) {
        uri.pathSegments.lastOrNull()?.let { pointerQuery(it)?.let { query -> return query } }
    }
    return url
}

internal fun contentLinks(content: String, event: Nip01Event): List<SearchLink> {
    val links = mutableListOf<SearchLink>()
    val urls = webLinks(content)
    // URL fragments and path hashtags must not become separate hashtag searches.
    fun overlaps(start: Int, end: Int) = urls.any { start < it.end && end > it.start } || links.any { start < it.end && end > it.start }
    urls.forEach { links.add(SearchLink(it.start, it.end, urlQuery(it.text), url = it.text)) }
    pointer.findAll(content).forEach { match ->
        if (!overlaps(match.range.first, match.range.last + 1)) pointerQuery(match.value)?.let { links.add(SearchLink(match.range.first, match.range.last + 1, it)) }
    }
    legacyMention.findAll(content).forEach { match ->
        if (!overlaps(match.range.first, match.range.last + 1)) {
            val tag = match.groupValues[1].toIntOrNull()?.let { event.tags.getOrNull(it) }
            if (tag != null && tag.size >= 2) referenceQuery(tag[0], tag[1])?.let { links.add(SearchLink(match.range.first, match.range.last + 1, it)) }
        }
    }
    hashtag.findAll(content).forEach { match ->
        if (!overlaps(match.range.first, match.range.last + 1)) links.add(SearchLink(match.range.first, match.range.last + 1, match.value))
    }
    return links.sortedBy { it.start }
}

internal fun linkedProfileKeys(event: Nip01Event): List<String> =
    contentLinks(event.content + "\n" + event.tagValue("comment").orEmpty(), event)
        .mapNotNull { profileKey(it.query) }.distinct().take(100)

private fun profileKey(query: String): String? =
    if (query.startsWith("by:")) Nip19.normalizePubkey(query.removePrefix("by:")) else null

internal data class LinkedContent(val text: AnnotatedString, val ranges: List<IntRange>, private val starts: IntArray, private val ends: IntArray) {
    fun mapRange(start: Int, end: Int): IntRange? {
        val a = starts[start.coerceIn(starts.indices)]
        val b = ends[end.coerceIn(ends.indices)]
        return if (a < b) a until b else null
    }
}

internal fun linkedContent(text: String, event: Nip01Event, profiles: Map<String, Profile>, ranges: List<IntRange> = emptyList(), protectedRanges: List<IntRange> = emptyList(), onNavigate: (String) -> Unit): LinkedContent {
    // Track both edges of replaced mentions so highlight spans still align
    // when a long identifier becomes a short display name.
    val starts = IntArray(text.length + 1)
    val ends = IntArray(text.length + 1)
    val result = buildAnnotatedString {
        val style = TextLinkStyles(style = SpanStyle(color = Color(0xFF60A5FA)))
        var cursor = 0
        fun appendPlain(end: Int) {
            for (i in cursor until end) { starts[i] = length; ends[i] = length; append(text[i]) }
            cursor = end
        }
        contentLinks(text, event).filter { link -> protectedRanges.none { link.start <= it.last && link.end > it.first } }.forEach { link ->
            appendPlain(link.start)
            val key = profileKey(link.query)
            val label = key?.let {
                val name = profiles[it]?.name?.replace(Regex("[\\p{Cc}\\p{Cf}]"), "")?.trim()?.take(60)
                "@" + (name?.takeIf(String::isNotBlank) ?: Nip19.npubEncode(it).let { npub -> npub.take(12) + "…" + npub.takeLast(6) })
            } ?: text.substring(link.start, link.end)
            val begin = length
            withLink(LinkAnnotation.Clickable(link.url ?: link.query, style) { onNavigate(link.url ?: link.query) }) { append(label) }
            for (i in link.start until link.end) {
                starts[i] = if (key == null) begin + i - link.start else begin
                ends[i] = if (key == null) begin + i - link.start else if (i == link.start) begin else length
            }
            ends[link.end] = length
            cursor = link.end
        }
        appendPlain(text.length)
        starts[text.length] = length; ends[text.length] = length
    }
    return LinkedContent(result, ranges.mapNotNull { range ->
        val start = starts[range.first.coerceIn(0, text.length)]
        val end = ends[(range.last + 1).coerceIn(0, text.length)]
        if (start < end) start until end else null
    }, starts, ends)
}

internal fun linkedText(text: String, event: Nip01Event, profiles: Map<String, Profile> = emptyMap(), onNavigate: (String) -> Unit): AnnotatedString =
    linkedContent(text, event, profiles, onNavigate = onNavigate).text

internal fun quotedQueries(event: Nip01Event): List<String> = event.tags.mapNotNull { tag ->
    if (tag.size < 2) null
    else when {
        tag[0] == "q" -> referenceQuery("q", tag[1])
        tag[0] == "e" && tag.getOrNull(3) == "mention" -> referenceQuery("e", tag[1])
        else -> null
    }
}.distinct().take(8)
