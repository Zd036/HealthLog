package com.rooftop.healthlog.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.receiver.AutoBackupReceiver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/** 每天 06:00 自动备份；若错过则在下次打开 APP 时补备一次。 */
object AutoBackupManager {

    const val ACTION_AUTO_BACKUP = "com.rooftop.healthlog.AUTO_BACKUP"

    private const val BACKUP_HOUR = 6
    private const val BACKUP_MINUTE = 0
    private const val REQUEST_CODE = 410000
    private val mutex = Mutex()

    fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = DateUtils.nextTriggerAt(BACKUP_HOUR, BACKUP_MINUTE)
        val pi = buildPendingIntent(appContext) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    suspend fun runIfDue(context: Context): Boolean = mutex.withLock {
        val app = context.applicationContext as? HealthLogApp ?: return@withLock false
        val now = System.currentTimeMillis()
        val latestDueAt = latestDueAt(now)
        if (app.settingsRepository.getOrDefault().lastAutoBackupAt >= latestDueAt) {
            return@withLock false
        }

        val displayPath = CsvExporter.exportAutoBackup(app, app.buildExportData()) ?: return@withLock false
        CsvExporter.pruneAutoBackups(app, keepCount = 7)
        app.settingsRepository.setLastAutoBackupAt(now)
        displayPath.isNotBlank()
    }

    private fun latestDueAt(now: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, BACKUP_HOUR)
            set(Calendar.MINUTE, BACKUP_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > now) add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
    }

    private fun buildPendingIntent(
        context: Context,
        noCreate: Boolean = false,
    ): PendingIntent? {
        val intent = Intent(context, AutoBackupReceiver::class.java).apply {
            action = ACTION_AUTO_BACKUP
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (noCreate) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
