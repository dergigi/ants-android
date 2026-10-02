package org.dergigi.ants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val terminalText = Color(0xFFBDCEDB)

/** Adjacent lazy rows paint slices of one terminal panel, without row dividers. */
@Composable
internal fun CommandTerminal(first: Boolean = true, last: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clipToBounds().drawBehind {
        fun panel(inset: Float, radius: Float, color: Color, border: Color) {
            val top = if (first) inset else -radius
            val bottom = if (last) size.height - inset else size.height + radius
            val origin = Offset(inset, top)
            val bounds = Size((size.width - inset * 2).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f))
            drawRoundRect(color, origin, bounds, CornerRadius(radius))
            drawRoundRect(border, origin, bounds, CornerRadius(radius), style = Stroke(1.dp.toPx()))
        }
        panel(0.5.dp.toPx(), 8.dp.toPx(), Color(0xFF2C2C2C), Color(0xFF3D3D3D))
        panel(14.dp.toPx(), 6.dp.toPx(), Color(0xFF001827), Color(0xFF34424B))
    }.padding(start = 26.dp, end = 26.dp, top = if (first) 26.dp else 0.dp, bottom = if (last) 26.dp else 0.dp)) {
        CompositionLocalProvider(LocalContentColor provides terminalText) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) { content() }
        }
    }
}

internal fun LazyListScope.terminalItems(count: Int, key: (Int) -> String, content: @Composable (Int) -> Unit) {
    items(count = count, key = key) { index ->
        CommandTerminal(first = index == 0, last = index == count - 1) { content(index) }
    }
}
