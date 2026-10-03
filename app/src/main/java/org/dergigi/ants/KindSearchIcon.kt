package org.dergigi.ants

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal fun kindSearchQuery(kind: Int): String {
    val alias = if (kind == 30023) "article" else kindAliases.entries.firstOrNull { kind in it.value }?.key
    return alias?.let { "is:$it" } ?: "kind:$kind"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KindSearchIcon(kind: Int, icon: ImageVector, tint: Color, onNavigate: (String) -> Unit) {
    val query = kindSearchQuery(kind)
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(query) } }, state = rememberTooltipState()) {
        IconButton(onClick = { onNavigate(query) }, modifier = Modifier.size(48.dp)) {
            Icon(icon, "Search $query", Modifier.size(16.dp), tint = tint)
        }
    }
}
