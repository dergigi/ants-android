package org.dergigi.ants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun AccountMenu(pubkey: String?, profile: Profile?, onSearch: (String) -> Unit) {
    var expanded by rememberSaveable(pubkey) { mutableStateOf(false) }
    var showProfile by rememberSaveable(pubkey) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { if (pubkey == null) onSearch("/login") else expanded = true }) {
            if (pubkey == null) Icon(Icons.Outlined.Login, "Log in with signer")
            else AccountAvatar(profile, "Signed in as ${profile?.name ?: pubkey.take(12)}. Open account menu")
        }
        DropdownMenu(expanded = expanded && pubkey != null, onDismissRequest = { expanded = false }) {
            Text(profile?.name ?: "Signed in", Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 240.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            DropdownMenuItem(text = { Text("Profile") }, leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) }, onClick = { expanded = false; showProfile = true })
            DropdownMenuItem(text = { Text("My posts") }, leadingIcon = { Icon(Icons.Outlined.Notes, null) }, onClick = { expanded = false; onSearch("by:@me") })
            DropdownMenuItem(text = { Text("Mentions") }, leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) }, onClick = { expanded = false; onSearch("mentions:@me") })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Log out") }, leadingIcon = { Icon(Icons.Outlined.Logout, null) }, onClick = { expanded = false; onSearch("/logout") })
        }
    }
    if (showProfile && pubkey != null) {
        val clipboard = LocalClipboardManager.current
        val npub = Nip19.npubEncode(pubkey)
        AlertDialog(onDismissRequest = { showProfile = false },
            title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountAvatar(profile, null)
                Text(profile?.name ?: "Your profile", maxLines = 2, overflow = TextOverflow.Ellipsis)
            } },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Signed in with an external signer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                profile?.about?.takeIf { it.isNotBlank() }?.let { SelectionContainer { Text(it) } }
                SelectionContainer { Text(npub, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionIcon(Icons.Outlined.Notes, "My posts", { showProfile = false; onSearch("by:@me") })
                    ActionIcon(Icons.Outlined.AlternateEmail, "Mentions", { showProfile = false; onSearch("mentions:@me") })
                    ActionIcon(Icons.Outlined.ContentCopy, "Copy public key", { clipboard.setText(AnnotatedString(npub)) })
                    ActionIcon(Icons.Outlined.Logout, "Log out", { showProfile = false; onSearch("/logout") })
                }
            } },
            confirmButton = { IconButton(onClick = { showProfile = false }) { Icon(Icons.Outlined.Close, "Close profile") } },
        )
    }
}

@Composable
private fun AccountAvatar(profile: Profile?, description: String?) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.AccountCircle, description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize())
        profile?.picture?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
