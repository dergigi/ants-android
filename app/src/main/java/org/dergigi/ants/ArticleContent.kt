package org.dergigi.ants

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

private data class ArticlePreview(val title: String, val summary: String, val body: String, val cover: String?, val images: List<String>)

@Composable
internal fun ArticleContent(event: Nip01Event, compact: Boolean, onNavigate: (String) -> Unit) {
    val article by produceState<ArticlePreview?>(null, event.id, compact) {
        value = withContext(Dispatchers.Default) {
            val title = event.tagValue("title").orEmpty().trim()
            val summary = event.tagValue("summary").orEmpty().trim()
            val cover = event.tagValue("image")?.takeIf { Uri.parse(it).scheme == "https" }
            var body = event.content.trim()
            val firstLine = body.lineSequence().firstOrNull().orEmpty()
            if (title.isNotBlank() && firstLine.matches(Regex("^#{1,6}\\s+.*")) && firstLine.trimStart('#', ' ').trimEnd(' ', '#') == title) {
                body = body.substringAfter('\n', "").trimStart()
            }
            if (compact) body = if (summary.isNotBlank()) "" else body.take(1200).let { if (body.length > it.length) "$it…" else it }
            ArticlePreview(title, summary, body, cover, (listOfNotNull(cover) + eventImages(event, compact = false)).distinct())
        }
    }
    val rendered = article ?: return
    val openGallery = LocalOpenGallery.current
    val currentNavigate by rememberUpdatedState(onNavigate)
    val uriHandler = remember { object : UriHandler {
        override fun openUri(uri: String) {
            val scheme = Uri.parse(uri).scheme?.lowercase()
            if (scheme in listOf("https", "http")) currentNavigate(uri)
            else if (scheme == "nostr") pointerQuery(uri)?.let(currentNavigate)
        }
    } }
    val imageTransformer = remember(rendered.images, openGallery) { object : ImageTransformer by Coil3ImageTransformerImpl {
        @Composable
        override fun transform(link: String): ImageData {
            val image = Coil3ImageTransformerImpl.transform(link)
            return image.copy(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).clip(RoundedCornerShape(6.dp)).clickable {
                val gallery = (rendered.images + link).distinct()
                openGallery(gallery, gallery.indexOf(link))
            }, contentScale = ContentScale.Fit)
        }
    } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rendered.title.isNotBlank()) Text(rendered.title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            maxLines = if (compact) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        if (rendered.cover != null && (compact || !event.content.contains(rendered.cover))) {
            AsyncImage(rendered.cover, rendered.title.ifBlank { "Article cover" },
                Modifier.fillMaxWidth().heightIn(max = if (compact) 240.dp else 480.dp).clip(RoundedCornerShape(6.dp))
                    .clickable { openGallery(rendered.images, rendered.images.indexOf(rendered.cover)) }, contentScale = ContentScale.FillWidth)
        }
        if (rendered.summary.isNotBlank()) Text(rendered.summary.take(if (compact) 1200 else 6000),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (compact) 6 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        if (rendered.body.isNotBlank()) {
            val flavour = remember { GFMFlavourDescriptor() }
            val parser = remember(flavour) { MarkdownParser(flavour) }
            val markdown = rememberMarkdownState(content = rendered.body, flavour = flavour, parser = parser)
            val bodyStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp)
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                Markdown(markdownState = markdown, modifier = Modifier.fillMaxWidth(), imageTransformer = imageTransformer,
                    colors = markdownColor(text = MaterialTheme.colorScheme.onSurface, codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
                        inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHighest),
                    typography = markdownTypography(text = bodyStyle, paragraph = bodyStyle, quote = bodyStyle,
                        code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), inlineCode = bodyStyle.copy(fontFamily = FontFamily.Monospace)))
            }
        }
    }
}
