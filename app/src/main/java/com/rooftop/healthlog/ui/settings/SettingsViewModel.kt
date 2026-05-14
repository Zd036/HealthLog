package com.rooftop.healthlog.ui.settings

import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.AppSettings
import com.rooftop.healthlog.utils.CsvExporter
import com.rooftop.healthlog.utils.CsvImporter
import com.rooftop.healthlog.worker.MedicationReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: HealthLogApp) : AndroidViewModel(app) {

    val settings: StateFlow<AppSettings> =
        app.settingsRepository.settings.stateIn(
            viewModelScope, SharingStarted.Eagerly, AppSettings()
        )

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    private val _importResult = MutableStateFlow<CsvImporter.ImportResult?>(null)
    val importResult: StateFlow<CsvImporter.ImportResult?> = _importResult.asStateFlow()

    fun setFontSize(large: Boolean) {
        viewModelScope.launch {
            app.settingsRepository.setFontSize(if (large) "large" else "normal")
        }
    }

    fun setEnableIntakeOutput(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setEnableIntakeOutput(enabled) }
    }

    /** 修改点1：全量导出到 Download/healthlog/ */
    fun exportAll() {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val data = CsvExporter.ExportData(
                        intake = app.intakeOutputRepository.all().first(),
                        vitals = app.vitalSignsRepository.all().first(),
                        medRecords = app.medicationRepository.allRecords().first(),
                        schedules = app.medicationRepository.getAllSchedules().first(),
                        medications = app.medicationRepository.getAllMedications().first(),
                    )
                    CsvExporter.exportAll(app, data)
                        ?: throw IllegalStateException("写入文件失败")
                }
            }
            _exporting.value = false
            _exportResult.value = result.fold(
                onSuccess = { ExportResult(displayPath = it) },
                onFailure = { ExportResult(displayPath = "", error = it.message ?: "未知错误") }
            )
        }
    }

    fun consumeExportResult() { _exportResult.value = null }

    /** 修改点1：从用户选中的 SAF Uri 导入 CSV */
    fun importFrom(uri: Uri) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                CsvImporter.importFromUri(app, uri)
            }
            _importing.value = false
            _importResult.value = r
            // 导入后可能新增了用药时间点 → 重新注册闹钟
            if (r.error == null && r.medSettingsAdded > 0) {
                MedicationReminderScheduler.rescheduleAll(app)
            }
        }
    }

    fun consumeImportResult() { _importResult.value = null }
}

data class ExportResult(val displayPath: String, val error: String? = null)
