package com.example.heartcare.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.heartcare.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

/** 启动页：1.5 秒自动进入主界面 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onFinish()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(PrimaryBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeartWithDropIcon()
            Text(
                "心衰助手",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                "守护您的每一天",
                style = TextStyle(color = Color.White, fontSize = 18.sp)
            )
        }
    }
}

/** Compose 绘制：心形 + 水滴图标 */
@Composable
private fun HeartWithDropIcon() {
    Canvas(modifier = Modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        // 心形
        val heart = Path().apply {
            val cx = w / 2f
            val cy = h * 0.55f
            val sz = w * 0.6f
            moveTo(cx, cy + sz * 0.45f)
            cubicTo(cx - sz * 0.9f, cy, cx - sz * 0.45f, cy - sz * 0.55f, cx, cy - sz * 0.15f)
            cubicTo(cx + sz * 0.45f, cy - sz * 0.55f, cx + sz * 0.9f, cy, cx, cy + sz * 0.45f)
            close()
        }
        drawPath(heart, color = Color.White)
        // 水滴
        val drop = Path().apply {
            val cx = w * 0.55f
            val cy = h * 0.5f
            val r = w * 0.09f
            moveTo(cx, cy - r * 2.2f)
            cubicTo(cx + r * 1.6f, cy - r * 0.4f, cx + r * 1.2f, cy + r * 1.2f, cx, cy + r * 1.2f)
            cubicTo(cx - r * 1.2f, cy + r * 1.2f, cx - r * 1.6f, cy - r * 0.4f, cx, cy - r * 2.2f)
            close()
        }
        drawPath(drop, color = PrimaryBlue.copy(alpha = 0.85f))
        // 高光
        drawCircle(
            color = Color.White.copy(alpha = 0.6f),
            radius = w * 0.025f,
            center = Offset(w * 0.5f, h * 0.42f)
        )
    }
}
