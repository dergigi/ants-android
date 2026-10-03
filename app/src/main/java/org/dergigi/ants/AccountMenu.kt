package org.dergigi.ants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
        DropdownMenu(expanded = expanded && pubkey != null, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            ProfileSearchMenu("@me") { query -> expanded = false; onSearch(query) }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("/logout") }, leadingIcon = { Icon(Icons.Outlined.Logout, null) }, onClick = { expanded = false; onSearch("/logout") })
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
