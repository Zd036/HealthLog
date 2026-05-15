package com.rooftop.healthlog.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.ui.components.BigCard
import com.rooftop.healthlog.ui.components.PrimaryBigButton
import com.rooftop.healthlog.ui.theme.DangerRed
import com.rooftop.healthlog.ui.theme.HintGray
import com.rooftop.healthlog.ui.theme.PrimaryBlue
import com.rooftop.healthlog.ui.theme.SuccessGreen
import com.rooftop.healthlog.ui.theme.TextDark
import com.rooftop.healthlog.utils.RecentVitalItem

/** 体征卡片 —— 当天各指标最近一次记录 */
@Composable
fun VitalSignsCard(
    recentVitals: List<RecentVitalItem>,
    onRecord: () -> Unit
) {
    BigCard {
        Text("今日体征", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (recentVitals.isEmpty()) {
            Text("今日暂无体征记录", style = MaterialTheme.typography.bodyLarge, color = HintGray)
        } else {
            recentVitals.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        item.timeText,
                        modifier = Modifier
                            .width(56.dp)
                            .alignByBaseline(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = HintGray,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Text(
                        item.nameText,
                        modifier = Modifier
                            .width(44.dp)
                            .alignByBaseline(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (item.abnormal) DangerRed else TextDark
                        )
                    )
                    BasicText(
                        text = recentVitalValueText(item),
                        modifier = Modifier
                            .weight(1f)
                            .alignByBaseline()
                    )
                }
                if (index != recentVitals.lastIndex) {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryBigButton("记体征", onClick = onRecord, color = PrimaryBlue)
    }
}

@Composable
private fun recentVitalValueText(item: RecentVitalItem): AnnotatedString {
    val baseColor = if (item.abnormal) DangerRed else TextDark
    val valueStyle = MaterialTheme.typography.bodyLarge.toSpanStyle().copy(color = baseColor)
    val deltaStyle = MaterialTheme.typography.bodyMedium.toSpanStyle().copy(
        color = weightDeltaColor(item.deltaText)
    )
    return buildAnnotatedString {
        pushStyle(valueStyle)
        append(item.valueText)
        pop()
        item.deltaText?.let {
            append(" ")
            pushStyle(deltaStyle)
            append("($it)")
            pop()
        }
    }
}

private fun weightDeltaColor(deltaText: String?): Color {
    if (deltaText.isNullOrBlank()) return HintGray
    return when {
        deltaText.startsWith("+") -> DangerRed
        deltaText.startsWith("-") -> SuccessGreen
        else -> HintGray
    }
}
