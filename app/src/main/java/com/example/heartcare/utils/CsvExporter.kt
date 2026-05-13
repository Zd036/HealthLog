package com.example.heartcare.utils

import android.content.Context
import com.example.heartcare.data.local.entity.IntakeOutputRecord
import com.example.heartcare.data.local.entity.MedicationRecord
import com.example.heartcare.data.local.entity.VitalSignsRecord
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

/** CSV 导出工具：UTF-8 with BOM，逗号分隔，表头中文。 */
object CsvExporter {

    /** 导出目录 /Android/data/[pkg]/files/export/ */
    fun exportDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "export")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 导出三份 CSV，返回生成的文件列表 */
    fun exportAll(
        context: Context,
        intakeOutputs: List<IntakeOutputRecord>,
        vitals: List<VitalSignsRecord>,
        medications: List<MedicationRecord>
    ): List<File> {
        val today = DateUtils.formatYmd(System.currentTimeMillis())
        val dir = exportDir(context)
        val files = mutableListOf<File>()
        files += writeIntakeOutput(dir, today, intakeOutputs)
        files += writeVitals(dir, today, vitals)
        files += writeMedications(dir, today, medications)
        return files
    }

    private fun writeIntakeOutput(dir: File, dayStr: String, list: List<IntakeOutputRecord>): File {
        val file = File(dir, "心衰记录_出入量_$dayStr.csv")
        writeCsv(file) { w ->
            w.appendLine("日期,时间,类型,类别,数值,备注")
            for (r in list) {
                val date = DateUtils.formatYmd(r.time)
                val time = DateUtils.formatHm(r.time)
                val type = if (r.type == "intake") "摄入" else "排出"
                val value = r.amount.toInt().toString()
                val note = escape(r.note)
                w.appendLine("$date,$time,$type,${escape(r.category)},$value,$note")
            }
        }
        return file
    }

    private fun writeVitals(dir: File, dayStr: String, list: List<VitalSignsRecord>): File {
        val file = File(dir, "心衰记录_体征_$dayStr.csv")
        writeCsv(file) { w ->
            w.appendLine("日期,时间,收缩压,舒张压,心率,体重,血糖,备注")
            for (v in list) {
                val date = DateUtils.formatYmd(v.time)
                val time = DateUtils.formatHm(v.time)
                val sys = v.systolic?.toString() ?: ""
                val dia = v.diastolic?.toString() ?: ""
                val hr = v.heartRate?.toString() ?: ""
                val wt = v.weight?.let { "%.1f".format(it) } ?: ""
                val bs = v.bloodSugar?.let { "%.1f".format(it) } ?: ""
                val note = escape(v.note)
                w.appendLine("$date,$time,$sys,$dia,$hr,$wt,$bs,$note")
            }
        }
        return file
    }

    private fun writeMedications(dir: File, dayStr: String, list: List<MedicationRecord>): File {
        val file = File(dir, "心衰记录_服药_$dayStr.csv")
        writeCsv(file) { w ->
            w.appendLine("日期,计划时间,实际时间,药品名称,剂量,单位,状态")
            for (r in list) {
                val date = DateUtils.formatYmd(r.scheduledTime)
                val planned = DateUtils.formatHm(r.scheduledTime)
                val actual = r.actualTime?.let { DateUtils.formatHm(it) } ?: ""
                val name = escape(r.medicationName)
                val dose = r.dosage.toString()
                val unit = escape(r.unit)
                val status = if (r.status == "taken") "按时" else "漏服"
                w.appendLine("$date,$planned,$actual,$name,$dose,$unit,$status")
            }
        }
        return file
    }

    private fun writeCsv(file: File, block: (Appendable) -> Unit) {
        FileOutputStream(file).use { fos ->
            // UTF-8 BOM，确保 Excel 打开不乱码
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(fos, Charset.forName("UTF-8")).use { w ->
                block(w)
                w.flush()
            }
        }
    }

    /** 转义 CSV 字段（包含逗号/引号/换行时加引号） */
    private fun escape(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val needs = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
        return if (needs) "\"${raw.replace("\"", "\"\"")}\"" else raw
    }
}
