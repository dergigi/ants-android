package org.dergigi.ants

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// Match the web app's filename extraction: keep URL encoding, omit query and fragment.
// Quote it so filenames containing query syntax are always searched as literal text.
internal fun imageFilenameQuery(imageUrl: String): String? {
    val filename = Uri.parse(imageUrl).encodedPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: return null
    return "\"${filename.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageFilenameSearchAction(imageUrl: String, tint: Color, onSearch: (String) -> Unit) {
    val query = remember(imageUrl) { imageFilenameQuery(imageUrl) }
    val label = if (query == null) "Image URL has no filename" else "Search Nostr for image filename"
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(enabled = query != null, onClick = { query?.let(onSearch) }) {
            Icon(Icons.Outlined.Search, label, tint = if (query != null) tint else tint.copy(alpha = 0.3f))
        }
    }
}
