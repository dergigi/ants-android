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
internal fun HelpContent(onSearch: (String) -> Unit) {
    val context = LocalContext.current
    CommandTerminal {
                slashCommands.forEach { command ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { onSearch(command.name) }.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(command.name, Modifier.width(88.dp), color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        Text(command.description, Modifier.weight(1f), color = terminalText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
                Text("Search syntax", color = terminalText, fontFamily = FontFamily.Monospace)
                Text("Group with parentheses. AND and spaces bind before OR. Quote literal phrases; use \\\" for a quote inside a phrase. Repeated authors and kinds intersect; use OR for alternatives.",
                    color = terminalText, fontSize = 13.sp)
                CommandRow("(bitcoin OR lightning) by:dergigi", onSearch)
                CommandRow("by:(dergigi OR fiatjaf) kind:(1 OR 30023)", onSearch)
                Text("Use p: for profiles, site: for domains, and /kinds for shortcuts. Use by:@contacts or mentions:@contacts for your public follows after /login. Any kind:0–65535 can be searched; NOT is unsupported. Searches allow 16 nested groups and 32 expanded branches.",
                    color = terminalText, fontSize = 13.sp)
                Text("v${BuildConfig.VERSION_NAME} ${BuildConfig.GIT_COMMIT.take(7)}",
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open GitHub release") {
                        openUrl(context, "https://github.com/dergigi/ants-android/releases/tag/v${BuildConfig.VERSION_NAME}")
                    }.padding(top = 20.dp, bottom = 8.dp), color = terminalText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

internal fun LazyListScope.commandItems(state: SearchState, onSearch: (String) -> Unit, onConnect: () -> Unit, onClearHistory: () -> Unit) {
    val command = state.command ?: return
    when (command) {
        "help" -> item { HelpContent(onSearch) }
        "examples" -> {
            val examples = searchExamples.filter { !it.needsLogin || state.pubkey != null }
            terminalItems(examples.size, { "example-${examples[it].query}" }) { CommandRow(examples[it].query, onSearch) }
        }
        "history" -> {
            if (state.history.isEmpty()) item { CommandTerminal { Text("No recent searches") } }
            else terminalItems(state.history.size + 1, { if (it == 0) "clear-history" else "history-${state.history[it - 1]}" }) { index ->
                if (index == 0) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ActionIcon(Icons.Outlined.DeleteOutline, "Clear search history", onClearHistory)
                } else CommandRow(state.history[index - 1], onSearch)
            }
        }
        "kinds" -> {
            val kinds = kindAliases.entries.toList()
            terminalItems(kinds.size, { "kind-${kinds[it].key}" }) { CommandRow("is:${kinds[it].key}", onSearch) }
            item { CommandTerminal {
                Text("All event kinds (0–65535) can be searched with kind:. Other kinds use a content-and-tags view.")
                CommandRow("kind:3", onSearch)
                CommandRow("kind:10002", onSearch)
            } }
        }
        "login" -> item { CommandTerminal { LoginContent(state, onConnect, onSearch) } }
        "logout", "clear" -> item { CommandTerminal {
            Text(state.commandMessage.orEmpty())
            CommandRow("/examples", onSearch)
        } }
        "tutorial" -> Unit // The fetched tutorial is rendered in a terminal panel in the results list.
        else -> item { CommandTerminal {
            Text(state.commandMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            slashCommands.forEach { CommandRow(it.name, onSearch) }
        } }
    }
}

@Composable
private fun LoginContent(state: SearchState, onConnect: () -> Unit, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        state.commandMessage?.let { Text(it) }
        val pubkey = state.pubkey
        if (pubkey != null) {
            CommandRow("by:@me", onSearch)
            CommandRow("mentions:@me", onSearch)
            Text(Nip19.npubEncode(pubkey), fontFamily = FontFamily.Monospace)
            CommandRow("/logout", onSearch)
        } else {
            FilledTonalButton(onClick = onConnect, enabled = !state.commandBusy) { Icon(Icons.Outlined.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Connect signer") }
            TextButton(onClick = { openUrl(context, "https://zapstore.dev/apps/com.greenart7c3.nostrsigner") }) { Text("Get Amber") }
        }
    }
}
