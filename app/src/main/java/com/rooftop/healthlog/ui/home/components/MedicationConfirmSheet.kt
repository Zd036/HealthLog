package com.rooftop.healthlog.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rooftop.healthlog.ui.home.PendingSchedule
import com.rooftop.healthlog.ui.theme.HintGray
import com.rooftop.healthlog.ui.theme.PrimaryBlue
import java.util.Calendar

/** 服药确认 BottomSheet（修改点5） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationConfirmSheet(
    pending: PendingSchedule,
    onDismiss: () -> Unit,
    onConfirmTaken: () -> Unit,
    onConfirmMissed: () -> Unit
) {
    val now = System.currentTimeMillis()
    val scheduledMillis = scheduleTimeMillis(pending.schedule.time)
    val diffMs = now - scheduledMillis
    // 超过 3 小时 → 漏服；未到时间 → 禁用确认
    val isOverdue = diffMs > 3 * 3600 * 1000L
    val isEarly = diffMs < 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = 640.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${pending.schedule.time} 服药清单",
                fontSize = 22.sp,
                style = MaterialTheme.typography.headlineSmall
            )

            // 药品列表
            for (med in pending.medications) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(med.name, fontSize = 20.sp, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${med.dosage} ${med.unit}  · ${med.specification} mg/片  · ${med.method}",
                            fontSize = 18.sp,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HintGray
                        )
                    }
                }
            }

            // 状态提示
            when {
                isOverdue -> Text(
                    "已超过服药时间 3 小时，标记为漏服",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                isEarly -> Text(
                    "未到服药时间，请在 ${pending.schedule.time} 后服用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HintGray
                )
            }

            Spacer(Modifier.height(4.dp))

            // 主操作按钮
            Button(
                onClick = if (isOverdue) onConfirmMissed else onConfirmTaken,
                enabled = !isEarly,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverdue) HintGray else Color(0xFF4CAF50),
                    disabledContainerColor = HintGray.copy(alpha = 0.4f)
                )
            ) {
                Text(
                    if (isOverdue) "标记为漏服" else "确认已服用",
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        }
    }
}

private fun scheduleTimeMillis(time: String): Long {
    val parts = time.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
