package com.rooftop.healthlog.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.ui.components.BigCard
import com.rooftop.healthlog.ui.home.MedicationSlotStatus
import com.rooftop.healthlog.ui.home.PendingSchedule
import com.rooftop.healthlog.ui.theme.DangerRed
import com.rooftop.healthlog.ui.theme.HintGray
import com.rooftop.healthlog.ui.theme.PrimaryBlue
import com.rooftop.healthlog.ui.theme.SuccessGreen
import com.rooftop.healthlog.utils.MEDICATION_STATUS_MISSED
import com.rooftop.healthlog.utils.MEDICATION_STATUS_TAKEN
import com.rooftop.healthlog.utils.medicationStatusLabel

/** 今日用药卡片：修改点2 后，已服用和已漏服时间点都不可重复点击。 */
@Composable
fun MedicationCard(
    schedules: List<PendingSchedule>,
    done: Boolean,
    onScheduleClick: (PendingSchedule) -> Unit = {}
) {
    BigCard {
        Text("今日用药", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        when {
            schedules.isEmpty() -> Text(
                "暂未设置用药时间点",
                style = MaterialTheme.typography.bodyLarge,
                color = HintGray
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done) {
                    Text(
                        "今日用药已完成",
                        style = MaterialTheme.typography.titleMedium.copy(color = SuccessGreen)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                for (p in schedules) {
                    val isPending = p.status == MedicationSlotStatus.PENDING
                    val cardColor = when (p.status) {
                        MedicationSlotStatus.PENDING -> PrimaryBlue.copy(alpha = 0.06f)
                        MedicationSlotStatus.TAKEN -> SuccessGreen.copy(alpha = 0.08f)
                        MedicationSlotStatus.MISSED -> DangerRed.copy(alpha = 0.08f)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .let { base ->
                                if (isPending) base.clickable { onScheduleClick(p) } else base
                            }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    timeLabel(p.schedule.time),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = when (p.status) {
                                            MedicationSlotStatus.PENDING -> PrimaryBlue
                                            MedicationSlotStatus.TAKEN -> SuccessGreen
                                            MedicationSlotStatus.MISSED -> DangerRed
                                        }
                                    )
                                )
                                Text(
                                    scheduleSummary(p),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HintGray
                                )
                            }
                            when (p.status) {
                                MedicationSlotStatus.PENDING -> Icon(
                                    Icons.Filled.ChevronRight,
                                    null,
                                    tint = HintGray,
                                    modifier = Modifier.size(20.dp)
                                )
                                MedicationSlotStatus.TAKEN -> Icon(
                                    Icons.Filled.Check,
                                    null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                MedicationSlotStatus.MISSED -> Icon(
                                    Icons.Filled.Close,
                                    null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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

private fun scheduleSummary(p: PendingSchedule): String {
    return when (p.status) {
        MedicationSlotStatus.PENDING -> "待处理"
        MedicationSlotStatus.TAKEN -> medicationStatusLabel(MEDICATION_STATUS_TAKEN)
        MedicationSlotStatus.MISSED -> medicationStatusLabel(MEDICATION_STATUS_MISSED)
    }
}
