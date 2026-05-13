package com.example.heartcare.worker

import android.content.Context
import androidx.work.*
import com.example.heartcare.HeartCareApp
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 调度器：根据所有启用的用药时间点，安排一次性 OneTimeWorkRequest（每天滚动） */
object MedicationReminderScheduler {

    private const val UNIQUE_PREFIX = "med_reminder_"
    private const val TAG = "med_reminder"
    const val KEY_SCHEDULE_ID = "scheduleId"
    const val KEY_TIME = "time"

    /** 重新调度所有启用的时间点 */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as HeartCareApp
        val schedules = app.medicationRepository.getEnabledSchedules().first()
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG)
        for (s in schedules) scheduleOne(context, s.id, s.time)
    }

    /** 安排一次性提醒：下一次该时间触发，触发后 Worker 内部再次自调度 */
    fun scheduleOne(context: Context, scheduleId: Long, time: String) {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = next.timeInMillis - now.timeInMillis
        val req = OneTimeWorkRequestBuilder<MedicationReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_SCHEDULE_ID to scheduleId, KEY_TIME to time))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_PREFIX + scheduleId,
            ExistingWorkPolicy.REPLACE,
            req
        )
    }
}
