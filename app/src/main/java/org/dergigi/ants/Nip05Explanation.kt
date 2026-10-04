package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal enum class Nip05Badge(val icon: ImageVector, val tint: Color?, val explanation: String) {
    Missing(Icons.Outlined.ErrorOutline, Color(0xFFFACC15), "No address"),
    Mismatch(Icons.Outlined.HighlightOff, Color(0xFFF87171), "Key mismatch"),
    Match(Icons.Outlined.Badge, Color(0xFF4ADE80), "Key matches"),
    DomainMatch(Icons.Outlined.DoneAll, Color(0xFF4ADE80), "Domain identity matches (_@domain)"),
    Unknown(Icons.Outlined.Badge, null, "Pending / unavailable"),
}

internal fun nip05Badge(address: String, verified: Boolean?): Nip05Badge = when {
    address.isBlank() -> Nip05Badge.Missing
    verified == false -> Nip05Badge.Mismatch
    verified == true && normalizedNip05(address).startsWith("_@") -> Nip05Badge.DomainMatch
    verified == true -> Nip05Badge.Match
    else -> Nip05Badge.Unknown
}

/** Shared with the verifier so the explanation shows the endpoint actually used. */
internal fun nip05LookupUrl(value: String): HttpUrl {
    val address = normalizedNip05(value)
    val name = address.substringBefore('@')
    val domain = address.substringAfter('@')
    require(name.isNotBlank() && '.' in domain && domain.none { it in "/:@?#" })
    return "https://$domain/.well-known/nostr.json".toHttpUrl().newBuilder().addQueryParameter("name", name).build()
}

@Composable
internal fun Nip05Explanation(address: String, pubkey: String, verified: Boolean?, checking: Boolean, onNavigate: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val missing = address.isBlank()
    val status = when {
        missing -> "No address set. NIP-05 is optional."
        checking -> "Checking the domain record…"
        verified == true -> "The address matches this profile’s key."
        verified == false -> "The domain record points to another key."
        else -> "Could not verify this address."
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("NIP-05") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!missing) ProfileAddressField("NIP-05", address) {
                    onDismiss(); onNavigate("p:${JSONObject.quote(address)}")
                }
                Text(status)
                Nip05Badge.entries.forEach { badge ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(badge.icon, null, Modifier.size(20.dp), tint = badge.tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(badge.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!missing) {
                    val endpoint = runCatching { nip05LookupUrl(address).toString() }.getOrNull()
                    if (endpoint != null) SelectionContainer {
                        Text("Checked\n$endpoint", style = MaterialTheme.typography.bodySmall)
                    }
                    ProfileAddressField("Public key", Nip19.npubEncode(pubkey))
                }
            }
        },
        confirmButton = { ActionIcon(Icons.Outlined.Close, "Close", onDismiss) },
        dismissButton = { Row {
            ActionIcon(Icons.Outlined.PersonSearch, "Search profile", { onDismiss(); onNavigate("p:${Nip19.npubEncode(pubkey)}") })
            ActionIcon(Icons.Outlined.Info, "About NIP-05", { openUrl(context, "https://github.com/nostr-protocol/nips/blob/master/05.md") })
        } },
    )
}
