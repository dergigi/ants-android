package org.dergigi.ants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow

// NIP-25: the LAST e tag identifies the reacted-to event, not its thread root.
internal fun reactionTargetId(event: Nip01Event): String? = if (event.kind == 7) {
    event.tags.lastOrNull { it.firstOrNull() == "e" }?.getOrNull(1)
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }?.lowercase()
} else null

@Composable
internal fun ReactionContent(event: Nip01Event, targets: Map<String, Nip01Event>, loading: Boolean, profiles: Map<String, Profile>, onNavigate: (String) -> Unit) {
    val id = reactionTargetId(event)
    val target = targets[id]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (event.content.trim()) {
                "", "+" -> Icon(Icons.Outlined.ThumbUp, "Liked", Modifier.size(20.dp))
                "-" -> Icon(Icons.Outlined.ThumbDown, "Disliked", Modifier.size(20.dp))
                else -> Text(event.content.take(120), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ArrowDownward, "Reaction to the post below", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (id == null) {
            Text("This reaction has no valid target reference.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            Surface(onClick = { onNavigate(Nip19.noteEncode(id)) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (target != null) {
                        Text(profiles[target.pubkey]?.name ?: "${Nip19.npubEncode(target.pubkey).take(14)}…",
                            Modifier.clickable { onNavigate("by:${Nip19.npubEncode(target.pubkey)}") }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        if (target.kind == 9802) HighlightContent(target, profiles, compact = true, onNavigate = onNavigate)
                        else EventContent(target, profiles[target.pubkey], compact = true, onNavigate = onNavigate)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Search, "Look up reacted-to post", Modifier.size(18.dp))
                            Text(if (loading) "Loading reacted-to post…" else "Open reacted-to post", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${id.take(12)}…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
