package com.example.heartcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.heartcare.ui.SplashScreen
import com.example.heartcare.ui.MainScaffold
import com.example.heartcare.ui.appViewModel
import com.example.heartcare.ui.components.FontSizeMode
import com.example.heartcare.ui.components.FontSizeProvider
import com.example.heartcare.ui.settings.SettingsViewModel
import com.example.heartcare.ui.theme.HeartCareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsVm: SettingsViewModel = appViewModel()
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            val mode = FontSizeMode.from(settings.fontSize)
            FontSizeProvider(mode = mode) {
                HeartCareTheme(fontSizeMode = mode) {
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
