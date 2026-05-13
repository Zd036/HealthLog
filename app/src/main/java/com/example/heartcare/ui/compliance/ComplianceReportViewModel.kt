package com.example.heartcare.ui.compliance

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heartcare.HeartCareApp
import com.example.heartcare.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

data class ComplianceExportResult(val path: String?, val error: String?)

class ComplianceReportViewModel(app: HeartCareApp) : AndroidViewModel(app) {

    private val medRepo = app.medicationRepository

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _data = MutableStateFlow<ComplianceReportData?>(null)
    val data: StateFlow<ComplianceReportData?> = _data.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _exportResult = MutableStateFlow<ComplianceExportResult?>(null)
    val exportResult: StateFlow<ComplianceExportResult?> = _exportResult.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            val now = System.currentTimeMillis()
            val schedules = medRepo.getAllSchedules().first()
            val meds = medRepo.getAllMedications().first()
            val records = medRepo.allRecords().first()
            val seven = ComplianceCalculator.build(now, 7, schedules, meds, records)
            val thirty = ComplianceCalculator.build(now, 30, schedules, meds, records)
            _data.value = ComplianceReportData(seven, thirty)
            _loading.value = false
        }
    }

    fun exportCsv(ctx: Context) {
        if (_exporting.value) return
        val current = _data.value ?: return
        _exporting.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val today = DateUtils.formatYmd(System.currentTimeMillis())
                    val dir = File(ctx.getExternalFilesDir(null), "export")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, "服药依从性报告_$today.csv")
                    FileOutputStream(file).use { fos ->
                        fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        OutputStreamWriter(fos, Charset.forName("UTF-8")).use { w ->
                            w.appendLine("统计维度,应服次数,实服次数,依从率")
                            fun row(label: String, s: ComplianceSummary) {
                                val pct = if (s.totalExpected == 0) "--"
                                else "${(s.overallRate * 100).toInt()}%"
                                w.appendLine("$label,${s.totalExpected},${s.totalActual},$pct")
                            }
                            row("最近 7 天", current.sevenDay)
                            row("最近 30 天", current.thirtyDay)
                            w.appendLine()
                            w.appendLine("药品名称,7天应服,7天实服,7天依从率,30天应服,30天实服,30天依从率")
                            val names = (current.sevenDay.perMedication.map { it.medicationName } +
                                current.thirtyDay.perMedication.map { it.medicationName }).toSet()
                            for (name in names) {
                                val s7 = current.sevenDay.perMedication.find { it.medicationName == name }
                                val s30 = current.thirtyDay.perMedication.find { it.medicationName == name }
                                val r7 = s7?.let { "${(it.rate * 100).toInt()}%" } ?: "--"
                                val r30 = s30?.let { "${(it.rate * 100).toInt()}%" } ?: "--"
                                w.appendLine(
                                    listOf(
                                        escape(name),
                                        s7?.expected ?: 0, s7?.actual ?: 0, r7,
                                        s30?.expected ?: 0, s30?.actual ?: 0, r30
                                    ).joinToString(",")
                                )
                            }
                            w.flush()
                        }
                    }
                    file.absolutePath
                }
            }
            _exporting.value = false
            _exportResult.value = if (result.isSuccess)
                ComplianceExportResult(result.getOrNull(), null)
            else ComplianceExportResult(null, result.exceptionOrNull()?.message ?: "未知错误")
        }
    }

    fun consumeExportResult() { _exportResult.value = null }

    private fun escape(raw: String): String {
        val needs = raw.contains(',') || raw.contains('"') || raw.contains('\n')
        return if (needs) "\"${raw.replace("\"", "\"\"")}\"" else raw
    }
}
