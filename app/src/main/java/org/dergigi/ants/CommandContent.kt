package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun CommandRow(query: String, description: String, onSearch: (String) -> Unit) {
    Surface(onClick = { onSearch(query) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(query, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.NorthEast, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun HelpContent(state: SearchState, onSearch: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tap any command or example to run it. Searches go directly to Nostr relays; text matching and coverage vary.")
        slashCommands.forEach { CommandRow(it.name, it.description, onSearch) }
        Text("TRY A SEARCH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf("bitcoin OR lightning", "#asknostr", "GM by:dergigi", "is:highlight", "Bitcoin has:image", "is:reaction by:dergigi").forEach { query ->
            val example = searchExamples.first { it.query == query }
            CommandRow(query, example.description, onSearch)
        }
        if (state.pubkey != null) CommandRow("mentions:@me", "Events tagging your connected account", onSearch)
        Text("Tap hashtags, mentions, quoted notes, and sources to explore. Back restores your place; the ants logo returns home. New results wait behind the jump-to-newest icon while you read.", style = MaterialTheme.typography.bodySmall)
        Text("Dates use YYYY-MM-DD in UTC. Grouped boolean expressions, relative dates, arbitrary username resolution, posting, and zaps aren't included yet. /examples lists queries supported by this Android version.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun LazyListScope.commandItems(state: SearchState, onSearch: (String) -> Unit, onConnect: () -> Unit) {
    val command = state.command ?: return
    item(key = "command-heading") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Terminal, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp)); Text("/$command", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
            if (state.commandBusy) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
        }
    }
    when (command) {
        "help" -> item { HelpContent(state, onSearch) }
        "examples" -> {
            item { Text("Tap a query to search. These examples work with Android's current syntax.", style = MaterialTheme.typography.bodySmall) }
            searchExamples.filter { !it.needsLogin || state.pubkey != null }.groupBy { it.section }.forEach { (section, examples) ->
                item(key = "section-$section") { Text(section, style = MaterialTheme.typography.titleSmall) }
                items(examples, key = { it.query }) { CommandRow(it.query, it.description, onSearch) }
            }
            item { CommandRow("/help", "All commands and search help", onSearch) }
        }
        "kinds" -> {
            item { Text("Tap a shortcut to search content with a native display. Encrypted events and unsupported event types are excluded.", style = MaterialTheme.typography.bodySmall) }
            items(kindAliases.entries.filter { entry -> entry.value.all { it in renderedKinds } }, key = { it.key }) { alias -> CommandRow("is:${alias.key}", alias.value.joinToString(" OR ") { "kind:$it" }, onSearch) }
        }
        "login" -> item { LoginContent(state, onConnect, onSearch) }
        "logout", "clear" -> {
            item { Text(state.commandMessage.orEmpty()) }
            item { CommandRow("/examples", "Find something new", onSearch) }
        }
        "tutorial" -> item {
            val context = LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The same tutorial event used by web ants.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { openUrl(context, "https://njump.to/$tutorialPointer") }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open tutorial in browser") }

                }
            }
        }
        else -> {
            item { Text(state.commandMessage.orEmpty(), color = MaterialTheme.colorScheme.error) }
            items(slashCommands) { CommandRow(it.name, it.description, onSearch) }
        }
    }
}

@Composable
private fun LoginContent(state: SearchState, onConnect: () -> Unit, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Connect with Amber or another Android signer. Your private key stays in your signer; ants only stores your public key and the signer app's name.")
        state.commandMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val pubkey = state.pubkey
        if (pubkey != null) {
            CommandRow("by:@me", "Search your events", onSearch)
            CommandRow("mentions:@me", "Find events tagging you", onSearch)
            Text(Nip19.npubEncode(pubkey), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            CommandRow("/logout", "Disconnect this account", onSearch)
        } else {
            FilledTonalButton(onClick = onConnect, enabled = !state.commandBusy) { Icon(Icons.Outlined.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Connect signer") }
            TextButton(onClick = { openUrl(context, "https://zapstore.dev/apps/com.greenart7c3.nostrsigner") }) { Text("Get Amber") }
        }
        Text("This connection enables @me searches. The app remains read-only: it does not request permission to sign events.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
