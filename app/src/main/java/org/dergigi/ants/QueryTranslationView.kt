package org.dergigi.ants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun QueryTranslationView(translation: String, pageId: Long, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(pageId) { mutableStateOf(false) }
    var overflows by remember(pageId) { mutableStateOf(false) }
    val muted = Color(0xFF9CA3AF)
    Row(modifier.heightIn(min = 48.dp).clickable(enabled = expanded || overflows, role = Role.Button,
        onClickLabel = if (expanded) "Collapse query translation" else "Expand query translation",
        onClick = { expanded = !expanded }), verticalAlignment = Alignment.Top) {
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            Text("=", color = muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (expanded) {
            SelectionContainer(Modifier.weight(1f).padding(vertical = 15.5.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                Text(translation, color = muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
            }
        } else {
            Text(translation.replace('\n', ' '), Modifier.weight(1f).padding(vertical = 15.5.dp), color = muted, fontFamily = FontFamily.Monospace,
                fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                onTextLayout = { overflows = it.hasVisualOverflow })
        }
        Spacer(Modifier.width(6.dp))
        // Reserve the affordance width so measuring overflow does not change the text width.
        Box(Modifier.width(24.dp).height(48.dp), contentAlignment = Alignment.Center) {
            if (expanded || overflows) Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null, Modifier.size(18.dp), tint = muted)
        }
    }
}
