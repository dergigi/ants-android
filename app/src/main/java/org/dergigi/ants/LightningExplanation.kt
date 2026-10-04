package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
        checking -> "Checking activity…"
        indicators.sentZap && indicators.sentNutzap -> "Sent zaps and nutzaps found."
        indicators.sentZap -> "Sent zaps found."
        indicators.sentNutzap -> "Sent nutzaps found."
        else -> "No outgoing activity found."
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Lightning & nutzaps") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileAddressField(if (address.startsWith("lnurl", ignoreCase = true)) "LNURL" else "Lightning", address) {
                    val term = JSONObject.quote(address)
                    onDismiss(); onNavigate("kind:0 $term OR kind:1 $term")
                }
                Text(status)
                ActivityLegendRow(zapActivityColor, "Zaps")
                ActivityLegendRow(nutzapActivityColor, "Nutzaps")
                ActivityLegendRow(combinedZapActivityColor, "Both")
                ActivityLegendRow(MaterialTheme.colorScheme.onSurfaceVariant, "Pending / not found")
                Text("Zaps: monetary transactions over Lightning.\nNutzaps: monetary transactions using Cashu ecash.")
                Text("Checked: outgoing zap receipts and nutzaps on public relays. Payments and address availability are unchecked.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { ActionIcon(Icons.Outlined.Close, "Close", onDismiss) },
        dismissButton = { Row {
            ActionIcon(Icons.Outlined.Bolt, "About Lightning zaps", { openUrl(context, "https://github.com/nostr-protocol/nips/blob/master/57.md") })
            ActionIcon(Icons.Outlined.Toll, "About Cashu nutzaps", { openUrl(context, "https://github.com/nostr-protocol/nips/blob/master/61.md") })
        } },
    )
}

@Composable
private fun ActivityLegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Bolt, null, Modifier.size(20.dp), tint = color)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
