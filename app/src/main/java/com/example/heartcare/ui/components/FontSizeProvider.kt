package com.example.heartcare.ui.components

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.heartcare.ui.theme.BgWhite
import com.example.heartcare.ui.theme.TextDark

/** 字体大小模式 */
enum class FontSizeMode(val key: String) {
    NORMAL("normal"),
    LARGE("large");

    companion object {
        fun from(key: String?): FontSizeMode =
            if (key == NORMAL.key) NORMAL else LARGE
    }
}

/** 当前字体大小（CompositionLocal） */
val LocalFontSizeMode = compositionLocalOf { FontSizeMode.LARGE }

/** 字体尺寸映射表 */
object AppFontSizes {
    fun pageTitle(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 24.sp else 20.sp
    fun cardTitle(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 20.sp else 18.sp
    fun body(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 18.sp else 16.sp
    fun button(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 20.sp else 18.sp
    fun input(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 20.sp else 16.sp
    fun inputLabel(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 18.sp else 14.sp
    fun listItem(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 18.sp else 16.sp
    fun axis(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 14.sp else 12.sp
    fun tab(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 16.sp else 14.sp
    fun display(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 32.sp else 28.sp
    fun headlineLarge(m: FontSizeMode): TextUnit = if (m == FontSizeMode.LARGE) 28.sp else 24.sp
}

/** 根据字体大小模式构造 Typography */
fun buildTypography(m: FontSizeMode): Typography = Typography(
    displayLarge = TextStyle(fontSize = AppFontSizes.display(m), fontWeight = FontWeight.Bold, color = TextDark),
    headlineLarge = TextStyle(fontSize = AppFontSizes.headlineLarge(m), fontWeight = FontWeight.Bold, color = TextDark),
    headlineMedium = TextStyle(fontSize = AppFontSizes.pageTitle(m), fontWeight = FontWeight.Bold, color = TextDark),
    titleLarge = TextStyle(fontSize = AppFontSizes.cardTitle(m), fontWeight = FontWeight.Bold, color = TextDark),
    titleMedium = TextStyle(fontSize = AppFontSizes.listItem(m), fontWeight = FontWeight.SemiBold, color = TextDark),
    bodyLarge = TextStyle(fontSize = AppFontSizes.body(m), color = TextDark),
    bodyMedium = TextStyle(fontSize = AppFontSizes.listItem(m), color = TextDark),
    labelLarge = TextStyle(fontSize = AppFontSizes.button(m), fontWeight = FontWeight.SemiBold, color = BgWhite),
    labelMedium = TextStyle(fontSize = AppFontSizes.inputLabel(m), color = TextDark)
)

@Composable
fun FontSizeProvider(mode: FontSizeMode, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFontSizeMode provides mode) { content() }
}
