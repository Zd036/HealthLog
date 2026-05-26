package com.rooftop.healthlog.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rooftop.healthlog.ui.components.BigCard
import com.rooftop.healthlog.ui.components.PrimaryBigButton
import com.rooftop.healthlog.ui.theme.DangerRed
import com.rooftop.healthlog.ui.theme.HintGray
import com.rooftop.healthlog.ui.theme.PrimaryBlue
import com.rooftop.healthlog.ui.theme.SuccessGreen
import com.rooftop.healthlog.ui.theme.TextDark
import com.rooftop.healthlog.utils.RecentVitalKind
import com.rooftop.healthlog.utils.RecentVitalItem
import com.rooftop.healthlog.ui.vitalsigns.VitalRecordTab

/** 体征卡片 —— 当天各指标最近一次记录 */
@Composable
fun VitalSignsCard(
    recentVitals: List<RecentVitalItem>,
    onRecord: (VitalRecordTab) -> Unit
) {
    val displayItems = remember(recentVitals) {
        val map = recentVitals.associateBy { it.kind }
        listOf(
            map[RecentVitalKind.WEIGHT] ?: MissingVitalItem("体重"),
            map[RecentVitalKind.BLOOD_PRESSURE] ?: MissingVitalItem("血压"),
            (map[RecentVitalKind.HEART_RATE]?.copy(nameText = "脉率") ?: MissingVitalItem("脉率")),
            map[RecentVitalKind.BLOOD_SUGAR] ?: MissingVitalItem("血糖")
        )
    }
    BigCard {
        val actionButtonHeight = 52.dp
        HomeCardHeader(
            title = "今日体征",
            accentColor = PrimaryBlue,
            icon = Icons.Filled.Favorite
        )
        Spacer(Modifier.height(8.dp))
        HomeInfoPanel {
            displayItems.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.nameText,
                        modifier = Modifier.width(52.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (item.abnormal) DangerRed else HintGray
                        )
                    )
                    BasicText(
                        text = recentVitalValueText(item),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        item.timeText,
                        modifier = Modifier.width(56.dp),
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = TextDark,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryBigButton(
                text = "记体重",
                onClick = { onRecord(VitalRecordTab.WEIGHT) },
                modifier = Modifier
                    .weight(1f)
                    .height(actionButtonHeight),
                color = PrimaryBlue,
                textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp)
            )
            PrimaryBigButton(
                text = "记血压",
                onClick = { onRecord(VitalRecordTab.BLOOD_PRESSURE) },
                modifier = Modifier
                    .weight(1f)
                    .height(actionButtonHeight),
                color = PrimaryBlue,
                textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp)
            )
            PrimaryBigButton(
                text = "记血糖",
                onClick = { onRecord(VitalRecordTab.BLOOD_SUGAR) },
                modifier = Modifier
                    .weight(1f)
                    .height(actionButtonHeight),
                color = PrimaryBlue,
                textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp)
            )
        }
    }
}

@Composable
private fun recentVitalValueText(item: RecentVitalItem): AnnotatedString {
    val isMissing = item.time == 0L
    val baseColor = if (isMissing) HintGray else if (item.abnormal) DangerRed else TextDark
    val valueStyle = MaterialTheme.typography.titleMedium.toSpanStyle().copy(color = baseColor)
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

private fun MissingVitalItem(name: String): RecentVitalItem = RecentVitalItem(
    kind = when (name) {
        "体重" -> RecentVitalKind.WEIGHT
        "血压" -> RecentVitalKind.BLOOD_PRESSURE
        "脉率" -> RecentVitalKind.HEART_RATE
        else -> RecentVitalKind.BLOOD_SUGAR
    },
    timeText = "--:--",
    nameText = name,
    valueText = "今日未记录",
    deltaText = null,
    abnormal = false,
    time = 0L
)
