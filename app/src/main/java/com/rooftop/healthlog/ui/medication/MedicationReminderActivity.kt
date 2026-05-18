package com.rooftop.healthlog.ui.medication

import android.os.Bundle
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
import com.rooftop.healthlog.utils.DateUtils
import com.rooftop.healthlog.utils.MedicationReminderActionHandler
import com.rooftop.healthlog.worker.MedicationReminderScheduler
import kotlinx.coroutines.launch

/**
 * 通知点击后的用药提醒 Activity：
 * - 展示当前时间点的处理入口
 * - 支持“已服用”和“稍后 10 分钟提醒”
 */
class MedicationReminderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_TIME = "time"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        val time = intent.getStringExtra(EXTRA_TIME) ?: "00:00"
        val scheduledAt = intent.getLongExtra(
            EXTRA_SCHEDULED_AT,
            DateUtils.scheduleTimeMillisOnDay(DateUtils.dayStartOf(System.currentTimeMillis()), time)
        )
        if (scheduleId == -1L) { finish(); return }

        lifecycleScope.launch {
            if (isHandled(scheduleId, scheduledAt)) {
                cancelReminderNotification(scheduleId)
                finish()
                return@launch
            }
            showReminderContent(scheduleId, time, scheduledAt)
        }
    }

    private fun showReminderContent(scheduleId: Long, time: String, scheduledAt: Long) {
        setContent {
            HealthLogTheme {
                ReminderContent(
                    scheduleId = scheduleId,
                    time = time,
                    onEmpty = {
                        lifecycleScope.launch {
                            cancelReminderNotification(scheduleId)
                            finish()
                        }
                    },
                    onTaken = { meds ->
                        lifecycleScope.launch {
                            val inserted = recordTaken(scheduleId, scheduledAt)
                            if (!inserted) {
                                cancelReminderNotification(scheduleId)
                                finish()
                                return@launch
                            }
                            cancelReminderNotification(scheduleId)
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
                            finish()
                        }
                    }
                )
            }
        }
    }

    /** 通知详情页确认服药也按时间点防重复。 */
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

    private suspend fun isHandled(scheduleId: Long, scheduledAt: Long): Boolean {
        val app = applicationContext as HealthLogApp
        return app.medicationRepository.countRecordedTodayForSchedule(scheduleId, scheduledAt) > 0
    }

    private fun cancelReminderNotification(scheduleId: Long) {
        NotificationManagerCompat.from(this).cancel(scheduleId.toInt())
    }

    override fun onBackPressed() {
        // 不允许跳过
    }
}

/** 提醒内容 */
@Composable
private fun ReminderContent(
    scheduleId: Long,
    time: String,
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
                        "请按时服药，或选择10分钟后再次提醒。如不处理，系统将每30分钟提醒1次。",
                        style = MaterialTheme.typography.titleMedium
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
                Text("稍后提醒", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
