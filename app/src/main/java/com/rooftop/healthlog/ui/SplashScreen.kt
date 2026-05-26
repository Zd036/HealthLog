package com.rooftop.healthlog.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rooftop.healthlog.R
import com.rooftop.healthlog.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

/** 启动页：1 秒自动进入主界面 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1000)
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
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "图标",
                modifier = Modifier.size(120.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )
            Text(
                "健康记录",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                "每天记录  健康常在",
                style = TextStyle(color = Color.White, fontSize = 18.sp)
            )
        }
    }
}
