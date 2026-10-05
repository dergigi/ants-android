package org.dergigi.ants

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class ImageSearchProvider(val name: String, val endpoint: String, val icon: ImageVector) {
    fun searchUrl(imageUrl: String): String = Uri.parse(endpoint).buildUpon()
        .appendQueryParameter("url", imageUrl).build().toString()
}

private val imageSearchProviders = listOf(
    ImageSearchProvider("Google Lens", "https://lens.google.com/uploadbyurl", Icons.Outlined.CenterFocusStrong),
    ImageSearchProvider("TinEye", "https://tineye.com/search", Icons.Outlined.Visibility),
    ImageSearchProvider("Yandex Images", "https://yandex.com/images/search?rpt=imageview", Icons.Outlined.Language),
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
            Row(Modifier.padding(horizontal = 4.dp)) {
                imageSearchProviders.forEach { provider ->
                    ActionIcon(provider.icon, "Search with ${provider.name}", {
                        expanded = false
                        openUrl(context, provider.searchUrl(imageUrl))
                    })
                }
            }
        }
    }
}
