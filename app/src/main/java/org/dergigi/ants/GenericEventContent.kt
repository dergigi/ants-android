package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** A bounded, inspectable fallback for kinds without a specialized native renderer. */
@Composable
internal fun GenericEventContent(event: Nip01Event, profiles: Map<String, Profile>, compact: Boolean, onNavigate: (String) -> Unit) {
    var page by remember(event.id) { mutableIntStateOf(0) }
    var tagCount by remember(event.id, compact) { mutableIntStateOf(if (compact) 3 else 20) }
    val context = LocalContext.current
    val open = LocalQuoteState.current.open
    val encrypted = event.kind in setOf(4, 44, 1059, 24133, 23194, 23195)
    val pageSize = 4000
    val pages = ((event.content.length.toLong() + pageSize - 1) / pageSize).toInt().coerceAtLeast(1)
    val text = event.content.substring((page * pageSize).coerceAtMost(event.content.length),
        ((page + 1) * pageSize).coerceAtMost(event.content.length))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (encrypted) Text("Encrypted content", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (text.isNotBlank()) {
            SelectionContainer { Text(text, fontFamily = if (text.trimStart().startsWith('{') || text.trimStart().startsWith('[')) FontFamily.Monospace else FontFamily.Default,
                maxLines = if (compact) 9 else Int.MAX_VALUE) }
            if (pages > 1 && !compact) Row {
                TextButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                TextButton(onClick = { page++ }, enabled = page + 1 < pages) { Text("Next (${page + 1}/$pages)") }
            }
        } else Text("No text content", color = MaterialTheme.colorScheme.onSurfaceVariant)
        event.tags.take(tagCount).forEach { tag ->
            val type = tag.firstOrNull().orEmpty()
            val value = tag.getOrNull(1).orEmpty()
            val query = remember(type, value) { referenceQuery(type, value) }
            if (query != null) EventReferenceRow(ListEntry(type, value, query), profiles, onNavigate)
            else if (attachmentUrl(value) != null) TextButton(onClick = { openUrl(context, value) }) {
                Text("${type.take(40)}: ${value.take(200)}", maxLines = 2)
            } else SelectionContainer {
                Text(tag.take(6).joinToString(" · ") { it.take(200) }, style = MaterialTheme.typography.bodySmall, maxLines = 4)
            }
        }
        if (event.tags.size > tagCount || compact && (pages > 1 || text.length > 400)) TextButton(onClick = {
            if (compact) open(event) else tagCount += 20
        }) { Text(if (compact) "View event" else "More tags (${event.tags.size - tagCount})") }
    }
}

internal fun attachmentUrl(value: String): String? = runCatching {
    val uri = java.net.URI(value)
    value.takeIf { uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null }
}.getOrNull()

@Composable
internal fun FileContent(event: Nip01Event, profiles: Map<String, Profile>, compact: Boolean, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val urls = remember(event.id) { event.tags.asSequence().filter { it.firstOrNull() in setOf("url", "fallback") }
        .mapNotNull { it.getOrNull(1)?.let(::attachmentUrl) }.distinct().take(10).toList() }
    val mime = event.tagValue("m").orEmpty().take(100)
    val size = event.tagValue("size")?.toLongOrNull()?.takeIf { it >= 0 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (event.content.isNotBlank() || eventImages(event, true).isNotEmpty() || eventVideos(event).isNotEmpty()) {
            EventContent(event.copy(kind = 1), null, profiles, compact, onNavigate)
        }
        Text(listOfNotNull(mime.takeIf(String::isNotBlank), size?.let { "$it bytes" }).joinToString(" · ").ifBlank { "File attachment" },
            style = MaterialTheme.typography.labelMedium)
        urls.forEachIndexed { index, url ->
            TextButton(onClick = { openUrl(context, url) }) { Text(if (index == 0) "Open file" else "Open alternate source ${index + 1}") }
        }
        if (urls.isEmpty()) Text("No downloadable URL", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!compact) event.tagValue("x")?.let { hash -> SelectionContainer {
            Text("SHA-256: ${hash.take(64)}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        } }
    }
}
