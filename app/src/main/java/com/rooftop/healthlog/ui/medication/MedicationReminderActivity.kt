package com.rooftop.healthlog.ui.medication

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.core.app.NotificationManagerCompat
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.Medication
import com.rooftop.healthlog.ui.components.UiFeedbackBus
import com.rooftop.healthlog.ui.theme.HealthLogTheme
import com.rooftop.healthlog.ui.theme.SuccessGreen
import com.rooftop.healthlog.utils.MedicationReminderActionHandler
import com.rooftop.healthlog.worker.MedicationReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 全屏用药提醒 Activity：
 * - 半透明黑色背景，覆盖整个屏幕
 * - 强提醒模式下响铃 5 秒 + 震动
 * - 屏幕常亮直到点击"已服用"
 * - 支持稍后 10 分钟再次提醒
 */
class MedicationReminderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_TIME = "time"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
        const val EXTRA_STRONG_REMINDER = "strongReminder"
    }

    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 屏幕常亮 + 锁屏上展示
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true); setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        }

        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        val time = intent.getStringExtra(EXTRA_TIME) ?: "00:00"
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, scheduleTimeMillis(time))
        val strongReminderEnabled = intent.getBooleanExtra(EXTRA_STRONG_REMINDER, false)
        if (scheduleId == -1L) { finish(); return }

        if (strongReminderEnabled) {
            startRingAndVibrate()
        }

        setContent {
            HealthLogTheme {
                ReminderContent(
                    scheduleId = scheduleId,
                    time = time,
                    strongReminderEnabled = strongReminderEnabled,
                    onEmpty = {
                        lifecycleScope.launch {
                            cancelReminderNotification(scheduleId)
                            stopRingAndVibrate()
                            finish()
                        }
                    },
                    onTaken = { meds ->
                        lifecycleScope.launch {
                            val inserted = recordTaken(scheduleId, scheduledAt)
                            if (!inserted) {
                                stopRingAndVibrate()
                                finish()
                                return@launch
                            }
                            cancelReminderNotification(scheduleId)
                            stopRingAndVibrate()
                            finish()
                        }
                    },
                    onSnooze = {
                        lifecycleScope.launch {
                            MedicationReminderScheduler.scheduleSnooze(
                                context = this@MedicationReminderActivity,
                                scheduleId = scheduleId,
                                time = time,
                                scheduledAt = scheduledAt
                            )
                            cancelReminderNotification(scheduleId)
                            stopRingAndVibrate()
                            finish()
                        }
                    }
                )
            }
        }
    }

    private fun startRingAndVibrate() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.streamType = AudioManager.STREAM_ALARM
            ringtone?.play()
        } catch (_: Throwable) {}

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400, 800, 400, 800), -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(longArrayOf(0, 800, 400, 800, 400, 800), -1)
            }
        }

        // 5 秒后停止响铃
        lifecycleScope.launch {
            delay(5000)
            try { ringtone?.stop() } catch (_: Throwable) {}
        }
    }

    private fun stopRingAndVibrate() {
        try { ringtone?.stop() } catch (_: Throwable) {}
        try { vibrator?.cancel() } catch (_: Throwable) {}
    }

    /** 修改点2：全屏提醒确认服药也按时间点防重复，不再处理库存字段。 */
    private suspend fun recordTaken(scheduleId: Long, scheduledAt: Long): Boolean {
        val app = applicationContext as HealthLogApp
        val inserted = MedicationReminderActionHandler.markTaken(app, scheduleId, scheduledAt)
        if (!inserted) {
            UiFeedbackBus.show("该时间点药品已标记，不可重复操作")
            return false
        }
        MedicationReminderScheduler.rescheduleAll(app)
        return inserted
    }

    private fun cancelReminderNotification(scheduleId: Long) {
        NotificationManagerCompat.from(this).cancel(scheduleId.toInt())
    }

    override fun onBackPressed() {
        // 不允许跳过
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingAndVibrate()
    }
}

private fun scheduleTimeMillis(time: String): Long {
    val parts = time.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** 全屏提醒内容 */
@Composable
private fun ReminderContent(
    scheduleId: Long,
    time: String,
    strongReminderEnabled: Boolean,
    onEmpty: () -> Unit,
    onTaken: (List<Medication>) -> Unit,
    onSnooze: () -> Unit
) {
    var meds by remember { mutableStateOf<List<Medication>>(emptyList()) }
    LaunchedEffect(scheduleId) {
        meds = HealthLogApp.instance.medicationRepository.getMedicationsForScheduleSync(scheduleId)
        if (meds.isEmpty()) {
            MedicationReminderScheduler.rescheduleAll(HealthLogApp.instance)
            onEmpty()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "$time 服药时间到",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Medication, null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("请按时服药",
                            style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        if (strongReminderEnabled) {
                            "强提醒已开启。服用完成后点击“已服用”，或稍后 10 分钟再提醒。"
                        } else {
                            "服用完成后，直接点击下方“已服用”，或选择稍后提醒。"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "不再显示完整药品清单，减少翻动和等待。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF666666)
                    )
                }
            }
            Button(
                onClick = { onTaken(meds) },
                enabled = meds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Text("已服用", fontSize = 22.sp, color = Color.White,
                    fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("10 分钟后提醒", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
