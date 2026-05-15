package com.rooftop.healthlog.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.receiver.MedicationAlarmReceiver
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * 修改点2：服药提醒调度器（基于 AlarmManager）。
 *
 * 替代原先基于 WorkManager 的实现，确保 APP 处于后台 / 被杀 / Doze 模式下，
 * 系统仍能在精确时间唤起 BroadcastReceiver 触发提醒。
 */
object MedicationReminderScheduler {

    const val ACTION_ALARM = "com.rooftop.healthlog.MEDICATION_ALARM"
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_TIME = "schedule_time"
    const val EXTRA_EVENT_TYPE = "event_type"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"

    const val EVENT_REMINDER = "reminder"
    const val EVENT_AUTO_MISSED = "auto_missed"
    private const val MISSED_DELAY_MS = 3L * 3600_000L
    private const val SNOOZE_DELAY_MS = 10L * 60_000L
    private const val REMINDER_REPEAT_DELAY_MS = 30L * 60_000L

    /** 重新调度所有启用的时间点（取消旧的，再添加新的） */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as HealthLogApp
        val all = app.medicationRepository.getAllSchedules().first()
        for (s in all) cancelOne(context, s.id)
        val enabled = app.medicationRepository.getEnabledSchedules().first()
        for (s in enabled) {
            if (!app.medicationRepository.hasMedicationsForSchedule(s.id)) continue
            scheduleNextRelevant(context, s.id, s.time)
        }
    }

    /** 为单个时间点设置下一次需要处理的轮次。 */
    suspend fun scheduleNextRelevant(context: Context, scheduleId: Long, time: String) {
        val app = context.applicationContext as HealthLogApp
        if (!app.medicationRepository.hasMedicationsForSchedule(scheduleId)) {
            cancelOne(context, scheduleId)
            return
        }
        val now = System.currentTimeMillis()
        val scheduledAt = nextOccurrenceMillis(time, now)
        val handled = app.medicationRepository.countRecordedTodayForSchedule(scheduleId, scheduledAt) > 0
        when {
            handled -> scheduleOccurrence(context, scheduleId, time, nextDayMillis(time, now))
            now < scheduledAt -> scheduleOccurrence(context, scheduleId, time, scheduledAt)
            now < scheduledAt + MISSED_DELAY_MS -> scheduleOccurrence(
                context = context,
                scheduleId = scheduleId,
                time = time,
                scheduledAt = scheduledAt,
                reminderTriggerAt = now
            )
            else -> scheduleOccurrence(
                context = context,
                scheduleId = scheduleId,
                time = time,
                scheduledAt = scheduledAt,
                scheduleReminder = false,
                autoMissedTriggerAt = now
            )
        }
    }

    /** 为单个时间点设置下一个闹钟（今天该时间已过但未处理时仍补今天这一轮） */
    fun nextOccurrenceMillis(time: String, now: Long = System.currentTimeMillis()): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return now
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return now
        return Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** 服用完成后，为同一时间点的"明天"再设置一次（保证持续滚动） */
    fun scheduleNextDay(context: Context, scheduleId: Long, time: String) {
        scheduleOccurrence(context, scheduleId, time, nextDayMillis(time))
    }

    /** 用户点击“稍后提醒”后，10 分钟再提醒一次。 */
    fun scheduleSnooze(
        context: Context,
        scheduleId: Long,
        time: String,
        scheduledAt: Long,
        delayMs: Long = SNOOZE_DELAY_MS
    ) {
        scheduleReminderRepeat(
            context = context,
            scheduleId = scheduleId,
            time = time,
            scheduledAt = scheduledAt,
            delayMs = delayMs
        )
    }

    /** 半小时后再次提醒；若已接近自动漏服截止时间，则最多提醒到截止前。 */
    fun scheduleReminderRepeat(
        context: Context,
        scheduleId: Long,
        time: String,
        scheduledAt: Long,
        delayMs: Long = REMINDER_REPEAT_DELAY_MS
    ) {
        val latestReminderAt = scheduledAt + MISSED_DELAY_MS - 1_000L
        val now = System.currentTimeMillis()
        val triggerAt = minOf(now + delayMs, latestReminderAt)
        if (triggerAt <= now) return
        setAlarmAt(
            context = context,
            scheduleId = scheduleId,
            time = time,
            triggerAt = triggerAt,
            eventType = EVENT_REMINDER,
            scheduledAt = scheduledAt,
            useAlarmClock = false
        )
    }

    private fun nextDayMillis(time: String, now: Long = System.currentTimeMillis()): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return now
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return now
        return Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    /** 取消单个时间点的闹钟 */
    fun cancelOne(context: Context, scheduleId: Long) {
        cancelByType(context, scheduleId, EVENT_REMINDER)
        cancelByType(context, scheduleId, EVENT_AUTO_MISSED)
    }

    /** 修改点2：时间点处理完成后取消当前"自动漏服"任务，避免已处理后再次写 missed。 */
    fun cancelAutoMissed(context: Context, scheduleId: Long) {
        cancelByType(context, scheduleId, EVENT_AUTO_MISSED)
    }

    private fun scheduleOccurrence(
        context: Context,
        scheduleId: Long,
        time: String,
        scheduledAt: Long,
        reminderTriggerAt: Long = scheduledAt,
        autoMissedTriggerAt: Long = scheduledAt + MISSED_DELAY_MS,
        scheduleReminder: Boolean = true,
    ) {
        if (scheduleReminder) {
            setAlarmAt(
                context = context,
                scheduleId = scheduleId,
                time = time,
                triggerAt = reminderTriggerAt,
                eventType = EVENT_REMINDER,
                scheduledAt = scheduledAt,
                useAlarmClock = true
            )
        } else {
            cancelByType(context, scheduleId, EVENT_REMINDER)
        }
        setAlarmAt(
            context = context,
            scheduleId = scheduleId,
            time = time,
            triggerAt = autoMissedTriggerAt,
            eventType = EVENT_AUTO_MISSED,
            scheduledAt = scheduledAt,
            useAlarmClock = false
        )
    }

    private fun cancelByType(context: Context, scheduleId: Long, eventType: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(
            context = context,
            scheduleId = scheduleId,
            time = null,
            eventType = eventType,
            scheduledAt = 0L,
            noCreate = true
        )
        pi?.let { am.cancel(it); it.cancel() }
    }

    /** 内部：在指定时刻设置 AlarmClock（高优先级、Doze 下也能触发） */
    private fun setAlarmAt(
        context: Context,
        scheduleId: Long,
        time: String,
        triggerAt: Long,
        eventType: String,
        scheduledAt: Long,
        useAlarmClock: Boolean
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, scheduleId, time, eventType, scheduledAt) ?: return

        if (useAlarmClock && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pi), pi)
            } else {
                // 无精确闹钟权限时回退（Doze 下可能略有延迟）
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun buildPendingIntent(
        context: Context,
        scheduleId: Long,
        time: String?,
        eventType: String,
        scheduledAt: Long,
        noCreate: Boolean = false
    ): PendingIntent? {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            time?.let { putExtra(EXTRA_TIME, it) }
            putExtra(EXTRA_EVENT_TYPE, eventType)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
        }
        var flags = PendingIntent.FLAG_IMMUTABLE
        flags = flags or if (noCreate) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT
        val requestCode = when (eventType) {
            EVENT_REMINDER -> scheduleId.toInt()
            EVENT_AUTO_MISSED -> scheduleId.toInt() + 100000
            else -> scheduleId.toInt()
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
