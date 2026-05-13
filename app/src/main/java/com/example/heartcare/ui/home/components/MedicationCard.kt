package com.example.heartcare.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.heartcare.ui.components.BigCard
import com.example.heartcare.ui.home.PendingSchedule
import com.example.heartcare.ui.theme.HintGray
import com.example.heartcare.ui.theme.PrimaryBlue
import com.example.heartcare.ui.theme.SuccessGreen

/** 今日待服药物卡片，每个时间点可点击 */
@Composable
fun MedicationCard(
    pending: List<PendingSchedule>,
    done: Boolean,
    onScheduleClick: (PendingSchedule) -> Unit = {}
) {
    BigCard {
        Text("今日用药", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        when {
            done -> Text(
                "今日用药已完成 ✅",
                style = MaterialTheme.typography.titleMedium.copy(color = SuccessGreen)
            )
            pending.isEmpty() -> Text(
                "暂未设置用药时间点",
                style = MaterialTheme.typography.bodyLarge,
                color = HintGray
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (p in pending) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryBlue.copy(alpha = 0.06f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onScheduleClick(p) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    timeLabel(p.schedule.time),
                                    style = MaterialTheme.typography.titleMedium.copy(color = PrimaryBlue)
                                )
                                Text(
                                    "还有 ${p.medications.size} 种药",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HintGray
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = HintGray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun timeLabel(time: String): String {
    val hour = time.substringBefore(':').toIntOrNull() ?: return time
    val period = when (hour) {
        in 0..4 -> "凌晨"; in 5..10 -> "早上"; in 11..13 -> "中午"
        in 14..17 -> "下午"; in 18..23 -> "晚上"; else -> ""
    }
    return "$period $time"
}
