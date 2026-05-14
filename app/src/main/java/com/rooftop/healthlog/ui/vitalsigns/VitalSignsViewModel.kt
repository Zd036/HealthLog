package com.rooftop.healthlog.ui.vitalsigns

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
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

    /**
     * 修改点3：检查空字段名称列表
     * 血糖改为纯可选项，不参与"未填提示"
     */
    fun emptyFieldNames(): List<String> {
        val s = _state.value
        val r = mutableListOf<String>()
        if (s.systolic.isBlank()) r += "收缩压"
        if (s.diastolic.isBlank()) r += "舒张压"
        if (s.heartRate.isBlank()) r += "心率"
        if (s.weight.isBlank()) r += "体重"
        // 修改点3：血糖不再加入未填提示列表（纯可选）
        return r
    }

    /**
     * 是否全部为空（不允许保存）
     * 修改点3：现在只检查 4 个核心字段（收缩压/舒张压/心率/体重），血糖单独判断
     */
    fun isAllEmpty(): Boolean {
        val s = _state.value
        return s.systolic.isBlank() && s.diastolic.isBlank() &&
               s.heartRate.isBlank() && s.weight.isBlank() &&
               s.bloodSugar.isBlank()
    }

    fun save(onDone: (abnormalBloodSugar: Float?) -> Unit) {
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
            val sugar = record.bloodSugar
            val abnormal = if (sugar != null && (sugar > 11.1f || sugar < 3.9f)) sugar else null
            onDone(abnormal)
        }
    }
}
