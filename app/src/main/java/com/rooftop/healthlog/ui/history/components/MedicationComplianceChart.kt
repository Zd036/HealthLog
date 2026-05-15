package com.rooftop.healthlog.ui.history.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.ui.history.MedicationHistoryItem
import com.rooftop.healthlog.ui.theme.*
import com.rooftop.healthlog.utils.MEDICATION_STATUS_TAKEN

/** 服药依从率柱状图（每日按时率 0~1） */
@Composable
fun MedicationComplianceChart(records: List<MedicationHistoryItem>, days: Int) {
    val dayStarts = remember(days) { dayStartsForRange(days) }
    val oneDay = 24L * 3600_000L
    val ratios: List<Float?> = dayStarts.map { ds ->
        val end = ds + oneDay
        val todays = records.filter { it.scheduledTime in ds until end }
        if (todays.isEmpty()) null
        else todays.count { it.status == MEDICATION_STATUS_TAKEN } / todays.size.toFloat()
    }
    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = HintGray)

    Column(Modifier.fillMaxWidth()) {
        Text("服药依从率", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val layout = computeLayout()
            val yTicks = listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f)
            drawAxes(
                layout, yTicks,
                yLabelFormatter = { "${(it * 100).toInt()}%" },
                xDayStarts = dayStarts,
                textMeasurer = tm,
                labelStyle = labelStyle
            )
            drawBars(
                layout = layout,
                xCount = dayStarts.size,
                values = ratios,
                colorFor = { v ->
                    when {
                        v >= 1f -> SuccessGreen
                        v >= 0.5f -> WarningYellow
                        else -> DangerRed
                    }
                },
                yMax = 1f
            )
        }
        Spacer(Modifier.height(6.dp))
        Legend(listOf(SuccessGreen to "100%", WarningYellow to "≥50%", DangerRed to "<50%"))
    }
}
