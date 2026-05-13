package com.example.heartcare.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.heartcare.ui.components.BigCard
import com.example.heartcare.ui.theme.AlertBg
import com.example.heartcare.ui.theme.DangerRed

/** 异常提示卡片（仅在异常时显示，红色背景） */
@Composable
fun AlertCard(messages: List<String>) {
    BigCard(background = AlertBg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = DangerRed)
            Spacer(Modifier.width(8.dp))
            Text("异常提示", style = MaterialTheme.typography.titleLarge.copy(color = DangerRed))
        }
        Spacer(Modifier.height(8.dp))
        for (m in messages) {
            Text("• $m", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
        }
    }
}
