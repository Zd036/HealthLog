package com.rooftop.healthlog.ui.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.ui.history.MedicationHistoryItem
import com.rooftop.healthlog.ui.theme.*
import com.rooftop.healthlog.utils.DateUtils

fun LazyListScope.medicationList(records: List<MedicationHistoryItem>) {
    if (records.isEmpty()) {
        item { EmptyHint("暂无服药记录") }
        return
    }
    val groups = groupByDay(records.map { it.scheduledTime })
    for ((dayTs, indices) in groups) {
        item(key = "mhdr-$dayTs") { DayGroupHeader(dayTs) }
        items(indices.size, key = { i -> "m-${records[indices[i]].scheduledTime}" }) { idx ->
            MedicationItem(records[indices[idx]])
        }
    }
}

@Composable
private fun MedicationItem(r: MedicationHistoryItem) {
    var expanded by remember { mutableStateOf(false) }
    val taken = r.status == "taken"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(96.dp)) {
                    Text(
                        DateUtils.formatHm(r.scheduledTime),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "服药",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HintGray
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (taken) "已按时服用" else "漏服",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        r.medicationNames.joinToString("、"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HintGray
                    )
                }
                val chipColor = if (taken) SuccessGreen else DangerRed
                AssistChip(
                    onClick = {},
                    label = { Text(if (taken) "按时" else "漏服", color = TextDark) },
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
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "药品详情：${r.medicationDetails.joinToString("、")}",
                    style = MaterialTheme.typography.bodyMedium, color = HintGray
                )
                Text(
                    "实际时间：" + (r.actualTime?.let { DateUtils.formatHm(it) } ?: "—"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HintGray
                )
            }
        }
    }
}
