package com.rooftop.healthlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rooftop.healthlog.ui.SplashScreen
import com.rooftop.healthlog.ui.MainScaffold
import com.rooftop.healthlog.ui.appViewModel
import com.rooftop.healthlog.ui.components.FontSizeMode
import com.rooftop.healthlog.ui.components.FontSizeProvider
import com.rooftop.healthlog.ui.settings.SettingsViewModel
import com.rooftop.healthlog.ui.theme.HealthLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsVm: SettingsViewModel = appViewModel()
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            val mode = FontSizeMode.from(settings.fontSize)
            FontSizeProvider(mode = mode) {
                HealthLogTheme(fontSizeMode = mode) {
                    var showSplash by remember { mutableStateOf(true) }
                    AnimatedContent(
                        targetState = showSplash,
                        label = "splash",
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
                    ) { splash ->
                        if (splash) {
                            SplashScreen(onFinish = { showSplash = false })
                        } else {
                            MainScaffold(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
