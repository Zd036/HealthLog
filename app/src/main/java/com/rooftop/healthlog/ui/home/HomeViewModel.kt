package com.rooftop.healthlog.ui.home

import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.data.local.entity.Medication
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.data.local.entity.MedicationSchedule
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import com.rooftop.healthlog.ui.components.UiFeedbackBus
import com.rooftop.healthlog.utils.buildDailyVitalAlertMessages
import com.rooftop.healthlog.utils.buildRecentVitalItems
import com.rooftop.healthlog.utils.DateUtils
import com.rooftop.healthlog.utils.MEDICATION_STATUS_MISSED
import com.rooftop.healthlog.utils.MEDICATION_STATUS_TAKEN
import com.rooftop.healthlog.utils.RecentVitalItem
import com.rooftop.healthlog.worker.MedicationReminderScheduler
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 首页总状态 */
data class HomeUiState(
    val totalIntake: Float = 0f,
    val totalOutput: Float = 0f,
    val stoolCount: Int = 0,
    val hasIntakeOutput: Boolean = false,
    val recentVitals: List<RecentVitalItem> = emptyList(),
    val todayMedicationSchedules: List<PendingSchedule> = emptyList(),
    val medicationDone: Boolean = false,
    val alerts: List<String> = emptyList(),
    val showThreeDayImbalanceAlert: Boolean = false,
    /** 用户点击的待服时间点，非 null 时弹出确认 BottomSheet */
    val clickedSchedule: PendingSchedule? = null
)

enum class MedicationSlotStatus { PENDING, TAKEN, MISSED }

data class PendingSchedule(
    val schedule: MedicationSchedule,
    val medications: List<Medication>,
    val scheduledAt: Long,
    val status: MedicationSlotStatus
)

class HomeViewModel(app: HealthLogApp) : AndroidViewModel(app) {

    private val intakeRepo = app.intakeOutputRepository
    private val medRepo = app.medicationRepository
    private val vitalsRepo = app.vitalSignsRepository
    private val settingsRepo = app.settingsRepository

    // 额外的可变状态（不进入 combine，避免重组风暴）
    private val _clickedSchedule = MutableStateFlow<PendingSchedule?>(null)

    private val currentDayStartFlow: Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val todayStart = DateUtils.dayStartOf(now)
            emit(todayStart)
            val tomorrowStart = DateUtils.dayRangeOf(now).second
            kotlinx.coroutines.delay((tomorrowStart - now).coerceAtLeast(1L))
        }
    }.distinctUntilChanged()

    private val todayRecordsFlow: Flow<List<IntakeOutputRecord>> =
        currentDayStartFlow.flatMapLatest { dayStart ->
            val dayEnd = DateUtils.dayRangeOf(dayStart).second
            intakeRepo.records(dayStart, dayEnd)
        }

    private val threeDayExcessIntakeFlow: Flow<Boolean> = flow {
        while (true) {
            emit(computeThreeDayExcessIntake())
            kotlinx.coroutines.delay(60_000L)
        }
    }

    private val vitalAlertWindowFlow: Flow<List<VitalSignsRecord>> =
        currentDayStartFlow.flatMapLatest { dayStart ->
            val dayEnd = DateUtils.dayRangeOf(dayStart).second
            vitalsRepo.between(DateUtils.daysAgoStart(1), dayEnd)
        }

    val uiState: StateFlow<HomeUiState> =
        combine(
            combine(
                todayRecordsFlow,
                vitalAlertWindowFlow,
                todayMedicationStateFlow()
            ) { r, alertWindowVitals, m ->
                Triple(r, alertWindowVitals, m)
            },
            combine(threeDayExcessIntakeFlow, settingsRepo.settings) { b, s -> b to s },
            _clickedSchedule
        ) { (records, alertWindowVitals, medPair), (threeDayBad, settings), clicked ->
            val (schedules, done) = medPair
            val (intakeSum, outputSum, stool) = aggregate(records)
            val todayStart = DateUtils.dayStartOf(System.currentTimeMillis())
            val todayVitals = alertWindowVitals.filter { it.time >= todayStart }
            val recentVitals = buildRecentVitalItems(todayVitals, alertWindowVitals)
            val alerts = buildAlerts(records, alertWindowVitals)
            val dismissedToday = settings.lastDismissedThreeDayAlertDate == todayStart
            HomeUiState(
                totalIntake = intakeSum,
                totalOutput = outputSum,
                stoolCount = stool,
                hasIntakeOutput = records.isNotEmpty(),
                recentVitals = recentVitals,
                todayMedicationSchedules = schedules,
                medicationDone = done,
                alerts = alerts,
                showThreeDayImbalanceAlert = threeDayBad && !dismissedToday,
                clickedSchedule = clicked
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun dismissThreeDayAlertToday() {
        viewModelScope.launch { settingsRepo.setThreeDayAlertDismissed(DateUtils.dayStartOf(System.currentTimeMillis())) }
    }

    /** 用户点击某个待服时间点 */
    fun onScheduleClick(pending: PendingSchedule) {
        viewModelScope.launch {
            // 修改点2：BottomSheet 弹出前先校验该时间点今天是否已处理。
            if (medRepo.countRecordedTodayForSchedule(pending.schedule.id, pending.scheduledAt) > 0 ||
                pending.status != MedicationSlotStatus.PENDING
            ) {
                _clickedSchedule.value = null
                UiFeedbackBus.show("该时间点已处理")
                return@launch
            }
            _clickedSchedule.value = pending
        }
    }

    fun clearClickedSchedule() { _clickedSchedule.value = null }

    /** 确认已服用：按时间点统一写入记录，并阻止 taken/missed 重复标记。 */
    fun confirmTaken(pending: PendingSchedule) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val records = pending.medications.map { med ->
                MedicationRecord(
                    scheduleId = pending.schedule.id,
                    medicationId = med.id,
                    medicationName = med.name,
                    dosage = med.dosage,
                    unit = med.unit,
                    scheduledTime = pending.scheduledAt,
                    actualTime = now,
                    status = MEDICATION_STATUS_TAKEN
                )
            }
            // 修改点2：数据层再次以时间点粒度防重复，taken/missed 任一已存在都拒绝。
            val inserted = medRepo.insertRecordsIfNotRecorded(
                pending.schedule.id,
                pending.scheduledAt,
                records
            )
            if (!inserted) {
                _clickedSchedule.value = null
                UiFeedbackBus.show("该时间点药品已标记，不可重复操作")
                return@launch
            }
            NotificationManagerCompat.from(getApplication()).cancel(pending.schedule.id.toInt())
            // 修改点2：处理完成后取消当前自动漏服任务，并注册下一天提醒。
            MedicationReminderScheduler.rescheduleAll(getApplication())
            _clickedSchedule.value = null
            UiFeedbackBus.show("已记录服药")
        }
    }

    /** 标记漏服：与已服用完全一致，同样走时间点级别的防重复逻辑。 */
    fun confirmMissed(pending: PendingSchedule) {
        viewModelScope.launch {
            val records = pending.medications.map { med ->
                MedicationRecord(
                    scheduleId = pending.schedule.id,
                    medicationId = med.id,
                    medicationName = med.name,
                    dosage = med.dosage,
                    unit = med.unit,
                    scheduledTime = pending.scheduledAt,
                    actualTime = null,
                    status = MEDICATION_STATUS_MISSED
                )
            }
            val inserted = medRepo.insertRecordsIfNotRecorded(
                pending.schedule.id,
                pending.scheduledAt,
                records
            )
            if (!inserted) {
                _clickedSchedule.value = null
                UiFeedbackBus.show("该时间点药品已标记，不可重复操作")
                return@launch
            }
            NotificationManagerCompat.from(getApplication()).cancel(pending.schedule.id.toInt())
            MedicationReminderScheduler.rescheduleAll(getApplication())
            _clickedSchedule.value = null
            UiFeedbackBus.show("已标记为漏服")
        }
    }

    private fun aggregate(records: List<IntakeOutputRecord>): Triple<Float, Float, Int> {
        var intake = 0f; var output = 0f; var stool = 0
        for (r in records) {
            when {
                r.type == "intake" -> intake += r.amount
                r.type == "output" && r.category == "大便" -> stool += r.amount.toInt()
                r.type == "output" -> output += r.amount
            }
        }
        return Triple(intake, output, stool)
    }

    private suspend fun computeThreeDayExcessIntake(): Boolean {
        for (offset in 0..2) {
            val start = DateUtils.daysAgoStart(offset)
            val end = start + 24L * 3600 * 1000
            val records = intakeRepo.records(start, end).first()
            if (records.isEmpty()) return false
            val diff = intakeMinusOutput(records)
            if (diff <= 500f) return false
        }
        return true
    }

    private fun buildAlerts(
        records: List<IntakeOutputRecord>,
        alertWindowVitals: List<VitalSignsRecord>
    ): List<String> {
        val list = mutableListOf<String>()
        val diff = currentIntakeOutputDiff(records)
        if (diff != null && diff > 500f) list += "今日出入量差值超标（${diff.toInt()}ml），请注意"
        list += buildDailyVitalAlertMessages(alertWindowVitals)
        return list
    }

    private fun currentIntakeOutputDiff(records: List<IntakeOutputRecord>): Float? {
        if (records.isEmpty()) return null
        return intakeMinusOutput(records)
    }

    private fun intakeMinusOutput(records: List<IntakeOutputRecord>): Float {
        var intake = 0f
        var output = 0f
        for (r in records) {
            when {
                r.type == "intake" -> intake += r.amount
                r.type == "output" && r.category != "大便" -> output += r.amount
            }
        }
        return intake - output
    }

    private fun todayMedicationStateFlow(): Flow<Pair<List<PendingSchedule>, Boolean>> {
        return currentDayStartFlow.flatMapLatest { dayStart ->
            val dayEnd = DateUtils.dayRangeOf(dayStart).second
            combine(
                medRepo.getEnabledSchedules(),
                medRepo.getRecordsBetween(dayStart, dayEnd)
            ) { schedules, records ->
                val slots = mutableListOf<PendingSchedule>()
                for (schedule in schedules) {
                    val meds = medRepo.getMedicationsForScheduleSync(schedule.id)
                    if (meds.isEmpty()) continue
                    val scheduledAt = DateUtils.scheduleTimeMillisOnDay(dayStart, schedule.time)
                    val slotRecords = records.filter {
                        it.scheduleId == schedule.id && it.scheduledTime == scheduledAt
                    }
                    val status = when {
                        slotRecords.any { it.status == MEDICATION_STATUS_MISSED } -> MedicationSlotStatus.MISSED
                        slotRecords.any { it.status == MEDICATION_STATUS_TAKEN } -> MedicationSlotStatus.TAKEN
                        else -> MedicationSlotStatus.PENDING
                    }
                    slots += PendingSchedule(
                        schedule = schedule,
                        medications = meds,
                        scheduledAt = scheduledAt,
                        status = status
                    )
                }
                slots to (slots.isNotEmpty() && slots.all { it.status != MedicationSlotStatus.PENDING })
            }
        }
    }
}
