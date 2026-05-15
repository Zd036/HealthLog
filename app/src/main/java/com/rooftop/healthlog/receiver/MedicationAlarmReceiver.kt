package com.rooftop.healthlog.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.R
import com.rooftop.healthlog.ui.medication.MedicationReminderActivity
import com.rooftop.healthlog.utils.MedicationReminderActionHandler
import com.rooftop.healthlog.worker.MedicationReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 修改点2：用药提醒 BroadcastReceiver。
 *
 * 由 AlarmManager 在精确时间唤起：
 * 1. 校验该时间点今天是否已处理（taken/missed），避免重复提醒
 * 2. 发送高优先级常驻通知（铃声+震动）
 * 3. 若用户未处理，则每 30 分钟再次提醒；若用户点“稍后提醒”，则 10 分钟后再次提醒
 * 4. 超过 3 小时仍未处理则自动标记漏服
 */
class MedicationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "medication_reminder_v3"
        const val ACTION_MARK_TAKEN = "com.rooftop.healthlog.MEDICATION_MARK_TAKEN"
        const val ACTION_SNOOZE = "com.rooftop.healthlog.MEDICATION_SNOOZE"
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()

        ioScope.launch {
            try {
                when (intent.action) {
                    ACTION_MARK_TAKEN -> handleMarkTaken(context, intent)
                    ACTION_SNOOZE -> handleSnooze(context, intent)
                    MedicationReminderScheduler.ACTION_ALARM -> handleAlarm(context, intent)
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleAlarm(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(MedicationReminderScheduler.EXTRA_SCHEDULE_ID, -1L)
        val time = intent.getStringExtra(MedicationReminderScheduler.EXTRA_TIME) ?: return
        val eventType = intent.getStringExtra(MedicationReminderScheduler.EXTRA_EVENT_TYPE)
            ?: MedicationReminderScheduler.EVENT_REMINDER
        val scheduledAt = intent.getLongExtra(
            MedicationReminderScheduler.EXTRA_SCHEDULED_AT,
            scheduleTimeMillis(time)
        )
        if (scheduleId == -1L) return

        val app = context.applicationContext as HealthLogApp
        val handled = app.medicationRepository.countRecordedTodayForSchedule(
            scheduleId = scheduleId,
            scheduledTime = scheduledAt
        ) > 0
        val hasMeds = app.medicationRepository.hasMedicationsForSchedule(scheduleId)
        when {
            !hasMeds -> {
                cancelNotification(context, scheduleId)
                MedicationReminderScheduler.cancelOne(context, scheduleId)
            }
            eventType == MedicationReminderScheduler.EVENT_AUTO_MISSED -> {
                if (!handled) {
                    MedicationReminderActionHandler.markMissed(app, scheduleId, scheduledAt)
                }
                cancelNotification(context, scheduleId)
                MedicationReminderScheduler.rescheduleAll(context)
            }
            handled -> {
                cancelNotification(context, scheduleId)
                MedicationReminderScheduler.rescheduleAll(context)
            }
            else -> {
                showNotification(
                    context = context,
                    scheduleId = scheduleId,
                    time = time,
                    scheduledAt = scheduledAt
                )
                MedicationReminderScheduler.scheduleReminderRepeat(context, scheduleId, time, scheduledAt)
            }
        }
    }

    private suspend fun handleMarkTaken(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(MedicationReminderScheduler.EXTRA_SCHEDULE_ID, -1L)
        val time = intent.getStringExtra(MedicationReminderScheduler.EXTRA_TIME) ?: return
        val scheduledAt = intent.getLongExtra(
            MedicationReminderScheduler.EXTRA_SCHEDULED_AT,
            scheduleTimeMillis(time)
        )
        if (scheduleId == -1L) return

        val app = context.applicationContext as HealthLogApp
        val hasMeds = app.medicationRepository.hasMedicationsForSchedule(scheduleId)
        cancelNotification(context, scheduleId)

        if (!hasMeds) {
            MedicationReminderScheduler.cancelOne(context, scheduleId)
            return
        }

        MedicationReminderActionHandler.markTaken(app, scheduleId, scheduledAt)
        MedicationReminderScheduler.rescheduleAll(context)
    }

    private suspend fun handleSnooze(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(MedicationReminderScheduler.EXTRA_SCHEDULE_ID, -1L)
        val time = intent.getStringExtra(MedicationReminderScheduler.EXTRA_TIME) ?: return
        val scheduledAt = intent.getLongExtra(
            MedicationReminderScheduler.EXTRA_SCHEDULED_AT,
            scheduleTimeMillis(time)
        )
        if (scheduleId == -1L) return

        val app = context.applicationContext as HealthLogApp
        val handled = app.medicationRepository.countRecordedTodayForSchedule(
            scheduleId = scheduleId,
            scheduledTime = scheduledAt
        ) > 0
        cancelNotification(context, scheduleId)

        if (handled) {
            MedicationReminderScheduler.rescheduleAll(context)
            return
        }

        MedicationReminderScheduler.scheduleSnooze(context, scheduleId, time, scheduledAt)
    }

    private fun scheduleTimeMillis(time: String): Long {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "服药提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "服药提醒"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 500, 500)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun showNotification(
        context: Context,
        scheduleId: Long,
        time: String,
        scheduledAt: Long = scheduleTimeMillis(time),
    ) {
        ensureChannel(context)

        val reminderIntent = Intent(context, MedicationReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MedicationReminderActivity.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(MedicationReminderActivity.EXTRA_TIME, time)
            putExtra(MedicationReminderActivity.EXTRA_SCHEDULED_AT, scheduledAt)
        }
        val reminderPi = PendingIntent.getActivity(
            context,
            scheduleId.toInt(),
            reminderIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takenPi = PendingIntent.getBroadcast(
            context,
            (scheduleId + 200000).toInt(),
            Intent(context, MedicationAlarmReceiver::class.java).apply {
                action = ACTION_MARK_TAKEN
                putExtra(MedicationReminderScheduler.EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(MedicationReminderScheduler.EXTRA_TIME, time)
                putExtra(MedicationReminderScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getBroadcast(
            context,
            (scheduleId + 300000).toInt(),
            Intent(context, MedicationAlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(MedicationReminderScheduler.EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(MedicationReminderScheduler.EXTRA_TIME, time)
                putExtra(MedicationReminderScheduler.EXTRA_SCHEDULED_AT, scheduledAt)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ $time 服药时间到")
            .setContentText("请按时服药，或选择10分钟后再次提醒")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 500, 500))
            .setContentIntent(reminderPi)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "已服用",
                takenPi
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                "稍后提醒",
                snoozePi
            )
        val notif = builder.build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId(scheduleId), notif)
        } catch (_: SecurityException) {
            // 无 POST_NOTIFICATIONS 权限时忽略
        }
    }

    private fun cancelNotification(context: Context, scheduleId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(scheduleId))
    }

    private fun notificationId(scheduleId: Long): Int {
        return scheduleId.toInt()
    }
}
