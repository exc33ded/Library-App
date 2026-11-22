package com.exc33ded.library.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exc33ded.library.stats.MonthCount
import kotlin.math.max

private val Initials = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/** Animates 0 -> 1 once after first composition with the Expressive spatial spec; read it only inside draw lambdas. */
@Composable
private fun rememberRevealProgress(): State<Float> {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { target = 1f }
    return animateFloatAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec<Float>(), label = "reveal")
}

@Composable
fun MonthlyBarChart(months: List<MonthCount>, modifier: Modifier = Modifier) {
    val progress = rememberRevealProgress()
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val countStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val maxBooks = max(1, months.maxOfOrNull { it.books } ?: 0)
    Canvas(modifier.fillMaxWidth().height(160.dp)) {
        val axisH = 20.dp.toPx()
        val topPad = 16.dp.toPx()
        val chartH = size.height - axisH - topPad
        val baseline = topPad + chartH
        val slot = size.width / months.size
        val barW = slot * 0.6f
        val corner = CornerRadius(4.dp.toPx())
        drawLine(track, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
        months.forEachIndexed { i, m ->
            val x = i * slot + (slot - barW) / 2
            val h = chartH * (m.books.toFloat() / maxBooks) * progress.value
            if (m.books > 0) {
                drawRoundRect(primary, Offset(x, baseline - h), Size(barW, h), corner)
                drawCentered(measurer, m.books.toString(), countStyle, x + barW / 2, baseline - h - 14.dp.toPx())
            }
            drawCentered(measurer, Initials[i], labelStyle, x + barW / 2, baseline + 4.dp.toPx())
        }
    }
}

private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, style: TextStyle, cx: Float, top: Float) {
    val r = measurer.measure(text, style)
    drawText(r, topLeft = Offset(cx - r.size.width / 2f, top))
}

@Composable
fun HorizontalBars(rows: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val progress = rememberRevealProgress()
    val maxValue = max(1, rows.maxOfOrNull { it.second } ?: 0)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (name, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    name, Modifier.width(96.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Canvas(Modifier.weight(1f).height(12.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track, size = size, cornerRadius = r)
                    if (value > 0) {
                        val w = size.width * (value.toFloat() / maxValue) * progress.value
                        drawRoundRect(primary, size = Size(max(w, size.height), size.height), cornerRadius = r)
                    }
                }
                Text(value.toString(), Modifier.width(28.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
            }
        }
    }
}
