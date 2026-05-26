package com.rooftop.healthlog.ui.history.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.ui.history.DateRange
import com.rooftop.healthlog.ui.theme.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun IntakeOutputChart(records: List<IntakeOutputRecord>, range: DateRange) {
    val dayStarts = remember(range, records) {
        dayStartsForRange(range, records.minOfOrNull { it.time })
    }
    val (intakeValues, outputValues) = remember(records, dayStarts) {
        dailyIntakeOutputTotals(records, dayStarts)
    }
    val yMax = remember(intakeValues, outputValues) {
        max(
            intakeValues.filterNotNull().maxOrNull() ?: 0f,
            outputValues.filterNotNull().maxOrNull() ?: 0f
        ).let { if (it <= 0f) 1000f else ceil(it / 500f) * 500f }
    }
    val yTicks = remember(yMax) { buildPositiveYTicks(yMax, 5) }
    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = HintGray)

    Column(Modifier.fillMaxWidth()) {
        Text("出入量趋势（单位 ml）",
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
                labelStyle = labelStyle,
                range = range
            )
            drawGroupedBarsWithDiffBand(
                layout = layout,
                xCount = dayStarts.size,
                intakeValues = intakeValues,
                outputValues = outputValues,
                yMax = yMax
            )
        }
        Spacer(Modifier.height(6.dp))
        Legend(
            items = listOf(
                DangerRed to "入量",
                SuccessGreen to "出量",
                DangerRed to "差值≥500ml",
                WarningYellow to "差值<500ml"
            )
        )
    }
}

internal fun buildPositiveYTicks(max: Float, count: Int): List<Float> {
    if (count <= 1 || max <= 0f) return listOf(0f, max)
    val step = max / (count - 1)
    return (0 until count).map { it * step }
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGroupedBarsWithDiffBand(
    layout: ChartLayout,
    xCount: Int,
    intakeValues: List<Float?>,
    outputValues: List<Float?>,
    yMax: Float
) {
    if (xCount <= 0) return
    val step = layout.width / xCount
    val groupWidth = step * 0.68f
    val barWidth = groupWidth * 0.34f
    val innerGap = groupWidth * 0.14f
    val range = yMax.coerceAtLeast(1f)
    val diffBandWidth = innerGap + barWidth * 0.5f

    fun topY(value: Float): Float = layout.bottom - (value / range) * layout.height

    for (i in 0 until xCount) {
        val intake = intakeValues.getOrNull(i)
        val output = outputValues.getOrNull(i)
        if (intake == null && output == null) continue

        val centerX = layout.left + step * i + step / 2f
        val intakeLeft = centerX - innerGap / 2f - barWidth
        val outputLeft = centerX + innerGap / 2f
        val diffBandLeft = centerX - diffBandWidth / 2f

        if (intake != null && output != null) {
            val intakeTop = topY(intake.coerceAtLeast(0f))
            val outputTop = topY(output.coerceAtLeast(0f))
            val diffTop = minOf(intakeTop, outputTop)
            val diffBottom = maxOf(intakeTop, outputTop)
            if (diffBottom > diffTop) {
                val diffColor = if (abs(intake - output) >= 500f) DangerRed else WarningYellow
                drawRect(
                    color = diffColor.copy(alpha = 0.45f),
                    topLeft = Offset(diffBandLeft, diffTop),
                    size = Size(diffBandWidth, diffBottom - diffTop)
                )
            }
        }

        intake?.let {
            val barTop = topY(it.coerceAtLeast(0f))
            drawRect(
                color = DangerRed,
                topLeft = Offset(intakeLeft, barTop),
                size = androidx.compose.ui.geometry.Size(barWidth, layout.bottom - barTop)
            )
        }
        output?.let {
            val barTop = topY(it.coerceAtLeast(0f))
            drawRect(
                color = SuccessGreen,
                topLeft = Offset(outputLeft, barTop),
                size = androidx.compose.ui.geometry.Size(barWidth, layout.bottom - barTop)
            )
        }
    }
}
