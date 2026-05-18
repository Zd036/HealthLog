package com.rooftop.healthlog

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rooftop.healthlog.receiver.MedicationAlarmReceiver
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
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

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

    override fun onResume() {
        super.onResume()
        launchActiveMedicationReminderIfNeeded()
    }

    /**
     * MainActivity 是 singleTask。用户从桌面图标回到应用时，系统会先把它拉到前台，
     * 这会清掉其上的提醒页。若当前仍有未处理的服药提醒通知，则立即重新打开提醒页。
     */
    private fun launchActiveMedicationReminderIfNeeded() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val reminderIntent = nm.activeNotifications
            .asSequence()
            .filter { it.notification.channelId == MedicationAlarmReceiver.CHANNEL_ID }
            .sortedByDescending { it.postTime }
            .mapNotNull { it.notification.contentIntent }
            .firstOrNull()
            ?: return

        try {
            reminderIntent.send()
        } catch (_: PendingIntent.CanceledException) {
        }
    }
}
