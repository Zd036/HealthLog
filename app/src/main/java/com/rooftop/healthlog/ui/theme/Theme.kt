package com.rooftop.healthlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rooftop.healthlog.ui.components.FontSizeMode
import com.rooftop.healthlog.ui.components.LocalFontSizeMode
import com.rooftop.healthlog.ui.components.buildTypography

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
    onError = BgWhite,
    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = BgWhite,
    inversePrimary = PrimaryBlue
)

@Composable
fun HealthLogTheme(
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
