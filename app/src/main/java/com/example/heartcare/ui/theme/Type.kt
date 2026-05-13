package com.example.heartcare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 默认大字体（大字号模式） */
val HeartCareTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextDark),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextDark),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextDark),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextDark),
    bodyLarge = TextStyle(fontSize = 18.sp, color = TextDark),
    bodyMedium = TextStyle(fontSize = 16.sp, color = TextDark),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = BgWhite),
    labelMedium = TextStyle(fontSize = 16.sp, color = TextDark)
)
