package com.rooftop.healthlog.ui.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

fun LazyListScope.vitalSignsList(records: List<VitalSignsRecord>) {
    if (records.isEmpty()) {
        item { EmptyHint("暂无体征记录") }
        return
    }
    val groups = groupByDay(records.map { it.time })
    for ((dayTs, indices) in groups) {
        item(key = "vhdr-$dayTs") { DayGroupHeader(dayTs) }
        items(indices.size, key = { i -> "v-${records[indices[i]].id}" }) { idx ->
            VitalSignsItem(records[indices[idx]], records, indices[idx])
        }
    }
}

@Composable
private fun VitalSignsItem(
    v: VitalSignsRecord,
    all: List<VitalSignsRecord>,
    selfIndex: Int
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
                Text(DateUtils.formatHm(v.time),
                    style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            FlowMetrics(
                listOf(
                    "体重" to (v.weight?.let { "%.1f 斤".format(it) } ?: "-") to weightAbnormal,
                    "血压" to ((v.systolic?.toString() ?: "-") + "/" +
                            (v.diastolic?.toString() ?: "-") + " mmHg") to bpAbnormal,
                    "脉率" to ((v.heartRate?.toString() ?: "-") + " 次/分") to hrAbnormal,
                    "血糖" to (v.bloodSugar?.let { "%.1f mmol/L".format(it) } ?: "-") to bsAbnormal
                )
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((kv, abnormal) in row) {
                    val (name, value) = kv
                    Row(modifier = Modifier.weight(1f)) {
                        Text("$name：", style = MaterialTheme.typography.bodyLarge, color = HintGray)
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (abnormal) DangerRed else TextDark
                            )
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 扩展 flowMetrics: 假装使用 Color 以避免未使用导入 */
@Suppress("unused")
private val _keep: Color = TextDark
