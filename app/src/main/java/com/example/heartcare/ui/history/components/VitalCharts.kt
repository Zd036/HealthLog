package com.example.heartcare.ui.history.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.heartcare.data.local.entity.VitalSignsRecord
import com.example.heartcare.ui.theme.*

/** 血压趋势图：双线（收缩压蓝、舒张压绿），异常值红色高亮 */
@Composable
fun BloodPressureChart(records: List<VitalSignsRecord>, days: Int) {
    val dayStarts = remember(days) { dayStartsForRange(days) }
    val sys = remember(records, dayStarts) {
        bucketDaily(records, { it.time }, { it.systolic?.toFloat() }, dayStarts)
    }
    val dia = remember(records, dayStarts) {
        bucketDaily(records, { it.time }, { it.diastolic?.toFloat() }, dayStarts)
    }
    val sysHl = sys.map { v -> v?.let { if (it > 140 || it < 90) DangerRed else null } }
    val diaHl = dia.map { v -> v?.let { if (it > 90 || it < 60) DangerRed else null } }

    val all = (sys + dia).filterNotNull()
    val yMin = (all.minOrNull() ?: 60f).coerceAtMost(60f) - 10f
    val yMax = (all.maxOrNull() ?: 140f).coerceAtLeast(140f) + 10f
    val yTicks = buildYTicks(yMin, yMax, 5)

    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = HintGray)

    Column(Modifier.fillMaxWidth()) {
        Text("血压趋势（mmHg）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val layout = computeLayout()
            drawAxes(layout, yTicks, { it.toInt().toString() }, dayStarts, tm, labelStyle)
            drawLineChart(layout, dayStarts.size, yMin, yMax, sys, sysHl,
                lineColor = PrimaryBlue, normalDotColor = PrimaryBlue)
            drawLineChart(layout, dayStarts.size, yMin, yMax, dia, diaHl,
                lineColor = SuccessGreen, normalDotColor = SuccessGreen)
        }
        Spacer(Modifier.height(6.dp))
        Legend(listOf(PrimaryBlue to "收缩压", SuccessGreen to "舒张压", DangerRed to "异常"))
    }
}

@Composable
fun HeartRateChart(records: List<VitalSignsRecord>, days: Int) {
    SingleMetricChart(
        title = "心率趋势（次/分）",
        records = records, days = days,
        selector = { it.heartRate?.toFloat() },
        abnormal = { v -> v > 100f || v < 60f },
        lineColor = WarningYellow,
        defaultMin = 50f, defaultMax = 110f
    )
}

@Composable
fun WeightChart(records: List<VitalSignsRecord>, days: Int) {
    // 体重异常：24 小时内增加 ≥ 2 斤（对上一天的 diff）
    val dayStarts = remember(days) { dayStartsForRange(days) }
    val vals = remember(records, dayStarts) {
        bucketDaily(records, { it.time }, { it.weight }, dayStarts)
    }
    val highlights = vals.mapIndexed { i, v ->
        val prev = vals.getOrNull(i - 1)
        if (v != null && prev != null && v - prev >= 2f) DangerRed else null
    }
    val nonNull = vals.filterNotNull()
    val yMin = (nonNull.minOrNull() ?: 100f) - 5f
    val yMax = (nonNull.maxOrNull() ?: 140f) + 5f
    val yTicks = buildYTicks(yMin, yMax, 5)
    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = HintGray)
    Column(Modifier.fillMaxWidth()) {
        Text("体重趋势（斤）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val layout = computeLayout()
            drawAxes(layout, yTicks, { "%.1f".format(it) }, dayStarts, tm, labelStyle)
            drawLineChart(layout, dayStarts.size, yMin, yMax, vals, highlights,
                lineColor = Color(0xFF9C27B0), normalDotColor = Color(0xFF9C27B0))
        }
        Spacer(Modifier.height(6.dp))
        Legend(listOf(Color(0xFF9C27B0) to "体重", DangerRed to "24小时增加≥2斤"))
    }
}

@Composable
fun BloodSugarChart(records: List<VitalSignsRecord>, days: Int) {
    SingleMetricChart(
        title = "血糖趋势（mmol/L）",
        records = records, days = days,
        selector = { it.bloodSugar },
        abnormal = { v -> v > 11.1f || v < 3.9f },
        lineColor = Color(0xFF00BCD4),
        defaultMin = 3f, defaultMax = 12f,
        yFormat = { "%.1f".format(it) }
    )
}

@Composable
private fun SingleMetricChart(
    title: String,
    records: List<VitalSignsRecord>,
    days: Int,
    selector: (VitalSignsRecord) -> Float?,
    abnormal: (Float) -> Boolean,
    lineColor: Color,
    defaultMin: Float,
    defaultMax: Float,
    yFormat: (Float) -> String = { it.toInt().toString() }
) {
    val dayStarts = remember(days) { dayStartsForRange(days) }
    val vals = remember(records, dayStarts) {
        bucketDaily(records, { it.time }, selector, dayStarts)
    }
    val highlights = vals.map { v -> v?.let { if (abnormal(it)) DangerRed else null } }
    val nonNull = vals.filterNotNull()
    val yMin = (nonNull.minOrNull() ?: defaultMin).coerceAtMost(defaultMin)
    val yMax = (nonNull.maxOrNull() ?: defaultMax).coerceAtLeast(defaultMax)
    val yTicks = buildYTicks(yMin, yMax, 5)
    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = HintGray)

    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val layout = computeLayout()
            drawAxes(layout, yTicks, yFormat, dayStarts, tm, labelStyle)
            drawLineChart(layout, dayStarts.size, yMin, yMax, vals, highlights,
                lineColor = lineColor, normalDotColor = lineColor)
        }
        Spacer(Modifier.height(6.dp))
        Legend(listOf(lineColor to "数值", DangerRed to "异常"))
    }
}

internal fun buildYTicks(min: Float, max: Float, count: Int): List<Float> {
    if (max <= min) return listOf(min, max + 1f)
    val step = (max - min) / (count - 1).coerceAtLeast(1)
    return (0 until count).map { min + it * step }
}
