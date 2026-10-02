package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow

// NIP-25: the LAST e tag identifies the reacted-to event, not its thread root.
internal fun reactionTargetId(event: Nip01Event): String? = if (event.kind == 7) {
    event.tags.lastOrNull { it.firstOrNull() == "e" }?.getOrNull(1)
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }?.lowercase()
} else null

// Marked references take precedence; explicit mentions are not reply parents.
internal fun parentEventId(event: Nip01Event): String? {
    if (event.kind == 7) return reactionTargetId(event)
    if (event.kind == 1111) return event.tags.firstOrNull { it.firstOrNull() == "e" }?.getOrNull(1)
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }?.lowercase()
    if (event.kind != 1) return null
    val tags = event.tags.filter { it.firstOrNull() == "e" }
    val tag = tags.firstOrNull { it.getOrNull(3) == "reply" }
        ?: tags.firstOrNull { it.getOrNull(3) == "root" }
        ?: tags.lastOrNull { it.getOrNull(3).isNullOrBlank() }
    return tag?.getOrNull(1)?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }?.lowercase()
}

@Composable
internal fun ReactionContent(event: Nip01Event) {
    when (event.content.trim()) {
        "", "+" -> Icon(Icons.Outlined.ThumbUp, "Liked", Modifier.size(24.dp))
        "-" -> Icon(Icons.Outlined.ThumbDown, "Disliked", Modifier.size(24.dp))
        else -> Text(event.content.take(120), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (reactionTargetId(event) == null) Text("This reaction has no valid target reference.", style = MaterialTheme.typography.bodySmall)
}
