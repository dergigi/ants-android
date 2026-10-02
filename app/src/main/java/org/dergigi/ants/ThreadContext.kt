package org.dergigi.ants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class ThreadState(val state: SearchState, val load: (String) -> Unit)
internal val LocalThreadState = staticCompositionLocalOf<ThreadState> { error("Thread state is required") }

@Composable
internal fun ThreadContext(event: Nip01Event, onNavigate: (String) -> Unit, ancestors: Set<String> = emptySet()) {
    val id = parentEventId(event) ?: return
    val thread = LocalThreadState.current
    val state = thread.state
    val target = state.reactionTargets[id] ?: state.events.firstOrNull { it.id == id }
    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }
    val loading = id in state.loadingParents
    val failed = id in state.failedParents
    val chain = ancestors + event.id
    val bounded = id in chain || chain.size >= 16
    val relation = if (event.kind == 7) "Reaction to" else "Reply to"
    val preview = target?.let { parent ->
        val author = state.profiles[parent.pubkey]?.name ?: Nip19.npubEncode(parent.pubkey).take(12) + "…"
        "$author · ${parent.content.replace('\n', ' ').take(100)}"
    } ?: Nip19.noteEncode(id).let { it.take(12) + "…" + it.takeLast(6) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF262626)).clickable {
            if (bounded) onNavigate(Nip19.noteEncode(id))
            else {
                expanded = !expanded
                if (expanded && target == null) thread.load(id)
            }
        }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (event.kind == 7) Icons.Outlined.FavoriteBorder else Icons.AutoMirrored.Outlined.Reply, relation, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text("$relation · $preview", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            Icon(if (bounded) Icons.Outlined.NorthEast else if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                if (bounded) "Open parent note" else if (expanded) "Collapse parent note" else "Expand parent note", Modifier.size(18.dp))
        }
        if (expanded && !bounded) {
            if (target != null) {
                ThreadContext(target, onNavigate, chain)
                Column(Modifier.fillMaxWidth().background(Color(0xFF242424)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.profiles[target.pubkey]?.name ?: Nip19.npubEncode(target.pubkey).take(16) + "…",
                            Modifier.weight(1f).clickable { onNavigate("by:${Nip19.npubEncode(target.pubkey)}") },
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        ActionIcon(Icons.Outlined.NorthEast, "Open parent note", { onNavigate(Nip19.noteEncode(id)) })
                    }
                    when (target.kind) {
                        7 -> ReactionContent(target)
                        9802 -> HighlightContent(target, state.profiles, compact = true, onNavigate = onNavigate)
                        else -> EventContent(target, state.profiles[target.pubkey], compact = true, onNavigate = onNavigate)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(if (loading) "Loading parent note…" else "Parent note unavailable", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (!loading) ActionIcon(Icons.Outlined.Refresh, if (failed) "Retry parent note" else "Load parent note", { thread.load(id) })
                    ActionIcon(Icons.Outlined.NorthEast, "Look up parent note", { onNavigate(Nip19.noteEncode(id)) })
                }
            }
            HorizontalDivider(color = Color(0xFF3D3D3D))
        }
    }
}
