package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun CommandRow(query: String, onSearch: (String) -> Unit) {
    Text(query,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button) { onSearch(query) }.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
}

@Composable
internal fun HelpContent(state: SearchState, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    val terminalText = Color(0xFFBDCEDB)
    Surface(color = Color(0xFF2C2C2C), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFF3D3D3D))) {
        Surface(Modifier.fillMaxWidth().padding(14.dp), color = Color(0xFF001827), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color(0xFF34424B))) {
            Column(Modifier.padding(12.dp)) {
                slashCommands.forEach { command ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { onSearch(command.name) }.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(command.name, Modifier.width(88.dp), color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        Text(command.description, Modifier.weight(1f), color = terminalText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
                Text("v${BuildConfig.VERSION_NAME} ${BuildConfig.GIT_COMMIT.take(7)}",
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open GitHub release") {
                        openUrl(context, "https://github.com/dergigi/ants-android/releases/tag/v${BuildConfig.VERSION_NAME}")
                    }.padding(top = 20.dp, bottom = 8.dp), color = terminalText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}

internal fun LazyListScope.commandItems(state: SearchState, onSearch: (String) -> Unit, onConnect: () -> Unit) {
    val command = state.command ?: return
    when (command) {
        "help" -> item { HelpContent(state, onSearch) }
        "examples" -> {
            items(searchExamples.filter { !it.needsLogin || state.pubkey != null }, key = { it.query }) {
                CommandRow(it.query, onSearch)
            }
        }
        "kinds" -> {
            items(kindAliases.entries.filter { entry -> entry.value.all { it in renderedKinds } }, key = { it.key }) { alias -> CommandRow("is:${alias.key}", onSearch) }
        }
        "login" -> item { LoginContent(state, onConnect, onSearch) }
        "logout", "clear" -> {
            item { Text(state.commandMessage.orEmpty()) }
            item { CommandRow("/examples", onSearch) }
        }
        "tutorial" -> Unit
        else -> {
            item { Text(state.commandMessage.orEmpty(), color = MaterialTheme.colorScheme.error) }
            items(slashCommands) { CommandRow(it.name, onSearch) }
        }
    }
}

@Composable
private fun LoginContent(state: SearchState, onConnect: () -> Unit, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        state.commandMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val pubkey = state.pubkey
        if (pubkey != null) {
            CommandRow("by:@me", onSearch)
            CommandRow("mentions:@me", onSearch)
            Text(Nip19.npubEncode(pubkey), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            CommandRow("/logout", onSearch)
        } else {
            FilledTonalButton(onClick = onConnect, enabled = !state.commandBusy) { Icon(Icons.Outlined.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Connect signer") }
            TextButton(onClick = { openUrl(context, "https://zapstore.dev/apps/com.greenart7c3.nostrsigner") }) { Text("Get Amber") }
        }
    }
}
