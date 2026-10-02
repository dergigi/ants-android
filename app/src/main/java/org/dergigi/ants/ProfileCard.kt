package org.dergigi.ants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.json.JSONObject

@Composable
internal fun ProfileCard(event: Nip01Event, profile: Profile?, profiles: Map<String, Profile>, onNavigate: (String) -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val npub = remember(event.pubkey) { Nip19.npubEncode(event.pubkey) }
    val fields = remember(event.id) { profileFields(event) }
    val metadata = remember(event.id) { runCatching { JSONObject(event.content) }.getOrDefault(JSONObject()) }
    fun field(vararg names: String) = names.firstNotNullOfOrNull { (metadata.opt(it) as? String)?.trim()?.takeIf(String::isNotBlank) }
    fun http(value: String?) = value?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    val banner = http(field("banner", "cover", "header"))
    val website = field("website", "url")?.let { http(it) ?: if (':' !in it && '.' in it) "https://$it" else null }
    val lightning = field("lud16", "lud06")
    val author = { onNavigate("p:$npub") }
    val shownProfile = profile ?: Profile(fields.display.ifBlank { fields.name }.ifBlank { npub.take(12) + "…" }, fields.about, http(field("picture", "image")), event.createdAt)
    ResolveMentionProfiles(event)
    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF2D2D2D), border = BorderStroke(1.dp, Color(0xFF3D3D3D))) {
        Column(Modifier.fillMaxWidth()) {
            banner?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(112.dp), contentScale = ContentScale.Crop) }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(shownProfile, event.pubkey, author, size = 48)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable(onClick = author)) {
                        Text(shownProfile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (fields.name.isNotBlank() && fields.name != shownProfile.name) Text("@${fields.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (fields.nip05.isNotBlank()) Text(fields.nip05.removePrefix("_@"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ActionIcon(Icons.Outlined.ContentCopy, "Copy public key", { clipboard.setText(AnnotatedString(npub)) })
                }
                if (shownProfile.about.isNotBlank()) Text(linkedText(shownProfile.about, event, profiles, onNavigate = onNavigate), style = MaterialTheme.typography.bodyMedium)
                website?.let { Text(it.removePrefix("https://").removePrefix("http://").trimEnd('/'), Modifier.clickable { openUrl(context, it) }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                lightning?.let { Row(Modifier.clickable { clipboard.setText(AnnotatedString(it)) }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bolt, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } }
            }
            HorizontalDivider(color = Color(0xFF3D3D3D))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(Icons.Outlined.Notes, "Search posts", { onNavigate("by:$npub") })
                ActionIcon(Icons.Outlined.AlternateEmail, "Search mentions", { onNavigate("mentions:$npub") })
                ActionIcon(Icons.Outlined.PhoneAndroid, "Open profile in app", { openInNostrApp(context, event) })
                ActionIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Open profile in browser", { openUrl(context, "https://njump.to/$npub") })
                ActionIcon(Icons.Outlined.MoreHoriz, "Profile event details", onOpen)
            }
        }
    }
}
