package com.example.heartcare.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.heartcare.data.local.entity.VitalSignsRecord
import com.example.heartcare.ui.components.BigCard
import com.example.heartcare.ui.components.PrimaryBigButton
import com.example.heartcare.ui.theme.DangerRed
import com.example.heartcare.ui.theme.HintGray
import com.example.heartcare.ui.theme.SuccessGreen
import com.example.heartcare.ui.theme.WarningYellow
import java.text.SimpleDateFormat
import java.util.*

/** 体重趋势（对比昨日） */
enum class WeightTrend { UP, DOWN, FLAT }

data class WeightTrendInfo(
    val trend: WeightTrend,
    /** 差值（斤），正为增加 */
    val diff: Float,
    /** 提示文字 + 对应颜色；无提示时返回 null */
    val warning: Pair<String, Color>?
)

fun computeWeightTrend(today: Float?, yesterday: Float?): WeightTrendInfo? {
    if (today == null) return null
    if (yesterday == null) return WeightTrendInfo(WeightTrend.FLAT, 0f, null)
    val diff = today - yesterday
    val absDiff = kotlin.math.abs(diff)
    val trend = when {
        absDiff < 0.1f -> WeightTrend.FLAT
        diff > 0 -> WeightTrend.UP
        else -> WeightTrend.DOWN
    }
    val warning: Pair<String, Color>? = when {
        diff >= 2f -> "24小时内体重增加 ${"%.1f".format(diff)} 斤，请就医检查" to DangerRed
        diff >= 1f -> "体重增加较快，请注意" to WarningYellow
        else -> null
    }
    return WeightTrendInfo(trend, diff, warning)
}

/** 体征卡片 —— 带体重趋势箭头、血糖异常高亮 */
@Composable
fun VitalSignsCard(
    latest: VitalSignsRecord?,
    yesterdayWeight: Float?,
    onRecord: () -> Unit
) {
    BigCard {
        Text("最近体征", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (latest == null || isOlderThanToday(latest.time)) {
            Text("今日暂无体征记录", style = MaterialTheme.typography.bodyLarge, color = HintGray)
        } else {
            if (latest.systolic != null && latest.diastolic != null) {
                Text(
                    "血压 ${latest.systolic}/${latest.diastolic} mmHg",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(4.dp))
            }
            latest.heartRate?.let {
                Text("心率 $it 次/分", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
            }
            latest.weight?.let { w ->
                val info = computeWeightTrend(w, yesterdayWeight)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "体重 ${"%.1f".format(w)} 斤",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (info != null && yesterdayWeight != null) {
                        Spacer(Modifier.width(6.dp))
                        val (icon, color) = trendIconAndColor(info)
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                        val signStr = if (info.diff > 0) "+" else ""
                        Text(
                            "$signStr${"%.1f".format(info.diff)} 斤",
                            style = MaterialTheme.typography.bodyMedium.copy(color = color)
                        )
                    }
                }
                if (info?.warning != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        info.warning.first,
                        style = MaterialTheme.typography.bodyMedium.copy(color = info.warning.second)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            latest.bloodSugar?.let { bs ->
                val abnormal = bs > 11.1f || bs < 3.9f
                val color = if (abnormal) DangerRed else Color.Unspecified
                Text(
                    "血糖 ${"%.1f".format(bs)} mmol/L" + if (abnormal) "（异常）" else "",
                    style = MaterialTheme.typography.bodyLarge.copy(color = color)
                )
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "记录时间：${SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(latest.time))}",
                style = MaterialTheme.typography.bodyMedium,
                color = HintGray
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryBigButton("记体征", onClick = onRecord)
    }
}

private fun trendIconAndColor(info: WeightTrendInfo): Pair<ImageVector, Color> {
    val color = info.warning?.second ?: when (info.trend) {
        WeightTrend.UP -> WarningYellow
        WeightTrend.DOWN -> SuccessGreen
        WeightTrend.FLAT -> HintGray
    }
    val icon = when (info.trend) {
        WeightTrend.UP -> Icons.Filled.ArrowUpward
        WeightTrend.DOWN -> Icons.Filled.ArrowDownward
        WeightTrend.FLAT -> Icons.Filled.ArrowForward
    }
    return icon to color
}

private fun isOlderThanToday(time: Long): Boolean {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return time < cal.timeInMillis
}
