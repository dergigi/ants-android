package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal val zapActivityColor = Color(0xFFFEF08A)
internal val nutzapActivityColor = Color(0xFFC084FC)
internal val combinedZapActivityColor = Color(0xFF4ADE80)

internal fun ProfileIndicators.lightningColor(neutral: Color): Color = when {
    sentZap && sentNutzap -> combinedZapActivityColor
    sentZap -> zapActivityColor
    sentNutzap -> nutzapActivityColor
    else -> neutral
}

@Composable
internal fun LightningExplanation(address: String, indicators: ProfileIndicators, checking: Boolean, onNavigate: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val status = when {
        checking -> "Looking for public zap and nutzap activity…"
        indicators.sentZap && indicators.sentNutzap -> "Found public activity for both zaps and nutzaps sent by this profile."
        indicators.sentZap -> "Found public zap activity for this profile. No sent nutzap activity was found in this check."
        indicators.sentNutzap -> "Found public nutzap activity for this profile. No sent zap activity was found in this check."
        else -> "No sent zap or nutzap activity was found in this check. Events may be private, missing from the queried relays, or unavailable."
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Lightning & nutzaps") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionContainer { Text("Published Lightning address / LNURL\n$address") }
                TextButton(onClick = {
                    val term = JSONObject.quote(address)
                    onDismiss()
                    onNavigate("kind:0 $term OR kind:1 $term")
                }) { Text("Search this address") }
                Text(status)
                Text("Icon colors", style = MaterialTheme.typography.titleSmall)
                ActivityLegendRow(zapActivityColor, "Yellow — sent zap activity found")
                ActivityLegendRow(nutzapActivityColor, "Purple — sent nutzap activity found")
                ActivityLegendRow(combinedZapActivityColor, "Green — both types found")
                ActivityLegendRow(MaterialTheme.colorScheme.onSurfaceVariant, "Gray — address published; activity pending or not found")
                Text("What are they?", style = MaterialTheme.typography.titleSmall)
                Text("Zaps are tips using Bitcoin’s Lightning Network, with public receipts on Nostr. Nutzaps are tips sent as Cashu ecash tokens, issued by a mint. Their value depends on the mint honoring them.")
                Text("What was checked", style = MaterialTheme.typography.titleSmall)
                Text("We look for public zap receipts (kind 9735) naming this profile as sender, and nutzap events (kind 9321) signed by this profile. Checks use ${generalRelays.joinToString { it.removePrefix("wss://") }} and may use cached results.",
                    style = MaterialTheme.typography.bodySmall)
                Text("The colors indicate observed sending activity, not a balance, identity verification, or proof of payment. This check does not test whether the published address can receive payments.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { openUrl(context, "https://github.com/nostr-protocol/nips/blob/master/57.md") }) { Text("Learn more about Lightning zaps") }
                TextButton(onClick = { openUrl(context, "https://github.com/nostr-protocol/nips/blob/master/61.md") }) { Text("Learn more about Cashu nutzaps") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ActivityLegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Bolt, null, Modifier.size(20.dp), tint = color)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
