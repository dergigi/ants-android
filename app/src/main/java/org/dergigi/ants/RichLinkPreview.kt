package org.dergigi.ants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal val LocalLinkPreviews = staticCompositionLocalOf<LinkPreviewRepository?> { null }

@Composable
internal fun RichLinkPreview(url: String) {
    val repository = LocalLinkPreviews.current ?: return
    val context = LocalContext.current
    val view = LocalView.current
    var visible by remember(url) { mutableStateOf(false) }
    val preview by produceState<LinkPreview?>(null, url, repository, visible) {
        if (visible) value = repository.preview(url)
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).onGloballyPositioned {
        val bounds = it.boundsInWindow()
        visible = bounds.bottom > 0 && bounds.top < view.height
    }) {
        preview?.let { data ->
            Card(Modifier.fillMaxWidth().clickable(onClickLabel = "Open link", onClick = { openUrl(context, data.url) })) {
                val bytes by produceState<ByteArray?>(null, data.image, visible) {
                    if (visible) data.image?.let { value = repository.image(it) }
                }
                bytes?.let {
                    AsyncImage(ImageRequest.Builder(context).data(it).size(640, 360).memoryCacheKey("preview:${data.image}").build(),
                        contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Crop)
                }
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(data.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (data.description.isNotBlank()) Text(data.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(data.url.toHttpUrlOrNull()?.host.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
