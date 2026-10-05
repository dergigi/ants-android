package org.dergigi.ants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Inline dropdown keeps the text field and keyboard focused while suggestions change. */
@Composable
internal fun QuerySuggestionMenu(suggestions: QuerySuggestions, onSelect: (String) -> Unit) {
    val scroll = rememberLazyListState()
    LaunchedEffect(suggestions.choices) { scroll.scrollToItem(0) }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp), tonalElevation = 4.dp, shadowElevation = 2.dp) {
        LazyColumn(Modifier.heightIn(max = 208.dp), state = scroll) {
            items(suggestions.choices, key = { it }) { choice ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Complete $choice") { onSelect(choice) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(choice, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
