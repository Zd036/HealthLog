package com.rooftop.healthlog.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rooftop.healthlog.worker.MedicationReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 修改点2：开机广播接收器。
 *
 * AlarmManager 的闹钟在设备重启后会丢失，因此在 BOOT_COMPLETED 时重新注册所有用药闹钟。
 */
class BootCompletedReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pending = goAsync()
        scope.launch {
            try {
                MedicationReminderScheduler.rescheduleAll(context.applicationContext)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}
