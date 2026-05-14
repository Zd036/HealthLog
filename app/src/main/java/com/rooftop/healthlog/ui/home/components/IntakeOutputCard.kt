package com.rooftop.healthlog.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.ui.components.BigCard
import com.rooftop.healthlog.ui.components.PrimaryBigButton
import com.rooftop.healthlog.ui.home.HomeUiState
import com.rooftop.healthlog.ui.theme.*

/** 出入量大卡片 */
@Composable
fun IntakeOutputCard(
    state: HomeUiState,
    onIntake: () -> Unit,
    onOutput: () -> Unit
) {
    BigCard {
        Text("今日出入量", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (!state.hasIntakeOutput) {
            Text("今日暂无记录", style = MaterialTheme.typography.bodyLarge, color = HintGray)
        } else {
            Text(
                "总摄入：${state.totalIntake.toInt()} ml",
                style = MaterialTheme.typography.titleLarge.copy(color = PrimaryBlue)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "总排出：${state.totalOutput.toInt()} ml" +
                        if (state.stoolCount > 0) "（大便 ${state.stoolCount} 次）" else "",
                style = MaterialTheme.typography.titleLarge.copy(color = PrimaryBlue)
            )
            Spacer(Modifier.height(6.dp))
            val diff = (state.totalIntake - state.totalOutput).toInt()
            val color: Color = when {
                diff > 1000 || diff < -1000 -> DangerRed
                diff in -500..500 -> SuccessGreen
                else -> WarningYellow
            }
            val sign = if (diff >= 0) "+" else ""
            Text(
                "差值：$sign$diff ml",
                style = MaterialTheme.typography.titleLarge.copy(color = color)
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryBigButton(
                "记摄入",
                onClick = onIntake,
                modifier = Modifier.weight(1f),
                color = DangerRed
            )
            PrimaryBigButton(
                "记排出",
                onClick = onOutput,
                modifier = Modifier.weight(1f),
                color = SuccessGreen
            )
        }
    }
}
