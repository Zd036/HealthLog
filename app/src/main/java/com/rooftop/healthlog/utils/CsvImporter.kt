package com.rooftop.healthlog.utils

import android.content.Context
import android.net.Uri
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.data.local.entity.Medication
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.data.local.entity.MedicationSchedule
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 修改点1：CSV 导入工具。
 *
 * 解析与 [CsvExporter] 对称的单文件多分区格式，
 * 将记录合并到 Room 数据库；同一时间点 + 同一类型/字段视为重复并跳过。
 */
object CsvImporter {

    data class ImportResult(
        val intakeAdded: Int = 0,
        val vitalsAdded: Int = 0,
        val medRecordsAdded: Int = 0,
        val medSettingsAdded: Int = 0,
        val skippedDuplicates: Int = 0,
        val skippedInvalid: Int = 0,
        val error: String? = null,
    ) {
        val isEmpty: Boolean
            get() = intakeAdded + vitalsAdded + medRecordsAdded + medSettingsAdded == 0
    }

    private val ymdHm = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    /** 主入口：从 SAF 选中的 Uri 解析 CSV 并合并到数据库 */
    suspend fun importFromUri(context: Context, uri: Uri): ImportResult {
        val app = context.applicationContext as HealthLogApp
        val parsed: ParsedCsv = try {
            parseCsv(context, uri)
        } catch (t: Throwable) {
            return ImportResult(error = "文件读取失败：${t.message ?: "未知错误"}")
        }

        if (parsed.allEmpty && parsed.invalidCount == 0) {
            return ImportResult(error = "文件内容为空")
        }

        // 加载现有数据用于去重
        val existingIntake = app.intakeOutputRepository.all().first()
        val existingVitals = app.vitalSignsRepository.all().first()
        val existingMedRecords = app.medicationRepository.allRecords().first()
        val existingSchedules = app.medicationRepository.getAllSchedules().first()
        val existingMeds = app.medicationRepository.getAllMedications().first()

        var intakeAdded = 0
        var vitalsAdded = 0
        var medAdded = 0
        var setAdded = 0
        var dups = 0

        // ---- 出入量 ----
        val intakeKeySet = existingIntake.map { intakeKey(it) }.toMutableSet()
        for (r in parsed.intake) {
            val k = intakeKey(r)
            if (k in intakeKeySet) { dups++; continue }
            app.intakeOutputRepository.insert(r)
            intakeKeySet += k
            intakeAdded++
        }

        // ---- 体征 ----
        val vitalKeySet = existingVitals.map { vitalKey(it) }.toMutableSet()
        for (v in parsed.vitals) {
            val k = vitalKey(v)
            if (k in vitalKeySet) { dups++; continue }
            app.vitalSignsRepository.insert(v)
            vitalKeySet += k
            vitalsAdded++
        }

        // ---- 用药设置（先导入，建立 schedule/medication，后用于服药记录的 medicationId 映射） ----
        // 现有：time -> scheduleId
        val scheduleByTime = existingSchedules.associateBy { it.time }.toMutableMap()
        // 现有：(scheduleId,name) -> medication
        val medByKey = existingMeds.associateBy { it.scheduleId to it.name }.toMutableMap()

        for (s in parsed.settings) {
            val sch = scheduleByTime[s.time] ?: run {
                val id = app.medicationRepository.insertSchedule(
                    MedicationSchedule(time = s.time, enabled = true)
                )
                val newSch = MedicationSchedule(id = id, time = s.time, enabled = true)
                scheduleByTime[s.time] = newSch
                newSch
            }
            val key = sch.id to s.name
            if (key in medByKey) { dups++; continue }
            val medId = app.medicationRepository.insertMedication(
                Medication(
                    scheduleId = sch.id,
                    name = s.name,
                    dosage = s.dosage,
                    unit = s.unit,
                    specification = s.specification,
                    method = s.method
                )
            )
            medByKey[key] = Medication(
                id = medId, scheduleId = sch.id, name = s.name, dosage = s.dosage,
                unit = s.unit, specification = s.specification, method = s.method
            )
            setAdded++
        }

        // ---- 服药记录 ----
        // 已存在记录的去重 key：scheduledTime + name + status
        val existRecKeys = existingMedRecords.map { medRecKey(it) }.toMutableSet()
        for (raw in parsed.medRecords) {
            val sch = scheduleByTime[raw.plannedTimeStr]
            for (detail in raw.medications) {
                // 尝试匹配 medication（按时间点 + 名称），匹配不到则用 0
                val med = sch?.let { medByKey[it.id to detail.name] }
                val record = MedicationRecord(
                    scheduleId = sch?.id ?: 0L,
                    medicationId = med?.id ?: 0L,
                    medicationName = detail.name,
                    dosage = detail.dosage,
                    unit = detail.unit,
                    scheduledTime = raw.scheduledTimeMs,
                    actualTime = raw.actualTimeMs,
                    status = if (raw.statusZh == "已服用") "taken" else "missed"
                )
                val k = medRecKey(record)
                if (k in existRecKeys) { dups++; continue }
                app.medicationRepository.insertRecord(record)
                existRecKeys += k
                medAdded++
            }
        }

        return ImportResult(
            intakeAdded = intakeAdded,
            vitalsAdded = vitalsAdded,
            medRecordsAdded = medAdded,
            medSettingsAdded = setAdded,
            skippedDuplicates = dups,
            skippedInvalid = parsed.invalidCount,
        )
    }

    private fun intakeKey(r: IntakeOutputRecord) =
        listOf(r.time, r.type, r.category, r.amount.toString())
    private fun vitalKey(v: VitalSignsRecord) =
        listOf(v.time, v.systolic, v.diastolic, v.heartRate, v.weight, v.bloodSugar)
    private fun medRecKey(r: MedicationRecord) =
        listOf(r.scheduledTime, r.medicationName, r.status, r.dosage)

    // ===== 解析 =====

    private data class ParsedCsv(
        val intake: List<IntakeOutputRecord>,
        val vitals: List<VitalSignsRecord>,
        val medRecords: List<RawMedRecord>,
        val settings: List<RawSetting>,
        val invalidCount: Int,
    ) {
        val allEmpty get() = intake.isEmpty() && vitals.isEmpty() && medRecords.isEmpty() && settings.isEmpty()
    }

    private data class RawMedRecord(
        val scheduledTimeMs: Long,
        val actualTimeMs: Long?,
        val plannedTimeStr: String,
        val medications: List<MedicationDetail>,
        val statusZh: String,
    )

    private data class MedicationDetail(
        val name: String,
        val dosage: Float,
        val unit: String,
    )

    private data class RawSetting(
        val time: String,
        val name: String,
        val dosage: Float,
        val unit: String,
        val specification: Float,
        val method: String,
    )

    private fun parseCsv(context: Context, uri: Uri): ParsedCsv {
        val intake = mutableListOf<IntakeOutputRecord>()
        val vitals = mutableListOf<VitalSignsRecord>()
        val medRecs = mutableListOf<RawMedRecord>()
        val settings = mutableListOf<RawSetting>()
        var invalid = 0

        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开文件")
        stream.use { input ->
            BufferedReader(InputStreamReader(input, Charset.forName("UTF-8"))).useLines { seq ->
                var section = ""
                var headerSkipped = false
                for (rawLine in seq) {
                    val line = stripBom(rawLine).trimEnd('\r')
                    when {
                        line.startsWith("===== 出入量记录") -> { section = "intake"; headerSkipped = false }
                        line.startsWith("===== 体征记录") -> { section = "vitals"; headerSkipped = false }
                        line.startsWith("===== 服药记录") -> { section = "medrec"; headerSkipped = false }
                        line.startsWith("===== 用药设置") -> { section = "settings"; headerSkipped = false }
                        line.isBlank() -> { /* 跳过空行 */ }
                        else -> {
                            if (!headerSkipped) { headerSkipped = true; continue }
                            val cols = parseCsvLine(line)
                            try {
                                when (section) {
                                    "intake" -> {
                                        val r = parseIntake(cols)
                                        if (r != null) intake += r else invalid++
                                    }
                                    "vitals" -> {
                                        val r = parseVitals(cols)
                                        if (r != null) vitals += r else invalid++
                                    }
                                    "medrec" -> {
                                        val r = parseMedRec(cols)
                                        if (r != null) medRecs += r else invalid++
                                    }
                                    "settings" -> {
                                        val r = parseSetting(cols)
                                        if (r != null) settings += r else invalid++
                                    }
                                }
                            } catch (_: Throwable) {
                                invalid++
                            }
                        }
                    }
                }
            }
        }
        return ParsedCsv(intake, vitals, medRecs, settings, invalid)
    }

    private fun stripBom(line: String): String =
        if (line.isNotEmpty() && line[0] == '\uFEFF') line.substring(1) else line

    /** 解析一行 CSV，支持引号转义 */
    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0; var inQuote = false
        while (i < line.length) {
            val c = line[i]
            when {
                inQuote -> {
                    if (c == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ }
                        else inQuote = false
                    } else sb.append(c)
                }
                c == ',' -> { out += sb.toString(); sb.setLength(0) }
                c == '"' -> inQuote = true
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private fun parseIntake(c: List<String>): IntakeOutputRecord? {
        if (c.size < 5) return null
        val date = c[0].trim(); val time = c[1].trim()
        val typeZh = c[2].trim(); val category = c[3].trim()
        val amount = c[4].trim().toFloatOrNull() ?: return null
        val ts = parseTimestamp(date, time) ?: return null
        val type = if (typeZh == "摄入") "intake" else if (typeZh == "排出") "output" else return null
        val note = c.getOrNull(7)?.trim().orEmpty()
        return IntakeOutputRecord(
            type = type, category = category, amount = amount, time = ts, note = note
        )
    }

    private fun parseVitals(c: List<String>): VitalSignsRecord? {
        if (c.size < 7) return null
        val ts = parseTimestamp(c[0].trim(), c[1].trim()) ?: return null
        val sys = c[2].trim().toIntOrNull()
        val dia = c[3].trim().toIntOrNull()
        val hr = c[4].trim().toIntOrNull()
        val wt = c[5].trim().toFloatOrNull()
        val bs = c[6].trim().toFloatOrNull()
        // 合理性校验
        if (sys != null && (sys < 0 || sys > 300)) return null
        if (dia != null && (dia < 0 || dia > 300)) return null
        if (hr != null && (hr < 0 || hr > 300)) return null
        if (sys == null && dia == null && hr == null && wt == null && bs == null) return null
        val note = c.getOrNull(7)?.trim().orEmpty()
        return VitalSignsRecord(
            systolic = sys, diastolic = dia, heartRate = hr,
            weight = wt, bloodSugar = bs, note = note, time = ts
        )
    }

    private fun parseMedRec(c: List<String>): RawMedRecord? {
        if (c.size >= 9) {
            // 兼容旧版明细导出：一行一个药品。
            val date = c[0].trim()
            val planned = c[1].trim()
            val actual = c[2].trim()
            val name = c[3].trim()
            val dose = c[4].trim().toFloatOrNull() ?: return null
            val unit = c[5].trim()
            val statusZh = c[8].trim()
            if (name.isEmpty()) return null
            val plannedTs = parseTimestamp(date, planned) ?: return null
            val actualTs = if (actual.isEmpty()) null else parseTimestamp(date, actual)
            return RawMedRecord(
                scheduledTimeMs = plannedTs,
                actualTimeMs = actualTs,
                plannedTimeStr = planned,
                medications = listOf(MedicationDetail(name, dose, unit)),
                statusZh = statusZh
            )
        }
        if (c.size < 4) return null
        // 修改点3：兼容新版聚合导出：一行一个时间点，药品清单中带剂量信息。
        val date = c[0].trim()
        val planned = c[1].trim()
        val statusZh = c[2].trim()
        val medicationList = parseMedicationDetails(c[3].trim())
        if (medicationList.isEmpty()) return null
        val plannedTs = parseTimestamp(date, planned) ?: return null
        return RawMedRecord(
            scheduledTimeMs = plannedTs,
            actualTimeMs = null,
            plannedTimeStr = planned,
            medications = medicationList,
            statusZh = statusZh
        )
    }

    private fun parseSetting(c: List<String>): RawSetting? {
        // 修改点1：导入时兼容旧版"剩余数量/购药提醒"列，但直接忽略这两列。
        if (c.size < 6) return null
        val time = c[0].trim()
        val name = c[1].trim()
        val dose = c[2].trim().toFloatOrNull() ?: return null
        val unit = c[3].trim()
        val specStr = c[4].trim()
        val spec = Regex("([\\d.]+)").find(specStr)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val method = c[5].trim()
        if (name.isEmpty() || time.isEmpty()) return null
        return RawSetting(time, name, dose, unit, spec, method)
    }

    private fun parseTimestamp(date: String, time: String): Long? {
        return try {
            ymdHm.parse("$date $time")?.time
        } catch (_: Throwable) {
            // 仅日期
            try { ymd.parse(date)?.time } catch (_: Throwable) { null }
        }
    }

    private fun parseMedicationDetails(raw: String): List<MedicationDetail> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',')
            .mapNotNull { part ->
                val text = part.trim()
                if (text.isEmpty()) return@mapNotNull null
                val match = Regex("^(.+?)\\s+([\\d.]+)([^\\d\\s]+)$").find(text)
                if (match != null) {
                    MedicationDetail(
                        name = match.groupValues[1].trim(),
                        dosage = match.groupValues[2].toFloatOrNull() ?: 0f,
                        unit = match.groupValues[3].trim()
                    )
                } else {
                    MedicationDetail(
                        name = text,
                        dosage = 0f,
                        unit = ""
                    )
                }
            }
    }
}
