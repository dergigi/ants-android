package org.dergigi.ants

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import coil3.compose.AsyncImage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val emojiShortcode = Regex(":([a-zA-Z0-9_-]+):")

internal fun customEmojis(event: Nip01Event): Map<String, String> = buildMap {
    event.tags.filter { it.firstOrNull() == "emoji" }.take(100).forEach { tag ->
        val name = tag.getOrNull(1) ?: return@forEach
        if (!name.matches(Regex("[a-zA-Z0-9_-]+"))) return@forEach
        val url = tag.getOrNull(2)?.toHttpUrlOrNull() ?: return@forEach
        if (url.isHttps && url.username.isEmpty() && url.password.isEmpty() && name !in this) put(name, url.toString())
    }
}

@Composable
internal fun CustomEmojiText(text: AnnotatedString, event: Nip01Event, style: TextStyle, emojiSize: TextUnit, maxLines: Int) {
    val emojis = remember(event.id) { customEmojis(event) }
    var failed by remember(event.id) { mutableStateOf(emptySet<String>()) }
    val matches = remember(text, emojis, failed) {
        val urls = webLinks(text.text)
        emojiShortcode.findAll(text.text).filter { match ->
            val name = match.groupValues[1]
            name in emojis && name !in failed && urls.none { match.range.first < it.end && match.range.last >= it.start }
        }.take(100).toList()
    }
    val annotated = remember(text, matches) {
        buildAnnotatedString {
            var cursor = 0
            matches.forEach { match ->
                append(text.subSequence(cursor, match.range.first))
                appendInlineContent("emoji:${match.groupValues[1]}", match.value)
                cursor = match.range.last + 1
            }
            append(text.subSequence(cursor, text.length))
        }
    }
    val inline = matches.map { it.groupValues[1] }.distinct().associate { name ->
        "emoji:$name" to InlineTextContent(Placeholder(emojiSize, emojiSize, PlaceholderVerticalAlign.TextCenter)) {
            AsyncImage(model = emojis.getValue(name), contentDescription = ":$name:",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                onError = { failed = failed + name })
        }
    }
    Text(annotated, inlineContent = inline, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
