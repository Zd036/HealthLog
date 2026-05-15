package com.rooftop.healthlog.ui.history

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationManagerCompat
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import com.rooftop.healthlog.ui.components.UiFeedbackBus
import com.rooftop.healthlog.utils.DateUtils
import com.rooftop.healthlog.utils.MEDICATION_STATUS_MISSED
import com.rooftop.healthlog.utils.MEDICATION_STATUS_TAKEN
import com.rooftop.healthlog.utils.medicationDoseLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 筛选范围 */
enum class DateRange(val days: Int, val label: String) {
    LAST_7(7, "最近7天"),
    LAST_30(30, "最近30天"),
    ALL(-1, "全部");
}

/** 图表维度 */
enum class ChartPeriod(val days: Int, val label: String) {
    WEEK(7, "周"),
    MONTH(30, "月");
}

class HistoryViewModel(app: HealthLogApp) : AndroidViewModel(app) {

    private val intakeRepo = app.intakeOutputRepository
    private val vitalRepo = app.vitalSignsRepository
    private val medRepo = app.medicationRepository

    private val _intakeRange = MutableStateFlow(DateRange.LAST_7)
    val intakeRange: StateFlow<DateRange> = _intakeRange.asStateFlow()

    private val _vitalRange = MutableStateFlow(DateRange.LAST_7)
    val vitalRange: StateFlow<DateRange> = _vitalRange.asStateFlow()

    private val _medRange = MutableStateFlow(DateRange.LAST_7)
    val medRange: StateFlow<DateRange> = _medRange.asStateFlow()

    private val _chartPeriod = MutableStateFlow(ChartPeriod.WEEK)
    val chartPeriod: StateFlow<ChartPeriod> = _chartPeriod.asStateFlow()

    /** 历史页列表仅加载最近 500 条。 */
    val intakeOutput: StateFlow<List<IntakeOutputRecord>> =
        intakeRepo.getRecentRecordsForDisplay()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 历史页列表仅加载最近 500 条。 */
    val vitals: StateFlow<List<VitalSignsRecord>> =
        vitalRepo.getRecentRecordsForDisplay()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 历史页列表仅加载最近 500 条。 */
    val meds: StateFlow<List<MedicationRecord>> =
        medRepo.getRecentRecordsForDisplay()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 服药列表按最近 500 条原始记录分组。 */
    val groupedMeds: StateFlow<List<MedicationHistoryItem>> =
        meds.map(::groupMedicationRecords)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 总数用于判断是否显示“仅展示最近 500 条”的提示。 */
    val intakeTotalCount: StateFlow<Int> =
        intakeRepo.countAllRecords()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val vitalsTotalCount: StateFlow<Int> =
        vitalRepo.countAllRecords()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val medsTotalCount: StateFlow<Int> =
        medRepo.countAllRecordEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 图表使用的时间范围数据，避免历史列表的 500 条限制影响趋势图。 */
    val intakeChartRecords: StateFlow<List<IntakeOutputRecord>> =
        combine(_intakeRange, _chartPeriod) { range, period ->
            resolveChartRange(range, period)
        }
            .flatMapLatest { (start, end) -> intakeRepo.records(start, end) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vitalChartRecords: StateFlow<List<VitalSignsRecord>> =
        combine(_vitalRange, _chartPeriod) { range, period ->
            resolveChartRange(range, period)
        }
            .flatMapLatest { (start, end) -> vitalRepo.between(start, end) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val medChartRecords: StateFlow<List<MedicationHistoryItem>> =
        combine(_medRange, _chartPeriod) { range, period ->
            resolveChartRange(range, period)
        }
            .flatMapLatest { (start, end) -> medRepo.getRecordsBetween(start, end) }
            .map(::groupMedicationRecords)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setIntakeRange(r: DateRange) { _intakeRange.value = r }
    fun setVitalRange(r: DateRange) { _vitalRange.value = r }
    fun setMedRange(r: DateRange) { _medRange.value = r }
    fun setChartPeriod(p: ChartPeriod) { _chartPeriod.value = p }

    fun deleteIntakeOutput(r: IntakeOutputRecord) {
        viewModelScope.launch {
            if (r.time < DateUtils.todayStart()) {
                UiFeedbackBus.show("今天 0 点前的出入量记录不允许删除")
                return@launch
            }
            intakeRepo.delete(r)
            UiFeedbackBus.showAction(
                text = "已删除记录",
                action = "撤销"
            ) {
                // 撤销时重新插入一条等价记录，避免破坏现有删除/撤销体验。
                viewModelScope.launch { intakeRepo.insert(r.copy(id = 0)) }
            }
        }
    }

    companion object {
        const val HISTORY_DISPLAY_LIMIT = 500

        fun groupMedicationRecords(records: List<MedicationRecord>): List<MedicationHistoryItem> {
            return records
                .groupBy { it.scheduledTime }
                .toSortedMap(compareByDescending { it })
                .values
                .map { sameTimeRecords ->
                    val sorted = sameTimeRecords.sortedBy { it.medicationName }
                    MedicationHistoryItem(
                        scheduledTime = sorted.first().scheduledTime,
                        actualTime = sorted.mapNotNull { it.actualTime }.minOrNull(),
                        status = if (sorted.any { it.status == MEDICATION_STATUS_MISSED }) {
                            MEDICATION_STATUS_MISSED
                        } else {
                            MEDICATION_STATUS_TAKEN
                        },
                        medicationNames = sorted.map { it.medicationName }.distinct(),
                        medicationDetails = sorted.map { record ->
                            "${record.medicationName} ${medicationDoseLabel(record.dosage, record.unit)}"
                        }
                    )
                }
        }
    }
}

/**
 * 图表按时间范围取数：
 * - 若用户选择“全部”，仍允许回退为图表维度的最近 7/30 天，避免趋势图加载全部历史。
 * - 这样可以满足“趋势图按时间范围查询，不需要加载全部数据”的要求。
 */
private fun resolveChartRange(
    range: DateRange,
    chartPeriod: ChartPeriod
): Pair<Long, Long> {
    val effectiveDays = if (range == DateRange.ALL) chartPeriod.days else range.days
    if (effectiveDays < 0) return 0L to Long.MAX_VALUE
    val from = DateUtils.daysAgoStart(effectiveDays - 1)
    return from to System.currentTimeMillis()
}
