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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class ThreadState(val state: SearchState, val load: (String) -> Unit)
internal val LocalThreadState = staticCompositionLocalOf<ThreadState> { error("Thread state is required") }

@Composable
internal fun ThreadContext(event: Nip01Event, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val thread = LocalThreadState.current
    val state = thread.state
    var requested by rememberSaveable(event.id) { mutableStateOf(emptyList<String>()) }
    fun find(id: String) = state.reactionTargets[id] ?: state.events.firstOrNull { it.id == id }

    // Build a flat timeline: each successful load consumes its bar and moves
    // the only remaining load control above the oldest visible ancestor.
    val parents = mutableListOf<Nip01Event>()
    val visited = mutableSetOf(event.id)
    var oldest = event
    while (true) {
        val id = parentEventId(oldest) ?: break
        if (id !in requested || id in visited) break
        val parent = find(id) ?: break
        visited += id
        parents += parent
        oldest = parent
    }
    val nextId = parentEventId(oldest)?.takeUnless { it in visited }
    Column(Modifier.fillMaxWidth()) {
        if (nextId != null) {
            val target = find(nextId)
            val loading = nextId in state.loadingParents
            val failed = nextId in state.failedParents
            val relation = if (oldest.kind == 7) "Reaction to" else "Reply to"
            val preview = target?.let { parent ->
                val author = state.profiles[parent.pubkey]?.name ?: Nip19.npubEncode(parent.pubkey).take(12) + "…"
                "$author · ${parent.content.replace('\n', ' ').take(100)}"
            } ?: Nip19.noteEncode(nextId).let { it.take(12) + "…" + it.takeLast(6) }
            Row(Modifier.fillMaxWidth().background(Color(0xFF262626)).clickable(
                enabled = !loading,
                onClickLabel = if (failed) "Retry loading earlier note" else "Load earlier note",
            ) {
                if (nextId !in requested) requested = requested + nextId
                if (target == null) thread.load(nextId)
            }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (oldest.kind == 7) Icons.Outlined.FavoriteBorder else Icons.AutoMirrored.Outlined.Reply, relation, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(when {
                    loading -> "Loading earlier note…"
                    failed -> "$relation · Unavailable — tap to retry"
                    else -> "$relation · $preview"
                }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(if (failed) Icons.Outlined.Refresh else Icons.Outlined.ArrowUpward,
                    if (failed) "Retry earlier note" else "Load earlier note", Modifier.size(18.dp))
            }
        }
        parents.asReversed().forEach { parent ->
            key(parent.id) {
                Column(Modifier.fillMaxWidth().background(Color(0xFF242424)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.profiles[parent.pubkey]?.name ?: Nip19.npubEncode(parent.pubkey).take(16) + "…",
                            Modifier.weight(1f).clickable { onNavigate("by:${Nip19.npubEncode(parent.pubkey)}") },
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        ActionIcon(Icons.Outlined.PhoneAndroid, "Open in app", { openInNostrApp(context, parent) })
                        ActionIcon(Icons.Outlined.NorthEast, "Open note", { onNavigate(Nip19.noteEncode(parent.id)) })
                    }
                    when (parent.kind) {
                        7 -> ReactionContent(parent)
                        9802 -> HighlightContent(parent, state.profiles, compact = true, onNavigate = onNavigate)
                        else -> EventContent(parent, state.profiles[parent.pubkey], state.profiles, compact = true, onNavigate = onNavigate)
                    }
                }
                HorizontalDivider(color = Color(0xFF3D3D3D))
            }
        }
    }
}
