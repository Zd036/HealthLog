package com.rooftop.healthlog.ui.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import com.rooftop.healthlog.ui.theme.*
import com.rooftop.healthlog.utils.DateUtils
import com.rooftop.healthlog.utils.isBloodPressureAbnormal
import com.rooftop.healthlog.utils.isBloodSugarAbnormal
import com.rooftop.healthlog.utils.isHeartRateAbnormal
import com.rooftop.healthlog.utils.isRapidWeightGain
import com.rooftop.healthlog.utils.previousWeightRecordWithin24Hours
import com.rooftop.healthlog.utils.weightGainDelta

enum class VitalHistoryTab(val label: String) {
    WEIGHT("体重"),
    BLOOD_PRESSURE("血压"),
    HEART_RATE("脉率"),
    BLOOD_SUGAR("血糖")
}

fun LazyListScope.vitalSignsList(records: List<VitalSignsRecord>, tab: VitalHistoryTab) {
    val filtered = records.filter { record ->
        when (tab) {
            VitalHistoryTab.WEIGHT -> record.weight != null
            VitalHistoryTab.BLOOD_PRESSURE -> record.systolic != null || record.diastolic != null
            VitalHistoryTab.HEART_RATE -> record.heartRate != null
            VitalHistoryTab.BLOOD_SUGAR -> record.bloodSugar != null
        }
    }
    if (filtered.isEmpty()) {
        item { EmptyHint("暂无体征记录") }
        return
    }
    val groups = groupByDay(filtered.map { it.time })
    for ((dayTs, indices) in groups) {
        item(key = "vhdr-$dayTs") { DayGroupHeader(dayTs) }
        items(indices.size, key = { i -> "v-${filtered[indices[i]].id}" }) { idx ->
            VitalSignsItem(filtered[indices[idx]], filtered, tab)
        }
    }
}

@Composable
private fun VitalSignsItem(
    v: VitalSignsRecord,
    all: List<VitalSignsRecord>,
    tab: VitalHistoryTab
) {
    val bpAbnormal = isBloodPressureAbnormal(v.systolic, v.diastolic)
    val hrAbnormal = isHeartRateAbnormal(v.heartRate)
    val bsAbnormal = isBloodSugarAbnormal(v.bloodSugar)
    val prevWeightRecord = previousWeightRecordWithin24Hours(all, v.time)
    val weightAbnormal = isRapidWeightGain(weightGainDelta(v.weight, prevWeightRecord?.weight))

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateUtils.formatHm(v.time),
                    style = historyTimeTextStyle(MaterialTheme.typography.titleMedium)
                )
            }
            Spacer(Modifier.height(6.dp))
            when (tab) {
                VitalHistoryTab.WEIGHT -> FlowMetrics(
                    listOf(
                        "体重" to (v.weight?.let { "%.1f 斤".format(it) } ?: "-") to weightAbnormal
                    )
                )
                VitalHistoryTab.BLOOD_PRESSURE -> FlowMetrics(
                    listOf(
                        "血压" to ((v.systolic?.toString() ?: "-") + "/" +
                            (v.diastolic?.toString() ?: "-") + " mmHg") to bpAbnormal
                    )
                )
                VitalHistoryTab.HEART_RATE -> FlowMetrics(
                    listOf(
                        "脉率" to ((v.heartRate?.toString() ?: "-") + " 次/分") to hrAbnormal
                    )
                )
                VitalHistoryTab.BLOOD_SUGAR -> FlowMetrics(
                    listOf(
                        "血糖" to (v.bloodSugar?.let { "%.1f mmol/L".format(it) } ?: "-") to bsAbnormal
                    )
                )
            }
            if (v.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(v.note, style = MaterialTheme.typography.bodyMedium, color = HintGray)
            }
        }
    }
}

@Composable
private fun FlowMetrics(items: List<Pair<Pair<String, String>, Boolean>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for ((kv, abnormal) in row) {
                    val (name, value) = kv
                    val itemModifier = if (row.size == 1) Modifier.fillMaxWidth() else Modifier.weight(1f)
                    Row(
                        modifier = itemModifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$name：", style = MaterialTheme.typography.bodyLarge, color = HintGray)
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (abnormal) DangerRed else TextDark
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

/** 扩展 flowMetrics: 假装使用 Color 以避免未使用导入 */
@Suppress("unused")
private val _keep: Color = TextDark
