package com.rooftop.healthlog.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.data.local.entity.Medication
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.data.local.entity.MedicationSchedule
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import com.rooftop.healthlog.ui.history.HistoryViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV 导出工具：
 * - 导出位置：/storage/emulated/Download/healthlog/健康记录_yyyy-MM-dd_HH-mm-ss.csv
 * - 单文件多分区（===== xxx =====），UTF-8 BOM
 *
 * 修改点1：导出全量数据；与 CsvImporter 配套实现导入/导出对称。
 */
object CsvExporter {

    private const val DIR_REL = "Download/healthlog"
    private const val SUBDIR = "healthlog"
    private val nameFmt = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.CHINA)

    data class ExportData(
        val intake: List<IntakeOutputRecord>,
        val vitals: List<VitalSignsRecord>,
        val medRecords: List<MedicationRecord>,
        val schedules: List<MedicationSchedule>,
        val medications: List<Medication>,
    )

    /** 导出全量数据，成功返回展示路径（如 "/Download/healthlog/xxx.csv"），失败返回 null */
    fun exportAll(context: Context, data: ExportData): String? {
        val fileName = "健康记录_${nameFmt.format(Date())}.csv"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(context, fileName, data)
        } else {
            exportViaLegacyFile(fileName, data)
        }
    }

    private fun exportViaMediaStore(context: Context, fileName: String, data: ExportData): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, DIR_REL)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { os -> writeCsvTo(os, data) }
            "/$DIR_REL/$fileName"
        } catch (t: Throwable) {
            try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
            null
        }
    }

    private fun exportViaLegacyFile(fileName: String, data: ExportData): String? {
        return try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, SUBDIR)
            if (!dir.exists() && !dir.mkdirs()) return null
            val file = File(dir, fileName)
            FileOutputStream(file).use { writeCsvTo(it, data) }
            "/$DIR_REL/$fileName"
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeCsvTo(os: OutputStream, data: ExportData) {
        // UTF-8 BOM 让 Excel 不乱码
        os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        OutputStreamWriter(os, Charset.forName("UTF-8")).use { w ->
            // 出入量
            w.appendLine("===== 出入量记录 =====")
            w.appendLine("日期,时间,类型,类别,数值(毫升/次),含水量(%),原始克数,备注")
            for (r in data.intake) {
                val date = DateUtils.formatYmd(r.time)
                val time = DateUtils.formatHm(r.time)
                val type = if (r.type == "intake") "摄入" else "排出"
                val value = if (r.amount == r.amount.toInt().toFloat())
                    r.amount.toInt().toString() else r.amount.toString()
                w.appendLine("$date,$time,$type,${esc(r.category)},$value,,,${esc(r.note)}")
            }
            w.appendLine()

            // 体征
            w.appendLine("===== 体征记录 =====")
            w.appendLine("日期,时间,收缩压(mmHg),舒张压(mmHg),心率(次/分),体重(斤),血糖(mmol/L),备注")
            for (v in data.vitals) {
                val date = DateUtils.formatYmd(v.time)
                val time = DateUtils.formatHm(v.time)
                val sys = v.systolic?.toString() ?: ""
                val dia = v.diastolic?.toString() ?: ""
                val hr = v.heartRate?.toString() ?: ""
                val wt = v.weight?.let { "%.1f".format(it) } ?: ""
                val bs = v.bloodSugar?.let { "%.1f".format(it) } ?: ""
                w.appendLine("$date,$time,$sys,$dia,$hr,$wt,$bs,${esc(v.note)}")
            }
            w.appendLine()

            // 服药记录
            w.appendLine("===== 服药记录 =====")
            w.appendLine("日期,时间,状态,药品清单")
            for (item in HistoryViewModel.groupMedicationRecords(data.medRecords)) {
                val date = DateUtils.formatYmd(item.scheduledTime)
                val planned = DateUtils.formatHm(item.scheduledTime)
                val status = if (item.status == "taken") "已服用" else "漏服"
                w.appendLine("$date,$planned,$status,${esc(item.medicationDetails.joinToString(","))}")
            }
            w.appendLine()

            // 用药设置
            w.appendLine("===== 用药设置 =====")
            w.appendLine("时间点,药品名称,单次剂量,单位,规格,服用方式")
            val schById = data.schedules.associateBy { it.id }
            for (m in data.medications) {
                val s = schById[m.scheduleId] ?: continue
                val dose = if (m.dosage == m.dosage.toInt().toFloat())
                    m.dosage.toInt().toString() else "%.1f".format(m.dosage)
                val spec = if (m.specification > 0) "${"%.0f".format(m.specification)}mg" else ""
                w.appendLine(
                    "${s.time},${esc(m.name)},$dose,${esc(m.unit)},${esc(spec)}," +
                        esc(m.method)
                )
            }
            w.flush()
        }
    }

    /** 转义 CSV 字段（含逗号/引号/换行加引号） */
    fun esc(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val needs = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
        return if (needs) "\"${raw.replace("\"", "\"\"")}\"" else raw
    }
}
