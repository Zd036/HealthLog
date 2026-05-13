package com.example.heartcare.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.heartcare.HeartCareApp
import com.example.heartcare.ui.appViewModel
import com.example.heartcare.ui.components.UiFeedbackBus
import com.example.heartcare.ui.home.components.*
import com.example.heartcare.ui.settings.SettingsViewModel
import com.example.heartcare.utils.ShareHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** 首页 */
@Composable
fun HomeScreen(
    onRecordIntakeOutput: (intake: Boolean) -> Unit,
    onRecordVitalSigns: () -> Unit
) {
    val vm: HomeViewModel = appViewModel()
    val settingsVm: SettingsViewModel = appViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val shareScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部栏：日期 + 右上角分享按钮
        TopBar(onShare = {
            shareScope.launch {
                try {
                    UiFeedbackBus.show("正在准备分享...")
                    val app = HeartCareApp.instance
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val dayLabel = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(today))
                    val end = today + 24L * 3600 * 1000
                    val meds = app.medicationRepository.getRecordsBetween(today, end).first()
                    ShareHelper.shareDailyReport(
                        context = ctx,
                        dayLabel = dayLabel,
                        intakeTotal = state.totalIntake.toInt(),
                        outputTotal = state.totalOutput.toInt(),
                        stoolCount = state.stoolCount,
                        hasIntakeOutput = state.hasIntakeOutput,
                        showIntakeOutput = settings.enableIntakeOutput,
                        latestVital = state.latestVital,
                        medications = meds
                    )
                    UiFeedbackBus.show("请选择分享方式")
                } catch (e: Exception) {
                    UiFeedbackBus.show("分享失败：${e.message ?: "未知错误"}")
                }
            }
        })

        // 连续 3 天不平衡 红色强警告（最高优先级）
        if (state.showThreeDayImbalanceAlert) {
            ThreeDayImbalanceCard(onDismiss = { vm.dismissThreeDayAlertToday() })
        }

        if (settings.enableIntakeOutput) {
            IntakeOutputCard(
                state = state,
                onIntake = { onRecordIntakeOutput(true) },
                onOutput = { onRecordIntakeOutput(false) }
            )
        }
        VitalSignsCard(
            latest = state.latestVital,
            yesterdayWeight = state.yesterdayWeight,
            onRecord = onRecordVitalSigns
        )
        MedicationCard(
            pending = state.todayPendingSchedules,
            done = state.medicationDone,
            onScheduleClick = { pending ->
                // 修改点5：点击时间点触发服药确认，由 HomeViewModel 处理
                vm.onScheduleClick(pending)
            }
        )

        if (state.alerts.isNotEmpty()) {
            AlertCard(messages = state.alerts)
        }

        Spacer(Modifier.height(80.dp))
    }

    // 修改点5：服药确认 Bottom Sheet
    val clickedSchedule = state.clickedSchedule
    if (clickedSchedule != null) {
        MedicationConfirmSheet(
            pending = clickedSchedule,
            onDismiss = { vm.clearClickedSchedule() },
            onConfirmTaken = { vm.confirmTaken(clickedSchedule) },
            onConfirmMissed = { vm.confirmMissed(clickedSchedule) }
        )
    }

    // 购药提醒对话框
    val refillMed = state.refillReminderMed
    if (refillMed != null) {
        AlertDialog(
            onDismissRequest = { vm.clearRefillReminder() },
            title = { Text("库存不足提醒", style = MaterialTheme.typography.headlineMedium) },
            text = { Text("${refillMed.name} 剩余量不足 3 天，请及时购药。", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { vm.clearRefillReminder() }) {
                    Text("知道了", style = MaterialTheme.typography.titleLarge)
                }
            }
        )
    }
}

@Composable
private fun TopBar(onShare: () -> Unit) {
    val cal = Calendar.getInstance()
    val dateStr = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(cal.time)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(dateStr, style = MaterialTheme.typography.headlineMedium)
        // 分享图标按钮，点击区域 ≥ 48dp
        IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Share, contentDescription = "分享当天记录",
                modifier = Modifier.size(24.dp))
        }
    }
}
