package org.dergigi.ants

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DevelopmentContent(event: Nip01Event, compact: Boolean, onNavigate: (String) -> Unit) {
    val references = remember(event.id) { taggedNoteReferences(event).take(4) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (event.kind == 1621) ArticleContent(event.copy(tags = event.tags.map {
            if (it.firstOrNull() == "subject") listOf("title") + it.drop(1) else it
        }), compact, onNavigate)
        else {
            var limit by remember(event.id, compact) { mutableIntStateOf(if (compact) 16 else 120) }
            val lines by produceState<List<String>>(emptyList(), event.id, limit) {
                value = withContext(Dispatchers.Default) { event.content.lineSequence().take(limit + 1).map { it.take(2000) }.toList() }
            }
            val open = LocalQuoteState.current.open
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
                Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp)) {
                    lines.take(limit).forEach { line ->
                        Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp,
                            color = when {
                                line.startsWith("+") -> Color(0xFF4ADE80)
                                line.startsWith("-") -> Color(0xFFF87171)
                                line.startsWith("@@") || line.startsWith("diff ") -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            })
                    }
                }
            }
            if (lines.size > limit) TextButton(onClick = { if (compact) open(event) else limit += 120 }) { Text("…") }
        }
        references.forEach { reference ->
            EventReferenceRow(ListEntry("a", reference.key, reference.query), emptyMap(), onNavigate)
        }
    }
}

@Composable
internal fun ReportContent(event: Nip01Event, profiles: Map<String, Profile>, compact: Boolean, onNavigate: (String) -> Unit) {
    val preparedTargets by produceState<List<Pair<ListEntry, String?>>?>(null, event.id) {
        value = withContext(Dispatchers.Default) { event.tags.mapNotNull { tag ->
        val type = tag.firstOrNull()
        if (type != "p" && type != "e") return@mapNotNull null
        val value = tag.getOrNull(1) ?: return@mapNotNull null
        val query = referenceQuery(type, value) ?: return@mapNotNull null
        ListEntry(type, value, query) to tag.getOrNull(2)?.takeIf { it in setOf("nudity", "malware", "profanity", "illegal", "spam", "impersonation", "other") }
    }.distinctBy { it.first.query } }
    }
    val targets = preparedTargets ?: return
    var limit by remember(event.id, compact) { mutableIntStateOf(if (compact) 3 else 20) }
    val pageId = LocalThreadState.current.state.pageId
    val loadProfiles by rememberUpdatedState(LocalLoadMentionProfiles.current)
    LaunchedEffect(event.id, pageId, limit, targets) {
        loadProfiles(targets.take(limit).mapNotNull { (entry, _) ->
            if (entry.type == "p") Nip19.normalizePubkey(entry.value) else null
        }.distinct().take(100))
    }
    val open = LocalQuoteState.current.open
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Report", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        targets.take(limit).forEach { (entry, reason) ->
            if (reason != null) Text(reason, style = MaterialTheme.typography.labelMedium)
            EventReferenceRow(entry, profiles, onNavigate)
        }
        if (targets.size > limit) TextButton(onClick = { if (compact) open(event) else limit += 20 }) { Text("+${targets.size - limit}") }
        if (event.content.isNotBlank()) ArticleContent(event, compact, onNavigate)
    }
}
