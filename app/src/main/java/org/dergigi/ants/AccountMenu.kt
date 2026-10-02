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
    Box {
        IconButton(onClick = { if (pubkey == null) onSearch("/login") else expanded = true }) {
            if (pubkey == null) Icon(Icons.Outlined.Login, "Log in with signer")
            else AccountAvatar(profile, "Signed in as ${profile?.name ?: pubkey.take(12)}. Open account menu")
        }
        DropdownMenu(expanded = expanded && pubkey != null, onDismissRequest = { expanded = false }) {
            Text(profile?.name ?: "Signed in", Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 240.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            DropdownMenuItem(text = { Text("Profile") }, leadingIcon = { Icon(Icons.Outlined.AccountCircle, null) }, onClick = { expanded = false; pubkey?.let { onSearch("p:${Nip19.npubEncode(it)}") } })
            DropdownMenuItem(text = { Text("My posts") }, leadingIcon = { Icon(Icons.Outlined.Notes, null) }, onClick = { expanded = false; onSearch("by:@me") })
            DropdownMenuItem(text = { Text("Mentions") }, leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) }, onClick = { expanded = false; onSearch("mentions:@me") })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Log out") }, leadingIcon = { Icon(Icons.Outlined.Logout, null) }, onClick = { expanded = false; onSearch("/logout") })
        }
    }

}

@Composable
private fun AccountAvatar(profile: Profile?, description: String?) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.AccountCircle, description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize())
        profile?.picture?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
