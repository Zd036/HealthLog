package com.rooftop.healthlog.ui.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.ui.theme.*
import com.rooftop.healthlog.utils.DateUtils

/** 按日期分组的分组头 */
internal fun groupByDay(items: List<Long>): Map<Long, List<Int>> {
    val map = LinkedHashMap<Long, MutableList<Int>>()
    items.forEachIndexed { i, t ->
        val day = DateUtils.dayStartOf(t)
        map.getOrPut(day) { mutableListOf() }.add(i)
    }
    return map
}

@Composable
fun DayGroupHeader(dayTs: Long) {
    Surface(
        color = PrimaryBlue.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            DateUtils.formatFullZh(dayTs),
            style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBlue),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

fun LazyListScope.intakeOutputList(
    records: List<IntakeOutputRecord>,
    onDelete: (IntakeOutputRecord) -> Unit
) {
    if (records.isEmpty()) {
        item { EmptyHint("暂无出入量记录") }
        return
    }
    val groups = groupByDay(records.map { it.time })
    for ((dayTs, indices) in groups) {
        item(key = "iohdr-$dayTs") { DayGroupHeader(dayTs) }
        items(indices.size, key = { i -> "io-${records[indices[i]].id}" }) { idx ->
            IntakeOutputListItem(records[indices[idx]], onDelete)
        }
    }
}

@Composable
private fun IntakeOutputListItem(r: IntakeOutputRecord, onDelete: (IntakeOutputRecord) -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    val canDelete = r.time >= DateUtils.todayStart()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = canDelete, onClick = { showConfirm = true }),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(DateUtils.formatHm(r.time),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(64.dp))
            val typeLabel = if (r.type == "intake") "摄入" else "排出"
            val tagColor = if (r.type == "intake") DangerRed else SuccessGreen
            AssistChip(
                onClick = {},
                label = { Text(text=typeLabel, color = TextDark) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = tagColor.copy(alpha = 0.12f),
                    labelColor = tagColor
                )
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(r.category, style = MaterialTheme.typography.titleMedium)
                val subtitle = buildString {
                    if (r.type == "output" && r.category == "大便") {
                        append("${r.amount.toInt()} 次")
                    } else {
                        append("${r.amount.toInt()} ml")
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HintGray)
                if (r.note.isNotBlank()) {
                    Text(r.note, style = MaterialTheme.typography.bodyMedium, color = HintGray)
                }
            }
        }
    }
    if (showConfirm && canDelete) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("删除记录", style = MaterialTheme.typography.headlineMedium) },
            text = { Text("确定要删除这条记录吗？", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { onDelete(r); showConfirm = false }) {
                    Text("删除", style = MaterialTheme.typography.titleLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("取消", style = MaterialTheme.typography.titleLarge)
                }
            }
        )
    }
}

@Composable
internal fun EmptyHint(text: String) {
    com.rooftop.healthlog.ui.components.EmptyStateView(
        text = text,
        modifier = Modifier.fillMaxWidth()
    )
}
