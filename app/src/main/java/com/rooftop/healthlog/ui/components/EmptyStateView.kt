package com.rooftop.healthlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertChartOutlined
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.ui.theme.HintGray
import com.rooftop.healthlog.ui.theme.PrimaryBlue

/** 通用空状态：图标 + 18sp 灰色文字 + 可选引导按钮 */
@Composable
fun EmptyStateView(
    text: String,
    icon: ImageVector = Icons.Filled.InsertChartOutlined,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HintGray,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(color = HintGray)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onAction,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = PrimaryBlue.copy(alpha = 0.12f),
                    contentColor = PrimaryBlue
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
