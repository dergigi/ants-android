package org.dergigi.ants

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CrashReportState(val report: String? = null, val sending: Boolean = false, val failed: Boolean = false, val sent: Boolean = false)

internal class CrashReportModel : ViewModel() {
    private val mutable = MutableStateFlow(CrashReportState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { mutable.value = CrashReportState(report = withContext(Dispatchers.IO) { CrashReporter.pending() }) } }
    fun dismiss() {
        if (state.value.sending) return
        mutable.value = CrashReportState()
        viewModelScope.launch(Dispatchers.IO) { CrashReporter.clear() }
    }
    fun send(recipient: String) {
        val report = state.value.report ?: return
        if (state.value.sending) return
        mutable.update { it.copy(sending = true, failed = false) }
        viewModelScope.launch {
            try {
                val success = CrashReporter.send(report, recipient)
                if (success) {
                    withContext(Dispatchers.IO) { CrashReporter.clear() }
                    mutable.value = CrashReportState(sent = true)
                } else mutable.update { it.copy(failed = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(failed = true) }
            } finally { mutable.update { it.copy(sending = false) } }
        }
    }
    fun acknowledgeSent() { mutable.update { it.copy(sent = false) } }
}

@Composable
internal fun CrashReportPrompt(recipient: String, model: CrashReportModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.sent) {
        if (state.sent) {
            Toast.makeText(context, "Crash report sent", Toast.LENGTH_SHORT).show()
            model.acknowledgeSent()
        }
    }
    val report = state.report ?: return
    AlertDialog(onDismissRequest = model::dismiss,
        title = { Text("ants crashed last time") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Send this report to ants as an encrypted Nostr DM? It uses a one-time key, not your account. Nothing is sent until you tap Send.")
            SelectionContainer {
                Text(report, Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
            if (state.failed) Text("Could not send. Try again or copy the report.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { model.send(recipient) }, enabled = !state.sending) {
            if (state.sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Send")
        } },
        dismissButton = { Row {
            IconButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Icon(Icons.Outlined.ContentCopy, "Copy crash report") }
            IconButton(onClick = model::dismiss, enabled = !state.sending) { Icon(Icons.Outlined.Close, "Dismiss crash report") }
        } },
    )
}
