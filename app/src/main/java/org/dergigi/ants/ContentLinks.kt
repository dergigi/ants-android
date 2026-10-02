package org.dergigi.ants

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*

internal data class SearchLink(val start: Int, val end: Int, val query: String)
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
    urls.forEach { links.add(SearchLink(it.start, it.end, urlQuery(it.text))) }
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

internal fun linkedText(text: String, event: Nip01Event, onNavigate: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    append(text)
    val style = TextLinkStyles(style = SpanStyle(color = Color(0xFF60A5FA)))
    contentLinks(text, event).forEach { link ->
        addLink(LinkAnnotation.Clickable(link.query, style) { onNavigate(link.query) }, link.start, link.end)
    }
}

internal fun quotedQueries(event: Nip01Event): List<String> = event.tags.mapNotNull { tag ->
    if (tag.size < 2) null
    else when {
        tag[0] == "q" -> referenceQuery("q", tag[1])
        tag[0] == "e" && tag.getOrNull(3) == "mention" -> referenceQuery("e", tag[1])
        else -> null
    }
}.distinct().take(8)
