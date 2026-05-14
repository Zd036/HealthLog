package com.rooftop.healthlog.ui.home

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.data.local.entity.Medication
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.data.local.entity.MedicationSchedule
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import com.rooftop.healthlog.ui.components.UiFeedbackBus
import com.rooftop.healthlog.utils.DateUtils
import com.rooftop.healthlog.worker.MedicationReminderScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

/** 首页总状态 */
data class HomeUiState(
    val totalIntake: Float = 0f,
    val totalOutput: Float = 0f,
    val stoolCount: Int = 0,
    val hasIntakeOutput: Boolean = false,
    val latestVital: VitalSignsRecord? = null,
    val yesterdayWeight: Float? = null,
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
    val status: MedicationSlotStatus
)

class HomeViewModel(app: HealthLogApp) : AndroidViewModel(app) {

    private val intakeRepo = app.intakeOutputRepository
    private val medRepo = app.medicationRepository
    private val vitalsRepo = app.vitalSignsRepository
    private val settingsRepo = app.settingsRepository

    // 额外的可变状态（不进入 combine，避免重组风暴）
    private val _clickedSchedule = MutableStateFlow<PendingSchedule?>(null)

    private val threeDayImbalanceFlow: Flow<Boolean> = flow {
        while (true) {
            emit(computeThreeDayImbalance())
            kotlinx.coroutines.delay(60_000L)
        }
    }

    private val yesterdayWeightFlow: Flow<Float?> = flow {
        while (true) {
            val todayStart = DateUtils.todayStart()
            val yesterdayStart = DateUtils.daysAgoStart(1)
            emit(vitalsRepo.getWeightInRange(yesterdayStart, todayStart)?.weight)
            kotlinx.coroutines.delay(60_000L)
        }
    }

    val uiState: StateFlow<HomeUiState> =
        combine(
            combine(intakeRepo.todayRecords(), vitalsRepo.latest(), todayMedicationStateFlow()) { r, v, m -> Triple(r, v, m) },
            combine(threeDayImbalanceFlow, settingsRepo.settings, yesterdayWeightFlow) { b, s, w -> Triple(b, s, w) },
            _clickedSchedule
        ) { (records, vital, medPair), (threeDayBad, settings, yWeight), clicked ->
            val (schedules, done) = medPair
            val (intakeSum, outputSum, stool) = aggregate(records)
            val alerts = buildAlerts(intakeSum, outputSum, vital, yWeight)
            val dismissedToday = settings.lastDismissedThreeDayAlertDate == DateUtils.todayStart()
            HomeUiState(
                totalIntake = intakeSum,
                totalOutput = outputSum,
                stoolCount = stool,
                hasIntakeOutput = records.isNotEmpty(),
                latestVital = vital,
                yesterdayWeight = yWeight,
                todayMedicationSchedules = schedules,
                medicationDone = done,
                alerts = alerts,
                showThreeDayImbalanceAlert = threeDayBad && !dismissedToday,
                clickedSchedule = clicked
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun dismissThreeDayAlertToday() {
        viewModelScope.launch { settingsRepo.setThreeDayAlertDismissed(DateUtils.todayStart()) }
    }

    /** 用户点击某个待服时间点 */
    fun onScheduleClick(pending: PendingSchedule) {
        viewModelScope.launch {
            val scheduledMillis = scheduleTimeMillis(pending.schedule.time)
            // 修改点2：BottomSheet 弹出前先校验该时间点今天是否已处理。
            if (medRepo.countRecordedTodayForSchedule(pending.schedule.id, scheduledMillis) > 0 ||
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
            val scheduledMillis = scheduleTimeMillis(pending.schedule.time)
            val records = pending.medications.map { med ->
                MedicationRecord(
                    scheduleId = pending.schedule.id,
                    medicationId = med.id,
                    medicationName = med.name,
                    dosage = med.dosage,
                    unit = med.unit,
                    scheduledTime = scheduledMillis,
                    actualTime = now,
                    status = "taken"
                )
            }
            // 修改点2：数据层再次以时间点粒度防重复，taken/missed 任一已存在都拒绝。
            val inserted = medRepo.insertRecordsIfNotRecorded(
                pending.schedule.id,
                scheduledMillis,
                records
            )
            if (!inserted) {
                _clickedSchedule.value = null
                UiFeedbackBus.show("该时间点药品已标记，不可重复操作")
                return@launch
            }
            // 修改点2：处理完成后取消当前自动漏服任务，并注册下一天提醒。
            MedicationReminderScheduler.cancelAutoMissed(getApplication(), pending.schedule.id)
            MedicationReminderScheduler.scheduleNextDay(getApplication(), pending.schedule.id, pending.schedule.time)
            _clickedSchedule.value = null
            UiFeedbackBus.show("已记录服药")
        }
    }

    /** 标记漏服：与已服用完全一致，同样走时间点级别的防重复逻辑。 */
    fun confirmMissed(pending: PendingSchedule) {
        viewModelScope.launch {
            val scheduledMillis = scheduleTimeMillis(pending.schedule.time)
            val records = pending.medications.map { med ->
                MedicationRecord(
                    scheduleId = pending.schedule.id,
                    medicationId = med.id,
                    medicationName = med.name,
                    dosage = med.dosage,
                    unit = med.unit,
                    scheduledTime = scheduledMillis,
                    actualTime = null,
                    status = "missed"
                )
            }
            val inserted = medRepo.insertRecordsIfNotRecorded(
                pending.schedule.id,
                scheduledMillis,
                records
            )
            if (!inserted) {
                _clickedSchedule.value = null
                UiFeedbackBus.show("该时间点药品已标记，不可重复操作")
                return@launch
            }
            MedicationReminderScheduler.cancelAutoMissed(getApplication(), pending.schedule.id)
            MedicationReminderScheduler.scheduleNextDay(getApplication(), pending.schedule.id, pending.schedule.time)
            _clickedSchedule.value = null
            UiFeedbackBus.show("已标记为漏服")
        }
    }

    /** 将 "HH:mm" 转为今日对应的毫秒时间戳 */
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

    private suspend fun computeThreeDayImbalance(): Boolean {
        for (offset in 0..2) {
            val start = DateUtils.daysAgoStart(offset)
            val end = start + 24L * 3600 * 1000
            val records = intakeRepo.records(start, end).first()
            if (records.isEmpty()) return false
            var intake = 0f; var output = 0f
            for (r in records) {
                if (r.type == "intake") intake += r.amount
                else if (r.type == "output" && r.category != "大便") output += r.amount
            }
            val diff = intake - output
            if (diff <= 1000f && diff >= -1000f) return false
        }
        return true
    }

    private fun buildAlerts(
        intake: Float, output: Float,
        vital: VitalSignsRecord?, yesterdayWeight: Float?
    ): List<String> {
        val list = mutableListOf<String>()
        val diff = intake - output
        if (diff > 1000 || diff < -1000) list += "今日出入量差值超标（${diff.toInt()}ml），请注意"
        if (vital != null) {
            val sys = vital.systolic; val dia = vital.diastolic
            if (sys != null && (sys > 140 || sys < 90)) list += "血压异常，收缩压 $sys mmHg"
            if (dia != null && (dia > 90 || dia < 60)) list += "血压异常，舒张压 $dia mmHg"
            val hr = vital.heartRate
            if (hr != null && (hr > 100 || hr < 60)) list += "心率异常，$hr 次/分"
            val sugar = vital.bloodSugar
            if (sugar != null && (sugar > 11.1f || sugar < 3.9f))
                list += "血糖异常，${"%.1f".format(sugar)} mmol/L"
            val weight = vital.weight
            if (weight != null && yesterdayWeight != null) {
                val d = weight - yesterdayWeight
                if (d >= 2f) list += "24小时内体重增加 ${"%.1f".format(d)} 斤，请就医检查"
                else if (d >= 1f) list += "体重增加较快，请注意"
            }
        }
        return list
    }

    private fun todayMedicationStateFlow(): Flow<Pair<List<PendingSchedule>, Boolean>> {
        val (dayStart, dayEnd) = dayRange()
        return combine(
            medRepo.getEnabledSchedules(),
            medRepo.getRecordsBetween(dayStart, dayEnd)
        ) { schedules, records ->
            val slots = mutableListOf<PendingSchedule>()
            for (schedule in schedules) {
                val meds = medRepo.getMedicationsForScheduleSync(schedule.id)
                if (meds.isEmpty()) continue
                val scheduledMillis = scheduleTimeMillis(schedule.time)
                val slotRecords = records.filter {
                    it.scheduleId == schedule.id && it.scheduledTime == scheduledMillis
                }
                val status = when {
                    slotRecords.any { it.status == "missed" } -> MedicationSlotStatus.MISSED
                    slotRecords.any { it.status == "taken" } -> MedicationSlotStatus.TAKEN
                    else -> MedicationSlotStatus.PENDING
                }
                slots += PendingSchedule(
                    schedule = schedule,
                    medications = meds,
                    status = status
                )
            }
            slots to (slots.isNotEmpty() && slots.all { it.status != MedicationSlotStatus.PENDING })
        }
    }

    private fun dayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }
}
