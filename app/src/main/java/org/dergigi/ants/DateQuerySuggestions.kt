package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal val relativeDateSuggestions = listOf("12h", "1d", "3d", "1w", "2w", "1m", "1y")
internal fun dateSuggestions(token: QueryToken): List<String> =
    if (token.field in setOf("since", "until")) relativeDateSuggestions.filter { it.startsWith(token.value) }.map { "${token.field}:$it" }
    else emptyList()

internal data class DateQueryRequest(val text: String, val token: QueryToken)
internal fun queryDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

@Composable
internal fun DateSuggestionMenu(token: QueryToken, onSelect: (String) -> Unit, onCalendar: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp), tonalElevation = 4.dp, shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(dateSuggestions(token)) { choice ->
                    TextButton(onClick = { onSelect(choice) }) {
                        Text(choice, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            ActionIcon(Icons.Outlined.CalendarMonth, "Choose ${token.field} date", onCalendar)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueryDatePicker(request: DateQueryRequest, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val initial = remember(request) {
        runCatching { LocalDate.parse(request.text.substring(request.token.start, request.token.end).substringAfter(':'))
            .takeIf { it.year in 1900..2100 }?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() }.getOrNull()
    }
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
            picker.selectedDateMillis?.let { onSelect("${request.token.field}:${queryDate(it)}") }
        }) { Text("Select") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) {
        DatePicker(picker, title = { Text(if (request.token.field == "since") "Since" else "Until", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
