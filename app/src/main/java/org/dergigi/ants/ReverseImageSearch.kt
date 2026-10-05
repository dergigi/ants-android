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

private data class ImageSearchProvider(val name: String, val endpoint: String) {
    fun searchUrl(imageUrl: String): String = Uri.parse(endpoint).buildUpon()
        .appendQueryParameter("url", imageUrl).build().toString()
}

private val imageSearchProviders = listOf(
    ImageSearchProvider("Google Lens", "https://lens.google.com/uploadbyurl"),
    ImageSearchProvider("TinEye", "https://tineye.com/search"),
    ImageSearchProvider("Yandex Images", "https://yandex.com/images/search?rpt=imageview"),
)

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
                Text("Sends this image’s URL to the provider you choose.", style = MaterialTheme.typography.bodySmall)
                imageSearchProviders.forEach { provider ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        ActionIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Search with ${provider.name}", {
                            expanded = false
                            openUrl(context, provider.searchUrl(imageUrl))
                        })
                    }
                }
            }
        }
    }
}
