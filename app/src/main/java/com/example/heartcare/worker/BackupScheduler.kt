package com.example.heartcare.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.heartcare.utils.DateUtils
import java.util.concurrent.TimeUnit

/** 调度每日凌晨 2:00 的数据库备份任务 */
object BackupScheduler {

    private const val UNIQUE_NAME = "database_backup_daily"
    const val TAG = "database_backup"

    /** 排程下一次凌晨 2:00 的备份（完成后 Worker 会自行重新排程） */
    fun scheduleDaily(context: Context) {
        val trigger = DateUtils.nextTriggerAt(2, 0)
        val delay = (trigger - System.currentTimeMillis()).coerceAtLeast(0L)
        val req = OneTimeWorkRequestBuilder<DatabaseBackupWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    /** 立即备份（手动触发），不延迟 */
    fun runNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<DatabaseBackupWorker>()
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
