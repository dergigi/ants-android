package org.dergigi.ants

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

internal data class SearchPlaceholder(val query: String, val progress: () -> Float, val next: () -> Unit)

@Composable
internal fun rememberSearchPlaceholder(active: Boolean, loggedIn: Boolean): SearchPlaceholder {
    var example by rememberSaveable { mutableStateOf("/examples") }
    var cycle by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pool = remember(loggedIn) {
        (listOf("/examples", "/help", "/kinds", "/history", "/tutorial") +
            searchExamples.filter { !it.needsLogin || loggedIn }.map { it.query }).distinct()
    }
    // Do not expose an account-relative example for even one frame after logout.
    val displayed = if (!loggedIn && example.contains(":@me")) "/examples" else example
    fun nextExample() { example = pool.filter { it != displayed }.randomOrNull() ?: "/examples" }
    LaunchedEffect(active, loggedIn, cycle, lifecycle) {
        progress.snapTo(0f)
        if (active) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(7000, easing = LinearEasing))
                val previous = example
                example = pool.filter { it != previous }.randomOrNull() ?: "/examples"
            }
        }
    }
    return SearchPlaceholder(displayed, { progress.value }, { nextExample(); cycle++ })
}

@Composable
internal fun NextSearchExample(placeholder: SearchPlaceholder) {
    IconButton(onClick = placeholder.next, modifier = Modifier.semantics { contentDescription = "Next search example" }) {
        // Read animation state during drawing, not at the app's composition root.
        Canvas(Modifier.size(18.dp)) {
            val stroke = 1.5.dp.toPx()
            val inset = stroke / 2
            val bounds = Size(size.width - stroke, size.height - stroke)
            drawCircle(Color(0xFF3D3D3D), radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            drawArc(Color(0xFF9CA3AF), startAngle = -90f, sweepAngle = maxOf(3.6f, placeholder.progress() * 360f),
                useCenter = false, topLeft = Offset(inset, inset), size = bounds, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}
