package com.example.heartcare.ui.settings

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heartcare.HeartCareApp
import com.example.heartcare.data.local.entity.AppSettings
import com.example.heartcare.data.local.entity.IntakeOutputRecord
import com.example.heartcare.data.local.entity.MedicationRecord
import com.example.heartcare.data.local.entity.VitalSignsRecord
import com.example.heartcare.utils.BackupManager
import com.example.heartcare.utils.CsvExporter
import com.example.heartcare.worker.BackupScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BackupFileInfo(val file: File, val sizeBytes: Long, val dayLabel: String)

class SettingsViewModel(private val app: HeartCareApp) : AndroidViewModel(app) {

    val settings: StateFlow<AppSettings> =
        app.settingsRepository.settings.stateIn(
            viewModelScope, SharingStarted.Eagerly, AppSettings()
        )

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _backingUp = MutableStateFlow(false)
    val backingUp: StateFlow<Boolean> = _backingUp.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    private val _backups = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val backups: StateFlow<List<BackupFileInfo>> = _backups.asStateFlow()

    init {
        refreshBackups()
    }

    fun setFontSize(large: Boolean) {
        viewModelScope.launch {
            app.settingsRepository.setFontSize(if (large) "large" else "normal")
        }
    }

    fun setEnableIntakeOutput(enabled: Boolean) {
        viewModelScope.launch {
            app.settingsRepository.setEnableIntakeOutput(enabled)
        }
    }

    /** 全量导出 CSV */
    fun exportAll() {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val intake: List<IntakeOutputRecord> =
                        app.intakeOutputRepository.all().first()
                    val vitals: List<VitalSignsRecord> =
                        app.vitalSignsRepository.all().first()
                    val meds: List<MedicationRecord> =
                        app.medicationRepository.allRecords().first()
                    val files = CsvExporter.exportAll(app, intake, vitals, meds)
                    val dir = CsvExporter.exportDir(app).absolutePath
                    ExportResult(files.size, dir)
                }
            }
            _exporting.value = false
            _exportResult.value = result.getOrElse {
                ExportResult(0, "", error = it.message ?: "未知错误")
            }
        }
    }

    fun consumeExportResult() { _exportResult.value = null }

    /** 立即手动备份 */
    fun backupNow() {
        if (_backingUp.value) return
        _backingUp.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val f = BackupManager.performBackup(app, forceOverwrite = true)
                    BackupManager.cleanupOld(app, 7)
                    // 重新排程下一次凌晨 2:00 的任务
                    BackupScheduler.scheduleDaily(app)
                    f?.absolutePath ?: ""
                }
            }
            _backingUp.value = false
            _backupResult.value = if (result.isSuccess) BackupResult(true, result.getOrDefault(""))
            else BackupResult(false, "", error = result.exceptionOrNull()?.message ?: "未知错误")
            refreshBackups()
        }
    }

    fun consumeBackupResult() { _backupResult.value = null }

    fun refreshBackups() {
        viewModelScope.launch {
            _backups.value = withContext(Dispatchers.IO) {
                BackupManager.listBackups(app).map { f ->
                    val day = f.name.removePrefix("heartcare_backup_").removeSuffix(".db")
                    BackupFileInfo(f, f.length(), day)
                }
            }
        }
    }
}

data class ExportResult(val fileCount: Int, val dirPath: String, val error: String? = null)
data class BackupResult(val success: Boolean, val filePath: String, val error: String? = null)
