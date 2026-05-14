package com.rooftop.healthlog.ui.history

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val intakeOutput: StateFlow<List<IntakeOutputRecord>> =
        intakeRepo.all()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val vitals: StateFlow<List<VitalSignsRecord>> =
        vitalRepo.all()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val meds: StateFlow<List<MedicationRecord>> =
        medRepo.allRecords()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedMeds: StateFlow<List<MedicationHistoryItem>> =
        medRepo.allRecords()
            .map(::groupMedicationRecords)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _intakeRange = MutableStateFlow(DateRange.LAST_7)
    val intakeRange: StateFlow<DateRange> = _intakeRange.asStateFlow()

    private val _vitalRange = MutableStateFlow(DateRange.LAST_7)
    val vitalRange: StateFlow<DateRange> = _vitalRange.asStateFlow()

    private val _medRange = MutableStateFlow(DateRange.LAST_7)
    val medRange: StateFlow<DateRange> = _medRange.asStateFlow()

    private val _chartPeriod = MutableStateFlow(ChartPeriod.WEEK)
    val chartPeriod: StateFlow<ChartPeriod> = _chartPeriod.asStateFlow()

    fun setIntakeRange(r: DateRange) { _intakeRange.value = r }
    fun setVitalRange(r: DateRange) { _vitalRange.value = r }
    fun setMedRange(r: DateRange) { _medRange.value = r }
    fun setChartPeriod(p: ChartPeriod) { _chartPeriod.value = p }

    fun deleteIntakeOutput(r: IntakeOutputRecord) {
        viewModelScope.launch {
            intakeRepo.delete(r)
            com.rooftop.healthlog.ui.components.UiFeedbackBus.showAction(
                text = "已删除记录",
                action = "撤销"
            ) {
                // 撤销：重新插入。Room 的 REPLACE 会保留同 id；若 autoGen 生成新 id 也可
                viewModelScope.launch { intakeRepo.insert(r.copy(id = 0)) }
            }
        }
    }

    companion object {
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
                        status = if (sorted.any { it.status == "missed" }) "missed" else "taken",
                        medicationNames = sorted.map { it.medicationName }.distinct(),
                        medicationDetails = sorted.map { record ->
                            val dose = if (record.dosage == record.dosage.toInt().toFloat()) {
                                record.dosage.toInt().toString()
                            } else {
                                "%.1f".format(record.dosage)
                            }
                            "${record.medicationName} ${dose}${record.unit}"
                        }
                    )
                }
        }
    }
}
