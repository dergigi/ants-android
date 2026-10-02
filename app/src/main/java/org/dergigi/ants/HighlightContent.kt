package org.dergigi.ants

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.UriHandler
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val highlightGold = Color(0xFFF6DE74)
private data class HighlightPassage(val text: AnnotatedString, val ranges: List<IntRange>)

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

private fun passage(content: AnnotatedString, context: AnnotatedString, compact: Boolean): HighlightPassage {
    val (haystack, offsets) = normalized(context.text)
    val needle = normalized(content.text).first.trim()
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
        val text = if (compact && content.length > 1400) content.subSequence(0, 1400) + AnnotatedString("…") else content
        return HighlightPassage(text, if (text.isEmpty()) emptyList() else listOf(0 until text.length))
    }
    if (!compact || context.length <= 1600) return HighlightPassage(context, ranges)
    val start = (ranges.first().first - 180).coerceAtLeast(0)
    val end = (start + 1600).coerceAtMost(context.length)
    val prefix = if (start > 0) "…" else ""
    val text = AnnotatedString(prefix) + context.subSequence(start, end) + AnnotatedString(if (end < context.length) "…" else "")
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

private data class HighlightSource(val label: String, val query: String)
private fun highlightSource(event: Nip01Event): HighlightSource? {
    event.tagValue("r")?.let { raw ->
        val uri = Uri.parse(raw)
        if (uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) return HighlightSource(uri.host!!.removePrefix("www."), raw)
    }
    event.tagValue("e")?.let { id ->
        if (id.matches(Regex("[0-9a-fA-F]{64}"))) return HighlightSource("nostr post", Nip19.noteEncode(id))
    }
    return runCatching {
        val parts = event.tagValue("a")?.split(':', limit = 3) ?: return null
        val kind = parts[0].toInt()
        val author = Nip19.normalizePubkey(parts[1]) ?: return null
        val pointer = Nip19.naddrEncode(NaddrPointer(parts[2], author, kind))
        HighlightSource(if (kind == 30023) "blog post" else "nostr post", pointer)
    }.getOrNull()
}

@Composable
internal fun HighlightContent(event: Nip01Event, profiles: Map<String, Profile>, compact: Boolean, onNavigate: (String) -> Unit) {
    ResolveMentionProfiles(event)
    val currentNavigate by rememberUpdatedState(onNavigate)
    val uriHandler = remember { object : UriHandler {
        override fun openUri(uri: String) {
            if (Uri.parse(uri).scheme?.lowercase() in listOf("http", "https")) currentNavigate(uri)
            else pointerQuery(uri)?.let(currentNavigate)
        }
    } }
    val settings = annotatorSettings(uriHandler = uriHandler)
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    val rendered by produceState<Triple<LinkedContent, AnnotatedString, AnnotatedString?>?>(null, event.id, compact, profiles, style) {
        value = withContext(Dispatchers.Default) {
            fun markdown(text: String): AnnotatedString = try {
                text.buildMarkdownAnnotatedString(style, settings).takeUnless { it.isEmpty() && text.isNotBlank() }
                    ?: AnnotatedString(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A single publisher's Markdown must not cancel the composition's scope.
                Log.w("HighlightContent", "Unable to format highlight ${event.id}", error)
                AnnotatedString(text)
            }
            val passage = passage(markdown(event.content.trim()), markdown(event.tagValue("context").orEmpty()), compact)
            val markdownLinks = passage.text.getLinkAnnotations(0, passage.text.length)
            val linked = linkedContent(passage.text.text, event, profiles, passage.ranges,
                protectedRanges = markdownLinks.map { it.start until it.end }) { currentNavigate(it) }
            val styled = buildAnnotatedString {
                append(linked.text)
                passage.text.spanStyles.forEach { span -> linked.mapRange(span.start, span.end)?.let { addStyle(span.item, it.first, it.last + 1) } }
                passage.text.paragraphStyles.forEach { span -> linked.mapRange(span.start, span.end)?.let { addStyle(span.item, it.first, it.last + 1) } }
                markdownLinks.forEach { span -> linked.mapRange(span.start, span.end)?.let { range ->
                    when (val link = span.item) {
                        is LinkAnnotation.Url -> addLink(link, range.first, range.last + 1)
                        is LinkAnnotation.Clickable -> addLink(link, range.first, range.last + 1)
                    }
                } }
                linked.ranges.forEach { addStyle(SpanStyle(background = highlightGold.copy(alpha = 0.30f), color = Color(0xFFF3F4F6), textDecoration = TextDecoration.Underline), it.first, it.last + 1) }
            }
            val comment = event.tagValue("comment")?.takeIf { it.isNotBlank() }?.let {
                val parsed = markdown(it)
                if (compact && parsed.length > 600) parsed.subSequence(0, 600) + AnnotatedString("…") else parsed
            }
            Triple(linked, styled, comment)
        }
    }
    val (linked, styled, comment) = rendered ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        comment?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = Color(0xFF3D3D3D))
        }
        Text(styled, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            modifier = Modifier.fillMaxWidth())

        val source = remember(event.id) { highlightSource(event) }
        val author = highlightAuthor(event)
        if (source != null) {
            val linkStyle = TextLinkStyles(style = SpanStyle(color = Color(0xFF60A5FA)))
            Text(buildAnnotatedString {
                append("Highlight from ")
                if (source.label in listOf("blog post", "nostr post")) append("a ")
                withLink(LinkAnnotation.Clickable(source.query, linkStyle) { onNavigate(source.query) }) { append(source.label) }
                if (author != null) {
                    append(" by ")
                    withLink(LinkAnnotation.Clickable(author, linkStyle) { onNavigate("p:${Nip19.npubEncode(author)}") }) { append(profiles[author]?.name ?: "${Nip19.npubEncode(author).take(12)}…") }
                }
            }, color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodySmall)
        }
    }
}
