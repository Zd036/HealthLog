package com.rooftop.healthlog.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
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
        HomeCardHeader(
            title = "今日出入量",
            accentColor = PrimaryBlue,
            icon = Icons.Filled.WaterDrop
        )
        Spacer(Modifier.height(8.dp))
        if (!state.hasIntakeOutput) {
            HomeInfoPanel {
                Text("今日未记录", style = MaterialTheme.typography.bodyLarge, color = HintGray)
            }
        } else {
            val diff = (state.totalIntake - state.totalOutput).toInt()
            val color: Color = when {
                diff < 0 -> SuccessGreen
                diff < 200 -> TextDark
                diff < 500 -> WarningYellow
                else -> DangerRed
            }
            val sign = if (diff >= 0) "+" else ""
            HomeInfoPanel {
                HomeMetricRow("总摄入", "${state.totalIntake.toInt()} ml", DangerRed)
                HomeMetricRow("总排出", "${state.totalOutput.toInt()} ml", SuccessGreen)
                HomeMetricRow("大便次数", "${state.stoolCount} 次", TextDark)
                HomeMetricRow("差值", "$sign$diff ml", color)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
