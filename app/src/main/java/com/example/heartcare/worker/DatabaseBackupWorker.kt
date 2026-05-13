package com.example.heartcare.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.heartcare.HeartCareApp
import com.example.heartcare.utils.BackupManager

/** 数据库备份 Worker：每日凌晨 2 点触发，若当天已备份过则跳过；清理 7 天前的旧备份。 */
class DatabaseBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as HeartCareApp
            val file = BackupManager.performBackup(app, forceOverwrite = false)
            BackupManager.cleanupOld(app, keepDays = 7)
            if (file != null) Result.success() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
