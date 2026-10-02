package org.dergigi.ants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class QuoteState(val state: SearchState, val load: (QuoteReference) -> Unit, val open: (Nip01Event) -> Unit)
internal val LocalQuoteState = staticCompositionLocalOf<QuoteState> { error("Quote state is required") }
internal val LocalQuoteAncestors = staticCompositionLocalOf<Set<String>> { emptySet() }

@Composable
internal fun EmbeddedNote(reference: QuoteReference, ancestors: Set<String>, onNavigate: (String) -> Unit) {
    val quotes = LocalQuoteState.current
    val state = quotes.state
    val event = state.quotes[reference.key]
    val failed = reference.key in state.failedQuotes
    LaunchedEffect(state.pageId, reference.key) {
        if (event == null && !failed && reference.key !in ancestors) quotes.load(reference)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = { if (event != null) quotes.open(event) else if (failed) quotes.load(reference) else onNavigate(reference.query) }) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.FormatQuote, "Quoted note", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                if (event != null) {
                    val profile = state.profiles[event.pubkey]
                    Avatar(profile, event.pubkey, { onNavigate("p:${Nip19.npubEncode(event.pubkey)}") }, size = 22)
                    Text(profile?.name ?: Nip19.npubEncode(event.pubkey).take(12) + "…",
                        Modifier.weight(1f).clickable { onNavigate("p:${Nip19.npubEncode(event.pubkey)}") }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium)
                } else {
                    Text(if (failed) "Note unavailable" else "Loading note…", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    if (!failed) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else ActionIcon(Icons.Outlined.Refresh, "Retry quoted note", { quotes.load(reference) })
                }
                ActionIcon(Icons.Outlined.NorthEast, "Open quoted note", { if (event != null) quotes.open(event) else onNavigate(reference.query) })
            }
            if (event != null && event.id !in ancestors) {
                CompositionLocalProvider(LocalQuoteAncestors provides ancestors) {
                    when (event.kind) {
                        7 -> ReactionContent(event)
                        9802 -> HighlightContent(event, state.profiles, compact = true, onNavigate = onNavigate)
                        else -> EventContent(event, state.profiles[event.pubkey], state.profiles, compact = true, onNavigate = onNavigate)
                    }
                }
            }
        }
    }
}
