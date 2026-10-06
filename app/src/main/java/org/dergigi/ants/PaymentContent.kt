package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal

private data class PaymentPreview(val amount: String?, val sender: String?, val recipient: String?, val comment: String, val mint: String?)
private val invoiceAmount = Regex("^ln(?:bcrt|tbs|bc|tb)([0-9]+)([munp]?)1", RegexOption.IGNORE_CASE)

// Display only: neither a signed receipt nor an amount proves a settled payment.
private fun invoiceSats(invoice: String): String? = runCatching {
    val match = invoiceAmount.find(invoice.trim()) ?: return null
    val scale = when (match.groupValues[2].lowercase()) { "m" -> 5; "u" -> 2; "n" -> -1; "p" -> -4; else -> 8 }
    BigDecimal(match.groupValues[1].takeIf { it.length <= 18 } ?: return null).scaleByPowerOfTen(scale)
        .takeIf { it.signum() > 0 }?.stripTrailingZeros()?.toPlainString()
}.getOrNull()

private fun paymentPreview(event: Nip01Event): PaymentPreview {
    val recipient = event.tagValue("p")?.let(Nip19::normalizePubkey)
    if (event.kind == 9735) {
        val request = runCatching { Nip01Event.parse(JSONObject(event.tagValue("description").orEmpty())) }.getOrNull()?.takeIf {
            it.kind == 9734 && it.verify() && it.tagValue("p") == recipient &&
                it.tagValue("e") == event.tagValue("e") && it.tagValue("a") == event.tagValue("a")
        }
        val anonymous = request?.tags?.any { it.firstOrNull() == "anon" } == true
        return PaymentPreview(event.tagValue("bolt11")?.let(::invoiceSats)?.let { "$it sats" },
            request?.pubkey?.takeUnless { anonymous }, recipient, request?.content.orEmpty(), null)
    }
    val proofs = event.tags.filter { it.firstOrNull() == "proof" }
    val amount = runCatching {
        require(proofs.isNotEmpty() && proofs.size <= 1000)
        proofs.fold(BigDecimal.ZERO) { sum, tag ->
            val raw = JSONObject(tag.getOrNull(1).orEmpty()).get("amount").toString()
            require(raw.length <= 18 && raw.all(Char::isDigit))
            val value = BigDecimal(raw)
            require(value.signum() > 0)
            sum + value
        }.stripTrailingZeros().toPlainString()
    }.getOrNull()
    val unit = event.tagValue("unit").orEmpty().ifBlank { "sat" }.take(12)
    return PaymentPreview(amount?.let { "$it $unit" }, event.pubkey, recipient, event.content,
        event.tagValue("u")?.takeIf { it.startsWith("https://") || it.startsWith("http://") })
}

@Composable
internal fun PaymentContent(event: Nip01Event, profiles: Map<String, Profile>, compact: Boolean, onNavigate: (String) -> Unit) {
    val preview by produceState<PaymentPreview?>(null, event.id) {
        value = withContext(Dispatchers.Default) { paymentPreview(event) }
    }
    val payment = preview ?: return
    val loadProfiles = LocalLoadMentionProfiles.current
    val pageId = LocalThreadState.current.state.pageId
    LaunchedEffect(event.id, pageId, payment.sender, payment.recipient) { loadProfiles(listOfNotNull(payment.sender, payment.recipient)) }
    val ancestors = LocalQuoteAncestors.current + event.id
    val references = remember(event.id) { taggedNoteReferences(event).take(1) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentParty(payment.sender, profiles, "Sender", "Anonymous / unknown", Modifier.weight(1f), onNavigate)
            PaymentAmount(payment, event.kind, onNavigate)
            PaymentParty(payment.recipient, profiles, "Recipient", "Unknown recipient", Modifier.weight(1f), onNavigate)
        }
        if (payment.comment.isNotBlank()) {
            if (compact) {
                val text = remember(payment.comment) { payment.comment.take(2000) }
                val navigate by rememberUpdatedState(onNavigate)
                val comment by produceState(AnnotatedString(text), event.id, text, profiles) {
                    value = withContext(Dispatchers.Default) { linkedText(text, event.copy(tags = emptyList()), profiles) { navigate(it) } }
                }
                Text(comment, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else EventContent(event.copy(kind = 1, content = payment.comment, tags = emptyList()), null, profiles, false, onNavigate)
        }
        references.forEach { reference ->
            if (!compact && reference.key !in ancestors && ancestors.size <= 2) EmbeddedNote(reference, ancestors, onNavigate)
            else EventReferenceRow(ListEntry("e", reference.key, reference.query), profiles, onNavigate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentParty(pubkey: String?, profiles: Map<String, Profile>, role: String, fallback: String,
    modifier: Modifier, onNavigate: (String) -> Unit) {
    val profile = pubkey?.let(profiles::get)
    val npub = remember(pubkey) { pubkey?.let(Nip19::npubEncode) }
    val label = profile?.name?.takeIf { it.isNotBlank() } ?: npub?.let { it.take(9) + "…" + it.takeLast(4) } ?: fallback
    val navigate = { if (npub != null) onNavigate("p:$npub") }
    TooltipBox(modifier = modifier, positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("$role: $label") } }, state = rememberTooltipState()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .clickable(enabled = pubkey != null, role = Role.Button, onClickLabel = "Open $role profile", onClick = navigate)
            .padding(vertical = 4.dp), horizontalAlignment = if (role == "Sender") Alignment.Start else Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (pubkey != null) Avatar(profile, pubkey, navigate, size = 40)
            else Icon(Icons.Outlined.PersonOutline, role, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (role == "Sender") TextAlign.Start else TextAlign.End, color = if (pubkey != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentAmount(payment: PaymentPreview, kind: Int, onNavigate: (String) -> Unit) {
    var details by remember { mutableStateOf(false) }
    val label = if (kind == 9735) "Zap receipt" else "Nutzap"
    val color = if (kind == 9735) Color(0xFFFACC15) else Color(0xFFC084FC)
    Box(Modifier.widthIn(max = 140.dp)) {
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text("$label: ${payment.amount ?: "unknown amount"}") } }, state = rememberTooltipState()) {
            Column(Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClickLabel = "Transaction details") { details = true }
                .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Outlined.Bolt, label, Modifier.size(28.dp), tint = color)
                    Text(payment.amount?.substringBeforeLast(' ') ?: "—", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color)
                }
                Text(payment.amount?.substringAfterLast(' ') ?: label, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = details, onDismissRequest = { details = false }) {
            Text("$label · ${payment.amount ?: "Unknown amount"}", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            Text("Published amount; payment not independently verified.", Modifier.widthIn(max = 260.dp).padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            payment.mint?.let { url ->
                DropdownMenuItem(text = { Text(android.net.Uri.parse(url).host ?: url.take(80)) },
                    leadingIcon = { Icon(Icons.Outlined.AccountBalance, "Mint") },
                    onClick = { details = false; onNavigate(url) })
            }
        }
    }
}
