package com.example.heartcare.ui.medication

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
import com.example.heartcare.HeartCareApp
import com.example.heartcare.data.local.entity.Medication
import com.example.heartcare.data.local.entity.MedicationRecord
import com.example.heartcare.ui.theme.HeartCareTheme
import com.example.heartcare.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全屏用药提醒 Activity：
 * - 半透明黑色背景，覆盖整个屏幕
 * - 响铃 5 秒 + 震动
 * - 屏幕常亮直到点击"已服用"
 * - 不允许跳过
 */
class MedicationReminderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_TIME = "time"
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
        if (scheduleId == -1L) { finish(); return }

        startRingAndVibrate()

        setContent {
            HeartCareTheme {
                ReminderContent(
                    scheduleId = scheduleId,
                    time = time,
                    onTaken = { meds ->
                        lifecycleScope.launch {
                            recordTaken(scheduleId, time, meds)
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

    /** 记录服药 + 自动扣减剩余 + 购药提醒 */
    private suspend fun recordTaken(scheduleId: Long, time: String, meds: List<Medication>) {
        val app = applicationContext as HeartCareApp
        val now = System.currentTimeMillis()
        for (m in meds) {
            val record = MedicationRecord(
                scheduleId = scheduleId,
                medicationId = m.id,
                medicationName = m.name,
                dosage = m.dosage,
                unit = m.unit,
                scheduledTime = now,
                actualTime = now,
                status = "taken"
            )
            app.medicationRepository.insertRecord(record)
            // 扣减剩余量
            val newRemaining = (m.remainingQuantity - m.dosage).coerceAtLeast(0f)
            app.medicationRepository.updateMedication(m.copy(remainingQuantity = newRemaining))
        }
    }

    override fun onBackPressed() {
        // 不允许跳过
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingAndVibrate()
    }
}

/** 全屏提醒内容 */
@Composable
private fun ReminderContent(
    scheduleId: Long,
    time: String,
    onTaken: (List<Medication>) -> Unit
) {
    var meds by remember { mutableStateOf<List<Medication>>(emptyList()) }
    LaunchedEffect(scheduleId) {
        meds = HeartCareApp.instance.medicationRepository.getMedicationsForScheduleSync(scheduleId)
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
                        Text("请服用以下药品",
                            style = MaterialTheme.typography.titleLarge)
                    }
                    if (meds.isEmpty()) {
                        Text("（暂无药品）", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        for (m in meds) {
                            val dosage = if (m.dosage == m.dosage.toInt().toFloat())
                                m.dosage.toInt().toString() else "%.1f".format(m.dosage)
                            val spec = if (m.specification > 0)
                                "（${"%.0f".format(m.specification)}mg/${m.unit}）" else ""
                            Text("• ${m.name}：$dosage${m.unit}$spec",
                                style = MaterialTheme.typography.titleLarge)
                        }
                        val methods = meds.mapNotNull {
                            it.method.takeIf { s -> s.isNotBlank() }
                        }.distinct()
                        if (methods.isNotEmpty()) {
                            Text("服用方式：${methods.joinToString("、")}",
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Button(
                onClick = { onTaken(meds) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Text("已服用", fontSize = 22.sp, color = Color.White,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}
