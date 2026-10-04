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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

@Composable
internal fun ProfileCard(event: Nip01Event, profile: Profile?, profiles: Map<String, Profile>, onNavigate: (String) -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember(event.pubkey) { mutableStateOf(false) }
    val npub = remember(event.pubkey) { Nip19.npubEncode(event.pubkey) }
    val fields = remember(event.id) { profileFields(event) }
    var identityInfoOpen by remember(event.pubkey, fields.nip05) { mutableStateOf(false) }
    val metadata = remember(event.id) { runCatching { JSONObject(event.content) }.getOrDefault(JSONObject()) }
    fun field(vararg names: String) = names.firstNotNullOfOrNull { (metadata.opt(it) as? String)?.trim()?.takeIf(String::isNotBlank) }
    fun http(value: String?) = value?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    val banner = http(field("banner", "cover", "header"))
    val website = field("website", "url")?.let { http(it) ?: if (':' !in it && '.' in it) "https://$it" else null }
    val lightning = field("lud16", "lud06")
    var checkingIdentity by remember(event.pubkey, fields.nip05, lightning != null) { mutableStateOf(true) }
    val indicators by produceState(ProfileIndicators(), event.pubkey, fields.nip05, lightning != null) {
        value = ProfileIndicators()
        checkingIdentity = true
        value = try { ProfileIndicatorLookup.load(event.pubkey, fields.nip05, lightning != null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ProfileIndicators() }
        checkingIdentity = false
    }
    val green = Color(0xFF4ADE80)
    val identityColor = when { fields.nip05.isBlank() -> Color(0xFFFACC15); indicators.verified == true -> green; indicators.verified == false -> Color(0xFFF87171); else -> MaterialTheme.colorScheme.onSurfaceVariant }
    val lightningColor = when { indicators.sentZap && indicators.sentNutzap -> green; indicators.sentZap -> Color(0xFFFEF08A); indicators.sentNutzap -> Color(0xFFC084FC); else -> MaterialTheme.colorScheme.onSurfaceVariant }
    val lightningStatus = when { indicators.sentZap && indicators.sentNutzap -> "Sent zaps and nutzaps"; indicators.sentZap -> "Sent zaps"; indicators.sentNutzap -> "Sent nutzaps"; else -> "Lightning address" }
    val domain = normalizedNip05(fields.nip05).substringAfter('@', "")
    val rootIdentity = normalizedNip05(fields.nip05).startsWith("_@")
    val author = { onNavigate("p:$npub") }
    val shownProfile = profile ?: Profile(fields.display.ifBlank { fields.name }.ifBlank { npub.take(12) + "…" }, fields.about, http(field("picture", "image")), event.createdAt)
    val openGallery = LocalOpenGallery.current
    val picture = http(shownProfile.picture)
    val galleryImages = remember(picture, banner) { listOfNotNull(picture, banner).distinct() }
    fun openImage(url: String) { openGallery(galleryImages, galleryImages.indexOf(url)) }
    ResolveMentionProfiles(event)
    if (identityInfoOpen) Nip05Explanation(fields.nip05, event.pubkey, indicators.verified, checkingIdentity) {
        identityInfoOpen = false
    }
    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF2D2D2D), border = BorderStroke(1.dp, Color(0xFF3D3D3D))) {
        Column(Modifier.fillMaxWidth()) {
            banner?.let { url -> AsyncImage(url, "Profile banner", Modifier.fillMaxWidth().height(112.dp)
                .clickable(onClickLabel = "Open banner in gallery") { openImage(url) }, contentScale = ContentScale.Crop) }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(shownProfile, event.pubkey, { if (picture != null) openImage(picture) else author() }, size = 48)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable(onClick = author)) {
                        Text(shownProfile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (fields.name.isNotBlank() && fields.name != shownProfile.name) Text("@${fields.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ActionIcon(Icons.Outlined.ContentCopy, "Copy public key", { clipboard.setText(AnnotatedString(npub)) })
                }
                if (shownProfile.about.isNotBlank()) Text(linkedText(shownProfile.about, event, profiles, onNavigate = onNavigate), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = Color(0xFF3D3D3D))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val identityStatus = when {
                    fields.nip05.isBlank() -> "No NIP-05 identity"
                    indicators.verified == true -> "Verified NIP-05: ${fields.nip05}"
                    indicators.verified == false -> "NIP-05 does not match this profile: ${fields.nip05}"
                    else -> "NIP-05 not verified: ${fields.nip05}"
                }
                ProfileStatusIcon(when {
                    fields.nip05.isBlank() -> Icons.Outlined.ErrorOutline
                    indicators.verified == false -> Icons.Outlined.HighlightOff
                    indicators.verified == true && rootIdentity -> Icons.Outlined.DoneAll
                    else -> Icons.Outlined.Badge
                }, identityStatus, identityColor) {
                    identityInfoOpen = true
                }
                if (fields.nip05.isNotBlank()) {
                    if (indicators.verified == true && rootIdentity) {
                        Icon(Icons.Outlined.Badge, null, Modifier.size(16.dp), tint = identityColor)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(domain.ifBlank { fields.nip05 }, Modifier.weight(1f).clickable(onClick = author), color = identityColor,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Spacer(Modifier.weight(1f))
                lightning?.let { address ->
                    ProfileStatusIcon(Icons.Outlined.Bolt, "$lightningStatus: $address. Search this address", lightningColor) {
                        val term = address.replace('"', ' ').trim()
                        onNavigate("kind:0 \"$term\" OR kind:1 \"$term\"")
                    }
                }
                ActionIcon(Icons.Outlined.PhoneAndroid, "Open profile in app", { openInNostrApp(context, event) })
                Box {
                    ActionIcon(Icons.Outlined.MoreHoriz, "Profile searches and actions", { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                        fun navigate(query: String) { menuOpen = false; onNavigate(query) }
                        ProfileSearchMenu(npub, ::navigate)
                        HorizontalDivider()
                        if (domain.isNotBlank()) DropdownMenuItem(text = { Text("p:$domain") },
                            leadingIcon = { Icon(Icons.Outlined.Group, null, tint = identityColor) }, onClick = { navigate("p:$domain") })
                        website?.let { url -> DropdownMenuItem(text = { Text("Website") }, leadingIcon = { Icon(Icons.Outlined.Home, null) },
                            onClick = { menuOpen = false; openUrl(context, url) }) }
                        DropdownMenuItem(text = { Text("Open in browser") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = { menuOpen = false; openUrl(context, "https://njump.to/$npub") })
                        DropdownMenuItem(text = { Text("Event details") }, leadingIcon = { Icon(Icons.Outlined.DataObject, null) },
                            onClick = { menuOpen = false; onOpen() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileStatusIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = tint) }
    }
}
