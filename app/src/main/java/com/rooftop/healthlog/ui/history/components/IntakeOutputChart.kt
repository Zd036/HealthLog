package com.rooftop.healthlog.ui.history.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.ui.theme.*
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun IntakeOutputChart(records: List<IntakeOutputRecord>, days: Int) {
    val dayStarts = remember(days) { dayStartsForRange(days) }
    val values = remember(records, dayStarts) { dailyIntakeOutputDiff(records, dayStarts) }
    val highlights: List<Color?> = values.map { v ->
        v?.let { intakeDiffColor(it, SuccessGreen, WarningYellow, DangerRed) }
    }

    val (yMin, yMax, yTicks) = remember(values) { computeYRange(values) }
    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = HintGray)

    Column(Modifier.fillMaxWidth()) {
        Text("出入量差值趋势（摄入 - 排出，单位 ml）",
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val layout = computeLayout()
            drawAxes(
                layout = layout,
                yTicks = yTicks,
                yLabelFormatter = { "${it.toInt()}" },
                xDayStarts = dayStarts,
                textMeasurer = tm,
                labelStyle = labelStyle
            )
            drawZeroLine(layout, yMin, yMax)
            drawLineChart(
                layout = layout,
                xCount = dayStarts.size,
                yMin = yMin, yMax = yMax,
                values = values,
                highlights = highlights,
                lineColor = PrimaryBlue
            )
        }
        Spacer(Modifier.height(6.dp))
        Legend(
            items = listOf(
                SuccessGreen to "平衡(<200)",
                WarningYellow to "异常(200~500)",
                DangerRed to "超标(≥500)"
            )
        )
    }
}

internal fun computeYRange(values: List<Float?>): Triple<Float, Float, List<Float>> {
    val nonNull = values.filterNotNull()
    if (nonNull.isEmpty()) {
        val ticks = listOf(-1000f, -500f, 0f, 500f, 1000f)
        return Triple(-1000f, 1000f, ticks)
    }
    val absMax = nonNull.maxOf { abs(it) }
    val span = ceil(absMax / 500f) * 500f
    val lo = -span
    val hi = span
    val step = (span / 2f).coerceAtLeast(250f)
    val ticks = generateSequence(lo) { it + step }.takeWhile { it <= hi + 0.1f }.toList()
    return Triple(lo, hi, ticks)
}

@Composable
internal fun Legend(items: List<Pair<Color, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((c, label) in items) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.Canvas(
                    Modifier.size(12.dp).padding(end = 4.dp)
                ) { drawCircle(c) }
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
