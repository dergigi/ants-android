package org.dergigi.ants

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal fun prettyEventJson(event: Nip01Event): String = JSONObject(event.toJsonString()).toString(2)

@Composable
internal fun JsonContent(event: Nip01Event) {
    val json = remember(event) { highlightJson(prettyEventJson(event)) }
    Text(
        json,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        softWrap = false,
        color = Color(0xFFCBD5E1),
    )
}

// Scan strings as whole tokens so escaped quotes and JSON inside content stay literal.
private fun highlightJson(json: String): AnnotatedString {
    val result = AnnotatedString.Builder(json)
    var index = 0
    while (index < json.length) {
        val start = index
        val color = when (json[index]) {
            '"' -> {
                index++
                while (index < json.length) {
                    when (json[index++]) {
                        '\\' -> if (index < json.length) index++
                        '"' -> break
                    }
                }
                var next = index
                while (next < json.length && json[next].isWhitespace()) next++
                if (json.getOrNull(next) == ':') Color(0xFF60A5FA) else Color(0xFF86EFAC)
            }
            '-', in '0'..'9' -> {
                index++
                while (index < json.length && json[index] in "0123456789.eE+-") index++
                Color(0xFFFBBF24)
            }
            't', 'f', 'n' -> {
                while (index < json.length && json[index].isLetter()) index++
                Color(0xFFC084FC)
            }
            else -> { index++; null }
        }
        if (color != null) result.addStyle(SpanStyle(color = color), start, index)
    }
    return result.toAnnotatedString()
}
