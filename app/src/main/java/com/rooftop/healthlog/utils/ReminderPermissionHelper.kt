package com.rooftop.healthlog.utils

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rooftop.healthlog.receiver.MedicationAlarmReceiver

data class ReminderPermissionStatus(
    val notificationsReady: Boolean,
    val exactAlarmReady: Boolean,
    val fullScreenReady: Boolean,
) {
    val allReady: Boolean
        get() = allReady(requireFullScreen = true)

    fun allReady(requireFullScreen: Boolean): Boolean =
        notificationsReady && exactAlarmReady && (!requireFullScreen || fullScreenReady)
}

object ReminderPermissionHelper {

    fun status(context: Context): ReminderPermissionStatus {
        val notificationManager = NotificationManagerCompat.from(context)
        val appNotificationsEnabled = notificationManager.areNotificationsEnabled()
        val runtimeNotificationGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        val reminderChannelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = nm?.getNotificationChannel(MedicationAlarmReceiver.CHANNEL_ID)
            channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }

        val exactAlarmReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val fullScreenReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.canUseFullScreenIntent() == true
        } else {
            true
        }

        return ReminderPermissionStatus(
            notificationsReady = appNotificationsEnabled && runtimeNotificationGranted && reminderChannelEnabled,
            exactAlarmReady = exactAlarmReady,
            fullScreenReady = fullScreenReady,
        )
    }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    fun exactAlarmSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            appDetailsSettingsIntent(context)
        }

    fun fullScreenSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            appDetailsSettingsIntent(context)
        }

    private fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}
