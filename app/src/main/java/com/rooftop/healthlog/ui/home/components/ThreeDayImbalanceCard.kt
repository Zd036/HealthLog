package com.rooftop.healthlog.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.ui.components.BigCard
import com.rooftop.healthlog.ui.theme.AlertBg
import com.rooftop.healthlog.ui.theme.DangerRed

/** 连续 3 天入量超标的强警告卡片 */
@Composable
fun ThreeDayImbalanceCard(onDismiss: () -> Unit) {
    BigCard(background = AlertBg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = DangerRed)
            Spacer(Modifier.width(8.dp))
            Text(
                "连续入量超标",
                style = MaterialTheme.typography.titleLarge.copy(color = DangerRed)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "您的入量已连续3天超标，请控制饮食",
            style = MaterialTheme.typography.bodyLarge.copy(color = DangerRed)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    "我知道了",
                    style = MaterialTheme.typography.titleMedium.copy(color = DangerRed)
                )
            }
        }
    }
}
