package com.example.heartcare.ui.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.heartcare.data.local.entity.MedicationRecord
import com.example.heartcare.ui.theme.*
import com.example.heartcare.utils.DateUtils

fun LazyListScope.medicationList(records: List<MedicationRecord>) {
    if (records.isEmpty()) {
        item { EmptyHint("暂无服药记录") }
        return
    }
    val groups = groupByDay(records.map { it.scheduledTime })
    for ((dayTs, indices) in groups) {
        item(key = "mhdr-$dayTs") { DayGroupHeader(dayTs) }
        items(indices.size, key = { i -> "m-${records[indices[i]].id}" }) { idx ->
            MedicationItem(records[indices[idx]])
        }
    }
}

@Composable
private fun MedicationItem(r: MedicationRecord) {
    val taken = r.status == "taken"
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(96.dp)) {
                Text("计划：" + DateUtils.formatHm(r.scheduledTime),
                    style = MaterialTheme.typography.bodyLarge)
                Text(
                    "实际：" + (r.actualTime?.let { DateUtils.formatHm(it) } ?: "—"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HintGray
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(r.medicationName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${r.dosage.toInt()} ${r.unit}",
                    style = MaterialTheme.typography.bodyMedium, color = HintGray
                )
            }
            val chipColor = if (taken) SuccessGreen else DangerRed
            AssistChip(
                onClick = {},
                label = { Text(if (taken) "按时服用" else "漏服") },
                leadingIcon = {
                    if (taken) Icon(Icons.Filled.Check, null, tint = chipColor)
                    else Icon(Icons.Filled.Close, null, tint = chipColor)
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = chipColor.copy(alpha = 0.12f),
                    labelColor = chipColor
                )
            )
        }
    }
}
