package com.rooftop.healthlog.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rooftop.healthlog.utils.AutoBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 06:00 自动备份广播。 */
class AutoBackupReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoBackupManager.ACTION_AUTO_BACKUP) return

        val pending = goAsync()
        scope.launch {
            try {
                AutoBackupManager.runIfDue(context.applicationContext)
            } catch (_: Throwable) {
            } finally {
                AutoBackupManager.scheduleNext(context.applicationContext)
                pending.finish()
            }
        }
    }
}
