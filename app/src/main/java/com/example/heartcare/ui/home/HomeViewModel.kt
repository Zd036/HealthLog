package com.example.heartcare.ui.home

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heartcare.HeartCareApp
import com.example.heartcare.data.local.entity.IntakeOutputRecord
import com.example.heartcare.data.local.entity.Medication
import com.example.heartcare.data.local.entity.MedicationRecord
import com.example.heartcare.data.local.entity.MedicationSchedule
import com.example.heartcare.data.local.entity.VitalSignsRecord
import com.example.heartcare.ui.components.UiFeedbackBus
import com.example.heartcare.utils.DateUtils
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
    val todayPendingSchedules: List<PendingSchedule> = emptyList(),
    val medicationDone: Boolean = false,
    val alerts: List<String> = emptyList(),
    val showThreeDayImbalanceAlert: Boolean = false,
    /** 用户点击的待服时间点，非 null 时弹出确认 BottomSheet */
    val clickedSchedule: PendingSchedule? = null,
    /** 需要购药提醒的药品 */
    val refillReminderMed: Medication? = null
)

data class PendingSchedule(
    val schedule: MedicationSchedule,
    val medications: List<Medication>
)

class HomeViewModel(app: HeartCareApp) : AndroidViewModel(app) {

    private val intakeRepo = app.intakeOutputRepository
    private val medRepo = app.medicationRepository
    private val vitalsRepo = app.vitalSignsRepository
    private val settingsRepo = app.settingsRepository

    // 额外的可变状态（不进入 combine，避免重组风暴）
    private val _clickedSchedule = MutableStateFlow<PendingSchedule?>(null)
    private val _refillReminderMed = MutableStateFlow<Medication?>(null)

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
            combine(_clickedSchedule, _refillReminderMed) { c, r -> c to r }
        ) { (records, vital, medPair), (threeDayBad, settings, yWeight), (clicked, refill) ->
            val (pending, done) = medPair
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
                todayPendingSchedules = pending,
                medicationDone = done,
                alerts = alerts,
                showThreeDayImbalanceAlert = threeDayBad && !dismissedToday,
                clickedSchedule = clicked,
                refillReminderMed = refill
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun dismissThreeDayAlertToday() {
        viewModelScope.launch { settingsRepo.setThreeDayAlertDismissed(DateUtils.todayStart()) }
    }

    /** 用户点击某个待服时间点 */
    fun onScheduleClick(pending: PendingSchedule) {
        _clickedSchedule.value = pending
    }

    fun clearClickedSchedule() { _clickedSchedule.value = null }
    fun clearRefillReminder() { _refillReminderMed.value = null }

    /** 确认已服用：写入 taken 记录，扣减库存，检查购药提醒 */
    fun confirmTaken(pending: PendingSchedule) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val scheduledMillis = scheduleTimeMillis(pending.schedule.time)
            for (med in pending.medications) {
                medRepo.insertRecord(
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
                )
                // 扣减库存
                val newQty = (med.remainingQuantity - med.dosage).coerceAtLeast(0f)
                medRepo.updateMedication(med.copy(remainingQuantity = newQty))
                // 检查购药提醒（剩余量 ≤ 3 天用量）
                if (med.enableRefillReminder && newQty <= med.dosage * 3) {
                    _refillReminderMed.value = med.copy(remainingQuantity = newQty)
                }
            }
            _clickedSchedule.value = null
            UiFeedbackBus.show("已记录服药")
        }
    }

    /** 标记漏服 */
    fun confirmMissed(pending: PendingSchedule) {
        viewModelScope.launch {
            val scheduledMillis = scheduleTimeMillis(pending.schedule.time)
            for (med in pending.medications) {
                medRepo.insertRecord(
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
                )
            }
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

    private fun todayMedicationStateFlow(): Flow<Pair<List<PendingSchedule>, Boolean>> = flow {
        val (dayStart, dayEnd) = dayRange()
        medRepo.getEnabledSchedules().collect { schedules ->
            val pending = mutableListOf<PendingSchedule>()
            var anyEnabled = false
            for (s in schedules) {
                anyEnabled = true
                val records = medRepo.getRecordsByScheduleAndDay(s.id, dayStart, dayEnd)
                val taken = records.any { it.status == "taken" }
                if (!taken) {
                    val meds = medRepo.getMedicationsForScheduleSync(s.id)
                    if (meds.isNotEmpty()) pending += PendingSchedule(s, meds)
                }
            }
            emit(pending to (anyEnabled && pending.isEmpty()))
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
