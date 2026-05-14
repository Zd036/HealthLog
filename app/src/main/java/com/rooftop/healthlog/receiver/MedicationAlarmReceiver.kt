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
import com.rooftop.healthlog.MainActivity
import com.rooftop.healthlog.R
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.ui.medication.MedicationReminderActivity
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
 * 2. 发送高优先级通知（铃声+震动+全屏意图）
 * 3. 尝试启动全屏 Activity（前台时可弹出；后台/锁屏时由全屏意图托底）
 * 4. 为同一时间点的明天再次设置闹钟（每日滚动）
 */
class MedicationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "medication_reminder_v2"
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(MedicationReminderScheduler.EXTRA_SCHEDULE_ID, -1L)
        val time = intent.getStringExtra(MedicationReminderScheduler.EXTRA_TIME) ?: return
        val eventType = intent.getStringExtra(MedicationReminderScheduler.EXTRA_EVENT_TYPE)
            ?: MedicationReminderScheduler.EVENT_REMINDER
        val scheduledAt = intent.getLongExtra(
            MedicationReminderScheduler.EXTRA_SCHEDULED_AT,
            scheduleTimeMillis(time)
        )
        if (scheduleId == -1L) return

        val pending = goAsync()

        ioScope.launch {
            try {
                val app = context.applicationContext as HealthLogApp

                // 修改点2：该时间点今天只要已 taken 或 missed，就不再重复提醒。
                val handled = app.medicationRepository.countRecordedTodayForSchedule(
                    scheduleId = scheduleId,
                    scheduledTime = scheduledAt
                ) > 0

                when {
                    eventType == MedicationReminderScheduler.EVENT_AUTO_MISSED -> {
                        if (!handled) {
                            autoMarkMissed(app, scheduleId, scheduledAt)
                        }
                        // 修改点2：自动漏服执行完成后再滚动到下一天，避免覆盖当前漏服任务。
                        MedicationReminderScheduler.scheduleNextDay(context, scheduleId, time)
                    }
                    handled -> {
                        // 该时间点若已被提前处理，提醒触发时直接滚动到下一天。
                        MedicationReminderScheduler.scheduleNextDay(context, scheduleId, time)
                    }
                    !handled -> {
                        showNotification(context, scheduleId, time)
                        tryStartFullScreen(context, scheduleId, time)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
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
                    description = "按时服药提醒（高优先级，支持锁屏与全屏）"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 500, 500)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun showNotification(context: Context, scheduleId: Long, time: String) {
        ensureChannel(context)

        // 全屏意图：在后台/锁屏时由系统呈现为全屏 Activity
        val fullIntent = Intent(context, MedicationReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MedicationReminderActivity.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(MedicationReminderActivity.EXTRA_TIME, time)
        }
        val fullPi = PendingIntent.getActivity(
            context,
            scheduleId.toInt(),
            fullIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 点击通知进入主 Activity（兜底，避免无图标）
        val contentPi = PendingIntent.getActivity(
            context,
            (scheduleId + 100000).toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏰ $time 服药时间到")
            .setContentText("请点击查看药品清单并确认服用")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(false)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 500, 500))
            .setContentIntent(contentPi)
            .setFullScreenIntent(fullPi, true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(scheduleId.toInt(), notif)
        } catch (_: SecurityException) {
            // 无 POST_NOTIFICATIONS 权限时忽略
        }
    }

    /** 尝试启动全屏 Activity（前台允许；后台依赖 setFullScreenIntent） */
    private fun tryStartFullScreen(context: Context, scheduleId: Long, time: String) {
        try {
            val i = Intent(context, MedicationReminderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MedicationReminderActivity.EXTRA_SCHEDULE_ID, scheduleId)
                putExtra(MedicationReminderActivity.EXTRA_TIME, time)
            }
            context.startActivity(i)
        } catch (_: Throwable) {
            // 后台禁止启动 Activity 时由全屏通知接管
        }
    }

    private suspend fun autoMarkMissed(app: HealthLogApp, scheduleId: Long, scheduledAt: Long) {
        val schedule = app.medicationRepository.getScheduleById(scheduleId) ?: return
        val meds = app.medicationRepository.getMedicationsForScheduleSync(scheduleId)
        if (meds.isEmpty()) return
        val records = meds.map { med ->
            MedicationRecord(
                scheduleId = schedule.id,
                medicationId = med.id,
                medicationName = med.name,
                dosage = med.dosage,
                unit = med.unit,
                scheduledTime = scheduledAt,
                actualTime = null,
                status = "missed"
            )
        }
        app.medicationRepository.insertRecordsIfNotRecorded(scheduleId, scheduledAt, records)
    }
}
