package com.rooftop.healthlog.ui.vitalsigns

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import com.rooftop.healthlog.utils.buildVitalAlertDetails
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

class VitalSignsViewModel(app: HealthLogApp) : AndroidViewModel(app) {
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

    /**
     * 修改点5：清空所有输入框，每次进入"新增模式"时调用，避免带入上次数据
     * 修改点2：同时重置 time 为当前真实时间（如不传 time 默认在保存时取当前时间）
     */
    fun clearInputFields() {
        _state.value = VitalsInputState(time = System.currentTimeMillis())
    }

    /**
     * 修改点5：编辑已有记录时使用，回填数据
     */
    fun loadRecord(record: VitalSignsRecord) {
        _state.value = VitalsInputState(
            systolic = record.systolic?.toString() ?: "",
            diastolic = record.diastolic?.toString() ?: "",
            heartRate = record.heartRate?.toString() ?: "",
            weight = record.weight?.toString() ?: "",
            bloodSugar = record.bloodSugar?.toString() ?: "",
            note = record.note,
            time = record.time
        )
    }

    fun isAllEmpty(): Boolean {
        val s = _state.value
        return s.systolic.isBlank() && s.diastolic.isBlank() &&
               s.heartRate.isBlank() && s.weight.isBlank() &&
               s.bloodSugar.isBlank()
    }

    fun bloodPressurePairMessage(): String? {
        val s = _state.value
        val hasSys = s.systolic.isNotBlank()
        val hasDia = s.diastolic.isNotBlank()
        return when {
            hasSys && !hasDia -> "已填写高压，低压也需要同时填写。"
            !hasSys && hasDia -> "已填写低压，高压也需要同时填写。"
            else -> null
        }
    }

    fun save(onDone: (List<String>) -> Unit) {
        val s = _state.value
        // 修改点2：保存时使用"保存瞬间"的真实时间，而非页面打开时间
        val now = System.currentTimeMillis()
        val record = VitalSignsRecord(
            systolic = s.systolic.toIntOrNull(),
            diastolic = s.diastolic.toIntOrNull(),
            heartRate = s.heartRate.toIntOrNull(),
            weight = s.weight.toFloatOrNull(),
            bloodSugar = s.bloodSugar.toFloatOrNull(),
            note = s.note,
            time = now
        )
        viewModelScope.launch {
            repo.insert(record)
            onDone(buildVitalAlertDetails(record, null).map { it.message })
        }
    }
}
