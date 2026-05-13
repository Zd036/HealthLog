package com.example.heartcare.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.heartcare.HeartCareApp
import com.example.heartcare.ui.medication.MedicationReminderActivity

/**
 * 用药提醒 Worker。被 WorkManager 在设定时间点唤起：
 * 1. 启动全屏 MedicationReminderActivity
 * 2. 重新调度同一时间点（下一天）
 */
class MedicationReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getLong(MedicationReminderScheduler.KEY_SCHEDULE_ID, -1L)
        val time = inputData.getString(MedicationReminderScheduler.KEY_TIME) ?: return Result.success()
        if (scheduleId == -1L) return Result.success()

        // 启动全屏提醒 Activity
        val app = applicationContext as HeartCareApp
        val intent = Intent(app, MedicationReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MedicationReminderActivity.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(MedicationReminderActivity.EXTRA_TIME, time)
        }
        app.startActivity(intent)

        // 重新调度下一次（24h 后）
        MedicationReminderScheduler.scheduleOne(applicationContext, scheduleId, time)
        return Result.success()
    }
}
