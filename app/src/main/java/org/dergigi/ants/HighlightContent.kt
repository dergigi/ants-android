package org.dergigi.ants

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val highlightGold = Color(0xFFF6DE74)
private data class HighlightPassage(val text: String, val ranges: List<IntRange>)

// Retain original offsets while treating runs of whitespace like the web renderer.
private fun normalized(text: String): Pair<String, List<Int>> {
    val out = StringBuilder()
    val offsets = ArrayList<Int>()
    text.forEachIndexed { index, c ->
        if (!c.isWhitespace() || out.lastOrNull() != ' ') {
            out.append(if (c.isWhitespace()) ' ' else c); offsets.add(index)
        }
    }
    return out.toString() to offsets
}

private fun passage(event: Nip01Event, compact: Boolean): HighlightPassage {
    val content = event.content.trim()
    val context = event.tagValue("context").orEmpty()
    val (haystack, offsets) = normalized(context)
    val needle = normalized(content).first.trim()
    val ranges = mutableListOf<IntRange>()
    if (needle.isNotEmpty()) {
        var cursor = 0
        while (cursor < haystack.length) {
            val start = haystack.indexOf(needle, cursor)
            if (start < 0) break
            val end = offsets[start + needle.length - 1] + 1
            ranges.add(offsets[start] until end)
            cursor = start + needle.length
        }
    }
    // Never lose the actual highlight when a publisher supplied unrelated context.
    if (ranges.isEmpty()) {
        val text = if (compact && content.length > 1400) content.take(1400) + "…" else content
        return HighlightPassage(text, if (text.isEmpty()) emptyList() else listOf(0 until text.length))
    }
    if (!compact || context.length <= 1600) return HighlightPassage(context, ranges)
    val start = (ranges.first().first - 180).coerceAtLeast(0)
    val end = (start + 1600).coerceAtMost(context.length)
    val prefix = if (start > 0) "…" else ""
    val text = prefix + context.substring(start, end) + if (end < context.length) "…" else ""
    return HighlightPassage(text, ranges.mapNotNull {
        val a = maxOf(it.first, start); val b = minOf(it.last + 1, end)
        if (a < b) (a - start + prefix.length) until (b - start + prefix.length) else null
    })
}

internal fun highlightAuthor(event: Nip01Event): String? {
    if (event.kind != 9802) return null
    val author = event.tags.firstOrNull { it.firstOrNull() == "p" && it.getOrNull(3) == "author" }?.getOrNull(1)
        ?: event.tagValue("p") ?: event.tagValue("a")?.split(':', limit = 3)?.getOrNull(1)
    return author?.let(Nip19::normalizePubkey)
}

private data class HighlightSource(val label: String, val url: String)
private fun highlightSource(event: Nip01Event): HighlightSource? {
    event.tagValue("r")?.let { raw ->
        val uri = Uri.parse(raw)
        if (uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) return HighlightSource(uri.host!!.removePrefix("www."), raw)
    }
    event.tagValue("e")?.let { id ->
        if (id.matches(Regex("[0-9a-fA-F]{64}"))) return HighlightSource("nostr post", "https://njump.me/${Nip19.noteEncode(id)}")
    }
    return runCatching {
        val parts = event.tagValue("a")?.split(':', limit = 3) ?: return null
        val kind = parts[0].toInt()
        val author = Nip19.normalizePubkey(parts[1]) ?: return null
        val pointer = Nip19.naddrEncode(NaddrPointer(parts[2], author, kind))
        HighlightSource(if (kind == 30023) "blog post" else "nostr post", "https://njump.me/$pointer")
    }.getOrNull()
}

@Composable
internal fun HighlightContent(event: Nip01Event, profiles: Map<String, Profile>, compact: Boolean) {
    val passage = remember(event.id, compact) { passage(event, compact) }
    val styled = remember(passage) { buildAnnotatedString {
        append(passage.text.ifBlank { "Empty highlight" })
        passage.ranges.forEach { addStyle(SpanStyle(background = highlightGold.copy(alpha = 0.30f), color = Color(0xFFF3F4F6)), it.first, it.last + 1) }
    } }
    var layout by remember(styled) { mutableStateOf<TextLayoutResult?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        event.tagValue("comment")?.takeIf { it.isNotBlank() }?.let {
            Text(if (compact) it.take(600) else it, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = Color(0xFF3D3D3D))
        }
        Text(styled, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            onTextLayout = { layout = it }, modifier = Modifier.fillMaxWidth().drawBehind {
                layout?.let { result ->
                    for (range in passage.ranges) for (line in result.getLineForOffset(range.first)..result.getLineForOffset(range.last)) {
                        val start = maxOf(range.first, result.getLineStart(line))
                        val end = minOf(range.last + 1, result.getLineEnd(line, visibleEnd = true))
                        if (start < end) {
                            val first = result.getBoundingBox(start); val last = result.getBoundingBox(end - 1)
                            drawLine(highlightGold, Offset(minOf(first.left, last.left), first.bottom - 1.dp.toPx()), Offset(maxOf(first.right, last.right), first.bottom - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
                        }
                    }
                }
            })
        val source = remember(event.id) { highlightSource(event) }
        val author = highlightAuthor(event)
        if (source != null) {
            val linkStyle = TextLinkStyles(style = SpanStyle(color = Color(0xFF60A5FA)))
            Text(buildAnnotatedString {
                append("Highlight from ")
                if (source.label in listOf("blog post", "nostr post")) append("a ")
                withLink(LinkAnnotation.Url(source.url, linkStyle)) { append(source.label) }
                if (author != null) {
                    append(" by ")
                    withLink(LinkAnnotation.Url("https://njump.me/${Nip19.npubEncode(author)}", linkStyle)) { append(profiles[author]?.name ?: "${Nip19.npubEncode(author).take(12)}…") }
                }
            }, color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodySmall)
        }
    }
}
