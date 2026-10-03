package org.dergigi.ants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val listKinds = setOf(10000, 10001, 10003, 39089)
internal data class ListEntry(val type: String, val value: String, val query: String)

internal fun publicListEntries(event: Nip01Event): List<ListEntry> = event.tags.mapNotNull { tag ->
    val type = tag.firstOrNull() ?: return@mapNotNull null
    val value = tag.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    val allowed = when (event.kind) {
        10000 -> setOf("p", "e", "t", "word")
        10001 -> setOf("e")
        10003 -> setOf("e", "a")
        39089 -> setOf("p")
        else -> emptySet()
    }
    if (type !in allowed) return@mapNotNull null
    val query = when (type) {
        "t" -> "#${value.removePrefix("#")}" 
        "word" -> "\"${value.replace("\"", "")}\""
        else -> referenceQuery(type, value)
    } ?: return@mapNotNull null
    ListEntry(type, value, query)
}.distinctBy { it.query }

/** A reference is navigation, never raw JSON or an automatically expanded list. */
@Composable
internal fun EventReferenceRow(entry: ListEntry, profiles: Map<String, Profile>, onNavigate: (String) -> Unit) {
    val destination = remember(entry.query) {
        val kind = runCatching { Nip19.naddrDecode(entry.query).kind }.getOrNull()
        if (kind != null && kind !in renderedKinds) "https://njump.to/${entry.query}" else entry.query
    }
    val pubkey = if (entry.type == "p") Nip19.normalizePubkey(entry.value) else null
    val name = pubkey?.let { profiles[it]?.name }
    val label = name ?: when (entry.type) {
        "p" -> pubkey?.let { Nip19.npubEncode(it).let { key -> key.take(12) + "…" + key.takeLast(6) } }.orEmpty()
        "t" -> "#${entry.value.removePrefix("#")}" 
        "word" -> entry.value
        else -> entry.query.let { if (it.length > 34) it.take(18) + "…" + it.takeLast(8) else it }
    }
    Row(Modifier.fillMaxWidth().clickable { onNavigate(destination) }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pubkey != null) Avatar(profiles[pubkey], pubkey, { onNavigate(destination) }, size = 28)
        else Icon(when (entry.type) { "t" -> Icons.Outlined.Tag; "word" -> Icons.Outlined.TextFields; else -> Icons.Outlined.Article }, null, Modifier.size(20.dp))
        Text(label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.Outlined.ChevronRight, "Open", Modifier.size(18.dp))
    }
}

@Composable
internal fun ListContent(event: Nip01Event, profiles: Map<String, Profile>, compact: Boolean, onNavigate: (String) -> Unit) {
    val entries by produceState<List<ListEntry>?>(null, event.id) {
        value = withContext(Dispatchers.Default) { publicListEntries(event) }
    }
    val all = entries ?: return
    var visibleCount by remember(event.id, compact) { mutableIntStateOf(if (compact) 5 else 30) }
    val open = LocalQuoteState.current.open
    val loadProfiles = LocalLoadMentionProfiles.current
    LaunchedEffect(event.id, visibleCount, all) {
        loadProfiles(all.take(visibleCount).filter { it.type == "p" }.mapNotNull { Nip19.normalizePubkey(it.value) }.take(100))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (event.kind == 39089) {
            val description = event.tagValue("description").orEmpty()
            if (description.isNotBlank() || eventImages(event, true).isNotEmpty()) {
                EventContent(event.copy(kind = 1, content = description, tags = event.tags.filter { it.firstOrNull() == "image" }), null, profiles, compact, onNavigate)
            }
        }
        Text("${all.size} ${if (event.kind == 39089) "profiles" else "public entries"}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        all.take(visibleCount).forEach { entry -> key(entry.query) { EventReferenceRow(entry, profiles, onNavigate) } }
        if (all.size > visibleCount) TextButton(onClick = {
            if (compact) open(event) else visibleCount += 30
        }) { Text("+${all.size - visibleCount}") }
        if (event.kind != 39089 && event.content.isNotBlank()) {
            Icon(Icons.Outlined.Lock, "Private entries are encrypted", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
