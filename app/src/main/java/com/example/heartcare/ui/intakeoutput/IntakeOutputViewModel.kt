package com.example.heartcare.ui.intakeoutput

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heartcare.HeartCareApp
import com.example.heartcare.data.local.entity.CustomCategory
import com.example.heartcare.data.local.entity.IntakeOutputRecord
import com.example.heartcare.data.repository.IntakeOutputRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IOInputState(
    val isIntake: Boolean = true,
    val category: String = "饮用水",
    val amountText: String = "",
    val time: Long = System.currentTimeMillis(),
    val note: String = "",
    val customCategories: List<CustomCategory> = emptyList()
)

class IntakeOutputViewModel(app: HeartCareApp) : AndroidViewModel(app) {

    private val repo: IntakeOutputRepository = app.intakeOutputRepository

    private val _state = MutableStateFlow(IOInputState())
    val state: StateFlow<IOInputState> = _state

    init {
        // 加载自定义类型
        viewModelScope.launch {
            combine(repo.getCategories("intake"), repo.getCategories("output")) { i, o ->
                i + o
            }.collect { list ->
                _state.update { it.copy(customCategories = list) }
            }
        }
    }

    fun setIntake(intake: Boolean) {
        _state.update {
            it.copy(
                isIntake = intake,
                category = if (intake) "饮用水" else "尿液",
                amountText = ""
            )
        }
    }

    fun setCategory(c: String) = _state.update { it.copy(category = c, amountText = "") }
    fun setAmount(s: String) = _state.update { it.copy(amountText = s.filter { ch -> ch.isDigit() || ch == '.' }) }
    fun setTime(t: Long) = _state.update { it.copy(time = t) }
    fun setNote(n: String) = _state.update { it.copy(note = n) }

    /** 类型可用列表（含内置 + 自定义） */
    fun categoryList(intake: Boolean): List<String> {
        val builtin = if (intake) repo.intakeCategories else repo.outputCategories
        val customs = _state.value.customCategories
            .filter { it.type == (if (intake) "intake" else "output") }
            .map { it.name }
        return builtin + customs
    }

    fun isLiquid(category: String): Boolean = repo.liquidCategories.contains(category)
    fun isSolid(category: String): Boolean = repo.solidCategories.contains(category)
    fun isStool(category: String): Boolean = category == "大便"

    /** 含水量 (%) */
    fun waterPercent(category: String): Float? {
        repo.builtinWaterContent[category]?.let { return it }
        return _state.value.customCategories.firstOrNull { it.name == category }?.waterPercent
    }

    /** 计算用于保存的"实际 ml 数"（或大便次数） */
    fun computeAmount(): Float? {
        val s = _state.value
        val raw = s.amountText.toFloatOrNull() ?: return null
        if (raw <= 0) return null
        return when {
            isStool(s.category) -> raw // 次数
            isLiquid(s.category) -> raw
            isSolid(s.category) -> {
                val pct = waterPercent(s.category) ?: 80f
                raw * pct / 100f // 克数 × 含水量
            }
            else -> raw
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val amount = computeAmount() ?: return
        val rec = IntakeOutputRecord(
            type = if (s.isIntake) "intake" else "output",
            category = s.category,
            amount = amount,
            time = s.time,
            note = s.note
        )
        viewModelScope.launch {
            repo.insert(rec)
            onDone()
        }
    }

    fun addCustomCategory(name: String, waterPct: Float, intake: Boolean) {
        viewModelScope.launch {
            repo.addCategory(
                CustomCategory(
                    type = if (intake) "intake" else "output",
                    name = name,
                    waterPercent = waterPct.coerceIn(0f, 100f)
                )
            )
        }
    }
}
