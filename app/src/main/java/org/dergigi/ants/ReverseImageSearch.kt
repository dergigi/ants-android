package org.dergigi.ants

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReverseImageSearchAction(imageUrl: String, tint: Color) {
    val context = LocalContext.current
    var expanded by remember(imageUrl) { mutableStateOf(false) }
    Box {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text("Reverse image search") } },
            state = rememberTooltipState(),
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Outlined.ImageSearch, "Reverse image search", tint = tint)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.width(260.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reverse image search", style = MaterialTheme.typography.titleSmall)
                Text("Sends this image’s URL to Google Lens.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Google Lens", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    ActionIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Search with Google Lens", {
                        expanded = false
                        val url = Uri.Builder().scheme("https").authority("lens.google.com")
                            .appendPath("uploadbyurl").appendQueryParameter("url", imageUrl).build()
                        openUrl(context, url.toString())
                    })
                }
            }
        }
    }
}
