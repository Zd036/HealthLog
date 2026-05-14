package com.rooftop.healthlog.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rooftop.healthlog.utils.DateUtils
import java.util.concurrent.TimeUnit

/**
 * 修改点2：每日凌晨重新注册所有用药闹钟的 Worker。
 *
 * 与 AlarmManager 配合：WorkManager 在每天凌晨触发一次该 Worker，
 * 由其遍历所有启用的用药时间点重新设置当天/明日的 AlarmManager 闹钟，
 * 提供"日级别"的兜底，确保即使某次闹钟漏触发，第二天也能恢复。
 */
class DailyAlarmSetupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        MedicationReminderScheduler.rescheduleAll(applicationContext)
        Result.success()
    } catch (_: Throwable) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE = "daily_alarm_setup"

        fun enqueue(context: Context) {
            // 计算到下一个凌晨 00:05 的延迟
            val trigger = DateUtils.nextTriggerAt(0, 5)
            val initialDelay = (trigger - System.currentTimeMillis()).coerceAtLeast(0L)
            val req = PeriodicWorkRequestBuilder<DailyAlarmSetupWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
