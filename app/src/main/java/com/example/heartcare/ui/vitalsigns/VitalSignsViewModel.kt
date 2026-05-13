package com.example.heartcare.ui.vitalsigns

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heartcare.HeartCareApp
import com.example.heartcare.data.local.entity.VitalSignsRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VitalsInputState(
    val systolic: String = "",
    val diastolic: String = "",
    val heartRate: String = "",
    val weight: String = "",
    val bloodSugar: String = "",
    val note: String = "",
    val time: Long = System.currentTimeMillis()
)

class VitalSignsViewModel(app: HeartCareApp) : AndroidViewModel(app) {
    private val repo = app.vitalSignsRepository
    private val _state = MutableStateFlow(VitalsInputState())
    val state: StateFlow<VitalsInputState> = _state

    fun setSys(v: String) = _state.update { it.copy(systolic = v.filter { c -> c.isDigit() }) }
    fun setDia(v: String) = _state.update { it.copy(diastolic = v.filter { c -> c.isDigit() }) }
    fun setHr(v: String) = _state.update { it.copy(heartRate = v.filter { c -> c.isDigit() }) }
    fun setWeight(v: String) = _state.update { it.copy(weight = v.filter { c -> c.isDigit() || c == '.' }) }
    fun setSugar(v: String) = _state.update { it.copy(bloodSugar = v.filter { c -> c.isDigit() || c == '.' }) }
    fun setNote(v: String) = _state.update { it.copy(note = v) }
    fun setTime(t: Long) = _state.update { it.copy(time = t) }

    /** 检查空字段名称列表 */
    fun emptyFieldNames(): List<String> {
        val s = _state.value
        val r = mutableListOf<String>()
        if (s.systolic.isBlank()) r += "收缩压"
        if (s.diastolic.isBlank()) r += "舒张压"
        if (s.heartRate.isBlank()) r += "心率"
        if (s.weight.isBlank()) r += "体重"
        if (s.bloodSugar.isBlank()) r += "血糖"
        return r
    }

    /** 是否全部为空（不允许保存） */
    fun isAllEmpty(): Boolean = emptyFieldNames().size == 5

    fun save(onDone: (abnormalBloodSugar: Float?) -> Unit) {
        val s = _state.value
        val record = VitalSignsRecord(
            systolic = s.systolic.toIntOrNull(),
            diastolic = s.diastolic.toIntOrNull(),
            heartRate = s.heartRate.toIntOrNull(),
            weight = s.weight.toFloatOrNull(),
            bloodSugar = s.bloodSugar.toFloatOrNull(),
            note = s.note,
            time = s.time
        )
        viewModelScope.launch {
            repo.insert(record)
            val sugar = record.bloodSugar
            val abnormal = if (sugar != null && (sugar > 11.1f || sugar < 3.9f)) sugar else null
            onDone(abnormal)
        }
    }
}
