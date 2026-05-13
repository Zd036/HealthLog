package com.example.heartcare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.heartcare.ui.components.FontSizeMode
import com.example.heartcare.ui.components.LocalFontSizeMode
import com.example.heartcare.ui.components.buildTypography

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = BgWhite,
    secondary = SuccessGreen,
    onSecondary = BgWhite,
    background = BgWhite,
    onBackground = TextDark,
    surface = BgWhite,
    onSurface = TextDark,
    error = DangerRed,
    onError = BgWhite
)

@Composable
fun HeartCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontSizeMode: FontSizeMode = LocalFontSizeMode.current,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = buildTypography(fontSizeMode),
        content = content
    )
}
