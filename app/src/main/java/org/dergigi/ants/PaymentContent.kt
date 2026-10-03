package org.dergigi.ants

import androidx.compose.foundation.layout.*
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
    LaunchedEffect(event.id, payment.sender, payment.recipient) { loadProfiles(listOfNotNull(payment.sender, payment.recipient)) }
    val ancestors = LocalQuoteAncestors.current + event.id
    val references = remember(event.id) { taggedNoteReferences(event).take(1) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Bolt, if (event.kind == 9735) "Zap receipt" else "Nutzap", tint = if (event.kind == 9735) Color(0xFFFACC15) else Color(0xFFC084FC))
            Text(payment.amount ?: if (event.kind == 9735) "Zap receipt" else "Nutzap", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            PaymentVerificationInfo()
        }
        payment.sender?.let { EventReferenceRow(ListEntry("p", it, "p:${Nip19.npubEncode(it)}"), profiles, onNavigate) }
            ?: Text("Anonymous / unknown sender", style = MaterialTheme.typography.labelMedium)
        payment.recipient?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ArrowDownward, "Recipient", Modifier.size(16.dp))
                Box(Modifier.weight(1f)) { EventReferenceRow(ListEntry("p", it, "p:${Nip19.npubEncode(it)}"), profiles, onNavigate) }
            }
        }
        if (payment.comment.isNotBlank()) EventContent(event.copy(kind = 1, content = payment.comment, tags = emptyList()), null, profiles, compact, onNavigate)
        payment.mint?.let { url -> TextButton(onClick = { onNavigate(url) }) { Text(android.net.Uri.parse(url).host ?: url.take(80)) } }
        references.forEach { reference ->
            if (reference.key !in ancestors && ancestors.size <= 2) EmbeddedNote(reference, ancestors, onNavigate)
            else EventReferenceRow(ListEntry("e", reference.key, reference.query), profiles, onNavigate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentVerificationInfo() {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("Published amount; payment not independently verified") } }, state = rememberTooltipState()) {
        Icon(Icons.Outlined.Info, "Published amount; payment not independently verified", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
