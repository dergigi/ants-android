package org.dergigi.ants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal fun orderedResults(events: List<Nip01Event>, newestFirst: Boolean, profileFeedAuthor: String?): List<Nip01Event> {
    val profile = profileFeedAuthor?.let { author -> events.firstOrNull { it.kind == 0 && it.pubkey == author } }
    val comparator = compareBy<Nip01Event> { it.createdAt }.thenBy { it.id }
    val sorted = events.filter { it.id != profile?.id }.sortedWith(if (newestFirst) comparator.reversed() else comparator)
    return listOfNotNull(profile) + sorted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultSortButton(newestFirst: Boolean, onToggle: () -> Unit) {
    val current = if (newestFirst) "Newest first" else "Oldest first"
    val action = if (newestFirst) "Show oldest first" else "Show newest first"
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("$current · $action") } }, state = rememberTooltipState()) {
        Row(Modifier.height(48.dp).width(64.dp).semantics { stateDescription = current }
            .clickable(role = Role.Button, onClickLabel = action, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.AutoMirrored.Outlined.Sort, action, Modifier.size(20.dp).scale(1f, if (newestFirst) 1f else -1f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
