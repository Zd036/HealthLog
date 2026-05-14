package com.rooftop.healthlog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import com.rooftop.healthlog.data.local.AppDatabase
import com.rooftop.healthlog.data.repository.IntakeOutputRepository
import com.rooftop.healthlog.data.repository.MedicationRepository
import com.rooftop.healthlog.data.repository.SettingsRepository
import com.rooftop.healthlog.data.repository.VitalSignsRepository
import com.rooftop.healthlog.worker.DailyAlarmSetupWorker
import com.rooftop.healthlog.worker.MedicationReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 应用 Application 类，负责初始化数据库、Repository、WorkManager。 */
class HealthLogApp : Application(), Configuration.Provider {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val intakeOutputRepository: IntakeOutputRepository by lazy {
        IntakeOutputRepository(database.intakeOutputDao())
    }
    val medicationRepository: MedicationRepository by lazy {
        MedicationRepository(database.medicationDao(), database.medicationRecordDao())
    }
    val vitalSignsRepository: VitalSignsRepository by lazy {
        VitalSignsRepository(database.vitalSignsDao())
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.settingsDao())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        // 异步初始化数据库 + WorkManager 重新调度
        appScope.launch {
            // 触发数据库初始化
            database.openHelper.writableDatabase
            MedicationReminderScheduler.rescheduleAll(this@HealthLogApp)
            // 每日兜底任务：每天凌晨 00:05 重新注册一次所有闹钟
            DailyAlarmSetupWorker.enqueue(this@HealthLogApp)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_REMINDER,
                "用药提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用药时间提醒"
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL_REMINDER = "medication_reminder"
        lateinit var instance: HealthLogApp
            private set
    }
}
