package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Shared with the verifier so the explanation shows the endpoint actually used. */
internal fun nip05LookupUrl(value: String): HttpUrl {
    val address = normalizedNip05(value)
    val name = address.substringBefore('@')
    val domain = address.substringAfter('@')
    require(name.isNotBlank() && '.' in domain && domain.none { it in "/:@?#" })
    return "https://$domain/.well-known/nostr.json".toHttpUrl().newBuilder().addQueryParameter("name", name).build()
}

@Composable
internal fun Nip05Explanation(address: String, pubkey: String, verified: Boolean?, checking: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val missing = address.isBlank()
    val title = when {
        missing -> "No NIP-05 identity"
        checking -> "Checking NIP-05 identity"
        verified == true -> "NIP-05 identity matches"
        verified == false -> "NIP-05 identity mismatch"
        else -> "NIP-05 could not be verified"
    }
    val explanation = when {
        missing -> "This profile has not published a NIP-05 address, so no domain lookup was performed. NIP-05 is optional; a missing address does not mean a profile is fake."
        checking -> "The identity check is in progress. We compare the public key listed by the address’s domain with this profile’s public key."
        verified == true -> "The domain’s entry returned a public key that matches this profile. This confirms the address-to-key link, not the person’s real-world identity."
        verified == false -> "The domain’s entry returned a different public key. It does not confirm the NIP-05 address claimed by this profile."
        else -> "We could not obtain a usable public-key mapping. The domain may be unavailable, or its entry may be missing or invalid. This is not a confirmed identity mismatch."
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(explanation)
                if (!missing) {
                    SelectionContainer { Text("Claimed address\n$address") }
                    val endpoint = runCatching { nip05LookupUrl(address).toString() }.getOrNull()
                    if (endpoint != null) {
                        SelectionContainer { Text("Lookup endpoint\n$endpoint", style = MaterialTheme.typography.bodySmall) }
                        Text("The names entry for “${normalizedNip05(address).substringBefore('@')}” is compared with the public key below.", style = MaterialTheme.typography.bodySmall)
                    } else Text("The claimed address does not form a valid lookup endpoint.", style = MaterialTheme.typography.bodySmall)
                    SelectionContainer { Text("Profile public key\n${Nip19.npubEncode(pubkey)}", style = MaterialTheme.typography.bodySmall) }
                    Text("Checks are cached temporarily. Use /clear and reopen the profile to check again.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = {
            openUrl(context, "https://github.com/nostr-protocol/nips/blob/master/05.md")
        }) { Text("Learn more about NIP-05") } },
    )
}
