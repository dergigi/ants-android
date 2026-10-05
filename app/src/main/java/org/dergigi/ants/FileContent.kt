package org.dergigi.ants

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FileContent(event: Nip01Event, compact: Boolean) {
    val context = LocalContext.current
    val openGallery = LocalOpenGallery.current
    var showDetails by remember(event.id) { mutableStateOf(false) }
    val attachment by produceState<FileAttachment?>(null, event.id) {
        value = withContext(Dispatchers.Default) { fileAttachment(event) }
    }
    val file = attachment ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Render the attachment directly. Reclassifying JSON as a note invoked the generic JSON fallback.
        when {
            file.image != null -> EventImage(file.image, compact) { openGallery(listOf(file.image), 0) }
            file.video != null -> EventVideo(VideoAttachment(file.video, file.poster))
            file.poster != null -> EventImage(file.poster, compact) { openGallery(listOf(file.poster), 0) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (file.image == null && file.video == null && file.poster == null) {
                Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            Text(file.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                maxLines = if (compact) 2 else 5, overflow = TextOverflow.Ellipsis)
            file.urls.firstOrNull()?.let { url ->
                ActionIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Open file", { openUrl(context, url) })
            }
            Box {
                ActionIcon(Icons.Outlined.Info, "File details", { showDetails = true })
                DropdownMenu(showDetails, onDismissRequest = { showDetails = false }, modifier = Modifier.widthIn(max = 300.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        file.mime?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        file.size?.let { Text(Formatter.formatFileSize(context, it), style = MaterialTheme.typography.bodySmall) }
                        if (file.urls.isEmpty()) Text("No file URL", style = MaterialTheme.typography.bodySmall)
                        file.hash?.let { SelectionContainer { Text("SHA-256: $it", style = MaterialTheme.typography.bodySmall) } }
                    }
                    file.urls.forEachIndexed { index, url ->
                        DropdownMenuItem(text = { Text(if (index == 0) "Open file" else "Open alternate source", maxLines = 1) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = { showDetails = false; openUrl(context, url) })
                    }
                }
            }
        }
        if (file.description.isNotBlank()) SelectionContainer {
            Text(file.description, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (compact) 3 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        }
    }
}
