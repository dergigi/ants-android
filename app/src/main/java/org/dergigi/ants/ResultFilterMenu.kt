package org.dergigi.ants

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ResultFilterButton(settings: ContentFilterSettings, query: String, total: Int, visible: Int,
    languageCounts: Map<String, Int> = emptyMap(),
    onSearchLanguages: () -> Unit = {},
    onChange: (ContentFilterSettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var languagesOpen by remember(query) { mutableStateOf(false) }
    val mixedLanguages = languageCounts.keys.count { it != UNKNOWN_LANGUAGE } > 1
    val selection = settings.languages.forQuery(query)
    if (languagesOpen && mixedLanguages) LanguagePicker(languageCounts, selection,
        enabled = settings.mode != ResultFilterMode.NEVER,
        onChange = { onChange(settings.copy(languages = it)) },
        onSearch = { languagesOpen = false; expanded = false; onSearchLanguages() },
        onDismiss = { languagesOpen = false })
    Box {
        ActionIcon(Icons.Outlined.FilterAlt, "Filter results · $visible / $total", { expanded = true }, selected = settings.enabled(total) || settings.mode != ResultFilterMode.NEVER &&
            (settings.hideEncrypted || mixedLanguages && (selection.excluded.isNotEmpty() || !selection.keepUnknown)))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 560.dp)) {
            Column(Modifier.width(300.dp).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$visible / $total", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    ActionIcon(Icons.Outlined.RestartAlt, "Restore default filters", { onChange(ContentFilterSettings()) })
                    ActionIcon(Icons.Outlined.FilterAltOff, "Clear all filters", { onChange(ContentFilterSettings.cleared()) })
                    ActionIcon(Icons.Outlined.Close, "Close filters", { expanded = false })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ResultFilterMode.entries.forEach { mode ->
                        FilterChip(selected = settings.mode == mode, onClick = { onChange(settings.copy(mode = mode)) }, label = { Text(mode.label) })
                    }
                }
                if (settings.mode == ResultFilterMode.SMART) Text("Smart: 69+ results", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val enabled = settings.mode != ResultFilterMode.NEVER
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = settings.fuzzyEnabled, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Enable text filter" }, onCheckedChange = {
                        onChange(settings.copy(fuzzyEnabled = it, resultFilter = if (it) settings.resultFilter else ""))
                    })
                    OutlinedTextField(settings.resultFilter, { onChange(settings.copy(resultFilter = it)) },
                        modifier = Modifier.weight(1f), enabled = enabled && settings.fuzzyEnabled, singleLine = true,
                        label = { Text("Filter text") }, textStyle = MaterialTheme.typography.bodySmall)
                }
                if (mixedLanguages) TextButton(onClick = { languagesOpen = true }) {
                    Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Languages · ${languageCounts.keys.count { it != UNKNOWN_LANGUAGE && it !in selection.excluded }} / ${languageCounts.keys.count { it != UNKNOWN_LANGUAGE }}")
                }
                FilterToggle("Valid NIP-05", settings.verifiedOnly, enabled) { onChange(settings.copy(verifiedOnly = it)) }
                val emojiDisabled = settings.emojiAutoDisabled(query)
                FilterLimit(if (emojiDisabled) "Emojis · auto-off" else "Emojis", settings.maxEmojis, 3, 9, enabled && !emojiDisabled, emojiDisabled) {
                    onChange(settings.copy(maxEmojis = it))
                }
                FilterLimit("Hashtags", settings.maxHashtags, 3, 9, enabled) { onChange(settings.copy(maxHashtags = it)) }
                FilterLimit("Mentions", settings.maxMentions, 6, 20, enabled) { onChange(settings.copy(maxMentions = it)) }
                FilterToggle("Hide external links", settings.hideLinks, enabled) { onChange(settings.copy(hideLinks = it)) }
                FilterToggle("Hide bridged accounts", settings.hideBridged, enabled) { onChange(settings.copy(hideBridged = it)) }
                FilterToggle("Hide encrypted content", settings.hideEncrypted, enabled) { onChange(settings.copy(hideEncrypted = it)) }
                FilterToggle("Hide bots", settings.hideBots, enabled) { onChange(settings.copy(hideBots = it)) }
                FilterToggle("Hide NSFW", settings.hideNsfw, enabled) { onChange(settings.copy(hideNsfw = it)) }
            }
        }
    }
}

@Composable
private fun FilterToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(12.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

@Composable
private fun FilterLimit(label: String, value: Int?, default: Int, max: Int, enabled: Boolean, autoDisabled: Boolean = false, onChange: (Int?) -> Unit) {
    var rememberedLimit by remember { mutableIntStateOf(value ?: default) }
    LaunchedEffect(value) { if (value != null) rememberedLimit = value }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(value != null && !autoDisabled, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Limit $label" },
            onCheckedChange = { onChange(if (it) rememberedLimit else null) })
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
        OutlinedTextField((value ?: rememberedLimit).toString(), { text ->
            val number = if (text.isEmpty()) 0 else text.toIntOrNull()
            if (number != null && number in 0..max) {
                rememberedLimit = number
                if (value != null) onChange(number)
            }
        }, Modifier.width(72.dp).semantics { contentDescription = "Maximum $label" }, enabled = enabled, singleLine = true, label = { Text("Max") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LanguagePicker(counts: Map<String, Int>, selection: LanguageSelection, enabled: Boolean,
    onChange: (LanguageSelection) -> Unit, onSearch: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Languages", Modifier.weight(1f))
            ActionIcon(Icons.Outlined.SelectAll, "Show all languages", { onChange(selection.copy(excluded = emptySet(), keepUnknown = true)) })
            ActionIcon(Icons.Outlined.Close, "Close languages", onDismiss)
        }
    }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            counts.keys.filter { it != UNKNOWN_LANGUAGE }.sortedBy(::languageName).forEach { code ->
                FilterToggle("${languageName(code)} · ${counts[code]}", code !in selection.excluded, enabled) { checked ->
                    onChange(selection.copy(excluded = if (checked) selection.excluded - code else selection.excluded + code))
                }
            }
            if (UNKNOWN_LANGUAGE in counts) FilterToggle("Unknown · ${counts[UNKNOWN_LANGUAGE]}", selection.keepUnknown, enabled) {
                onChange(selection.copy(keepUnknown = it))
            }
        }
    }, confirmButton = {
        if (enabled && selection.excluded.isNotEmpty() && counts.keys.any { it != UNKNOWN_LANGUAGE && it !in selection.excluded })
            ActionIcon(Icons.Outlined.Search, "Search selected languages", onSearch)
    })
}
