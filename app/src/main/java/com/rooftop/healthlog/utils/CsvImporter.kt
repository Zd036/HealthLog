package com.rooftop.healthlog.utils

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.data.local.entity.Medication
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.data.local.entity.MedicationSchedule
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import com.rooftop.healthlog.data.local.entity.CustomCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * CSV 导入工具。
 *
 * 修改点2：
 * - 导入前按业务字段检查重复记录；
 * - 整个导入过程使用数据库事务包裹，保证统计与落库一致；
 * - 导入结果返回“成功导入 / 跳过重复 / 格式错误”统计。
 */
object CsvImporter {

    private data class IntakeDuplicateKey(
        val timestamp: String,
        val type: String,
        val category: String,
        val amount: String,
    )

    private data class VitalDuplicateKey(
        val timestamp: String,
        val systolic: Int?,
        val diastolic: Int?,
        val heartRate: Int?,
        val weight: String?,
        val bloodSugar: String?,
    )

    private data class MedicationIdentityKey(
        val scheduleId: Long,
        val name: String,
        val dosage: String,
        val unit: String,
    )

    private data class MedicationSettingKey(
        val time: String,
        val name: String,
        val dosage: String,
        val unit: String,
        val specification: String,
        val method: String,
    )

    private data class CustomCategoryKey(
        val type: String,
        val name: String,
    )

    data class ImportResult(
        val intakeAdded: Int = 0,
        val vitalsAdded: Int = 0,
        val medRecordsAdded: Int = 0,
        val medSettingsAdded: Int = 0,
        val customCategoriesAdded: Int = 0,
        val importedCount: Int = 0,
        val skippedDuplicates: Int = 0,
        val skippedInvalid: Int = 0,
        val error: String? = null,
    ) {
        val isEmpty: Boolean
            get() = importedCount == 0
    }

    private val ymdHm = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    /** 主入口：从 SAF 选中的 Uri 解析 CSV 并合并到数据库。 */
    suspend fun importFromUri(context: Context, uri: Uri): ImportResult {
        val app = context.applicationContext as HealthLogApp
        val parsed = try {
            parseCsv(context, uri)
        } catch (t: Throwable) {
            return ImportResult(error = "文件读取失败：${t.message ?: "未知错误"}")
        }

        if (parsed.allEmpty && parsed.invalidCount == 0) {
            return ImportResult(error = "文件内容为空")
        }

        return try {
            app.database.withTransaction {
                importParsedCsv(app, parsed)
            }
        } catch (t: Throwable) {
            ImportResult(error = "导入失败：${t.message ?: "未知错误"}")
        }
    }

    private suspend fun importParsedCsv(
        app: HealthLogApp,
        parsed: ParsedCsv
    ): ImportResult {
        var intakeAdded = 0
        var vitalsAdded = 0
        var medAdded = 0
        var settingAdded = 0
        var customCategoryAdded = 0
        var duplicates = 0
        val existingIntakeKeys = app.intakeOutputRepository.getAllRecordsForExport()
            .first()
            .mapTo(mutableSetOf(), ::intakeDuplicateKey)
        val existingVitalKeys = app.vitalSignsRepository.getAllRecordsForExport()
            .first()
            .mapTo(mutableSetOf(), ::vitalDuplicateKey)
        val existingCustomCategoryKeys = app.intakeOutputRepository.getAllCategories()
            .first()
            .mapTo(mutableSetOf(), ::customCategoryKey)

        // ---- 出入量记录：按导出后的“日期时间(分钟) + 类型 + 类别 + 数值”去重 ----
        for (record in parsed.intake) {
            if (existingIntakeKeys.add(intakeDuplicateKey(record))) {
                app.intakeOutputRepository.insert(record)
                intakeAdded++
            } else {
                duplicates++
            }
        }

        // ---- 体征记录：按导出后的“日期时间(分钟) + 六项数值字段”去重 ----
        for (record in parsed.vitals) {
            if (existingVitalKeys.add(vitalDuplicateKey(record))) {
                app.vitalSignsRepository.insert(record)
                vitalsAdded++
            } else {
                duplicates++
            }
        }

        // ---- 自定义出入量类型：按“类型 + 名称”去重 ----
        for (category in parsed.customCategories) {
            if (existingCustomCategoryKeys.add(customCategoryKey(category))) {
                app.intakeOutputRepository.addCategory(category)
                customCategoryAdded++
            } else {
                duplicates++
            }
        }

        // ---- 用药设置：先补 schedule / medication，供后续服药记录映射 medicationId ----
        val scheduleByTime = app.medicationRepository.getAllSchedules().firstMapByTime()
        val medicationByKey = app.medicationRepository.getAllMedications().firstMapByIdentity()
        val settingKeys = scheduleByTime.values
            .associateBy({ it.id }, { it.time })
            .let { scheduleTimeById ->
                app.medicationRepository.getAllMedications().first()
                    .mapNotNullTo(mutableSetOf()) { medication ->
                        val time = scheduleTimeById[medication.scheduleId] ?: return@mapNotNullTo null
                        medicationSettingKey(time, medication)
                    }
            }
        val existingMedicationRecordKeys = app.medicationRepository.getAllRecordsForExport()
            .first()
            .mapTo(mutableSetOf()) { medicationRecordKey(it) }

        for (raw in parsed.settings) {
            val schedule = scheduleByTime[raw.time] ?: run {
                val scheduleId = app.medicationRepository.insertSchedule(
                    MedicationSchedule(time = raw.time, enabled = true)
                )
                MedicationSchedule(id = scheduleId, time = raw.time, enabled = true).also {
                    scheduleByTime[raw.time] = it
                }
            }

            val settingKey = medicationSettingKey(raw)
            if (!settingKeys.add(settingKey)) {
                duplicates++
                continue
            }

            val medicationId = app.medicationRepository.insertMedication(
                Medication(
                    scheduleId = schedule.id,
                    name = raw.name,
                    dosage = raw.dosage,
                    unit = raw.unit,
                    specification = raw.specification,
                    method = raw.method
                )
            )
            medicationByKey[medicationIdentityKey(
                scheduleId = schedule.id,
                name = raw.name,
                dosage = raw.dosage,
                unit = raw.unit
            )] = Medication(
                id = medicationId,
                scheduleId = schedule.id,
                name = raw.name,
                dosage = raw.dosage,
                unit = raw.unit,
                specification = raw.specification,
                method = raw.method
            )
            settingAdded++
        }

        // ---- 服药记录：按 scheduleId + scheduledTime + medicationId + status 去重 ----
        for (raw in parsed.medRecords) {
            val schedule = scheduleByTime[raw.plannedTimeStr]
            val normalizedStatus = parseMedicationStatus(raw.statusZh) ?: MEDICATION_STATUS_MISSED

            for (detail in raw.medications) {
                val medication = schedule?.let {
                    medicationByKey[medicationIdentityKey(
                        scheduleId = it.id,
                        name = detail.name,
                        dosage = detail.dosage,
                        unit = detail.unit
                    )]
                }
                val record = MedicationRecord(
                    scheduleId = schedule?.id ?: 0L,
                    scheduledTime = raw.scheduledTimeMs,
                    medicationId = medication?.id ?: 0L,
                    medicationName = detail.name,
                    dosage = detail.dosage,
                    unit = detail.unit,
                    actualTime = raw.actualTimeMs,
                    status = normalizedStatus
                )

                // 中文注释：优先按本次要求的 4 字段规则判重；若 medicationId 无法映射，仍兼容旧导入格式。
                val isDuplicate = if (record.medicationId != 0L && record.scheduleId != 0L) {
                    app.medicationRepository.countDuplicateRecord(record) > 0
                } else {
                    medicationRecordKey(record) in existingMedicationRecordKeys
                }

                if (isDuplicate) {
                    duplicates++
                    continue
                }

                runCatching { app.medicationRepository.insertRecord(record) }
                    .onSuccess {
                        medAdded++
                        existingMedicationRecordKeys += medicationRecordKey(record)
                    }
                    .onFailure { duplicates++ }
            }
        }

        val importedCount = intakeAdded + vitalsAdded + medAdded + settingAdded + customCategoryAdded
        return ImportResult(
            intakeAdded = intakeAdded,
            vitalsAdded = vitalsAdded,
            medRecordsAdded = medAdded,
            medSettingsAdded = settingAdded,
            customCategoriesAdded = customCategoryAdded,
            importedCount = importedCount,
            skippedDuplicates = duplicates,
            skippedInvalid = parsed.invalidCount,
        )
    }

    private fun medicationRecordKey(record: MedicationRecord): List<Any> {
        // 中文注释：当旧 CSV 无法映射出 medicationId / scheduleId 时，
        // 回退使用药名和剂量参与判重，避免同一时间点不同药品被误判为重复。
        return if (record.scheduleId != 0L && record.medicationId != 0L) {
            listOf(
                record.scheduleId,
                DateUtils.formatYmdHm(record.scheduledTime),
                record.medicationId,
                record.status
            )
        } else {
            listOf(
                record.scheduleId,
                DateUtils.formatYmdHm(record.scheduledTime),
                record.medicationName.trim(),
                formatMedicationAmount(record.dosage),
                record.unit.trim(),
                record.status
            )
        }
    }

    private suspend fun Flow<List<MedicationSchedule>>.firstMapByTime():
        MutableMap<String, MedicationSchedule> = first().associateBy { it.time }.toMutableMap()

    private suspend fun Flow<List<Medication>>.firstMapByIdentity():
        MutableMap<MedicationIdentityKey, Medication> =
        first().associateBy {
            medicationIdentityKey(
                scheduleId = it.scheduleId,
                name = it.name,
                dosage = it.dosage,
                unit = it.unit
            )
        }.toMutableMap()

    // ===== 解析 =====

    private data class ParsedCsv(
        val intake: List<IntakeOutputRecord>,
        val vitals: List<VitalSignsRecord>,
        val medRecords: List<RawMedRecord>,
        val settings: List<RawSetting>,
        val customCategories: List<CustomCategory>,
        val invalidCount: Int,
    ) {
        val allEmpty: Boolean
            get() = intake.isEmpty() &&
                vitals.isEmpty() &&
                medRecords.isEmpty() &&
                settings.isEmpty() &&
                customCategories.isEmpty()
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
        val customCategories = mutableListOf<CustomCategory>()
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
                        line.startsWith("===== 自定义出入量类型") -> { section = "customCategories"; headerSkipped = false }
                        line.startsWith("===== 体征记录") -> { section = "vitals"; headerSkipped = false }
                        line.startsWith("===== 服药记录") -> { section = "medrec"; headerSkipped = false }
                        line.startsWith("===== 用药设置") -> { section = "settings"; headerSkipped = false }
                        line.isBlank() -> Unit
                        else -> {
                            if (!headerSkipped) {
                                headerSkipped = true
                                continue
                            }
                            val cols = parseCsvLine(line)
                            try {
                                when (section) {
                                    "intake" -> parseIntake(cols)?.let(intake::add) ?: run { invalid++ }
                                    "customCategories" -> parseCustomCategory(cols)?.let(customCategories::add) ?: run { invalid++ }
                                    "vitals" -> parseVitals(cols)?.let(vitals::add) ?: run { invalid++ }
                                    "medrec" -> parseMedRec(cols)?.let(medRecs::add) ?: run { invalid++ }
                                    "settings" -> parseSetting(cols)?.let(settings::add) ?: run { invalid++ }
                                    else -> invalid++
                                }
                            } catch (_: Throwable) {
                                invalid++
                            }
                        }
                    }
                }
            }
        }
        return ParsedCsv(intake, vitals, medRecs, settings, customCategories, invalid)
    }

    private fun stripBom(line: String): String =
        if (line.isNotEmpty() && line[0] == '\uFEFF') line.substring(1) else line

    /** 解析一行 CSV，支持引号转义。 */
    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        var inQuote = false
        while (i < line.length) {
            val c = line[i]
            when {
                inQuote -> {
                    if (c == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            sb.append('"')
                            i++
                        } else {
                            inQuote = false
                        }
                    } else {
                        sb.append(c)
                    }
                }
                c == ',' -> {
                    out += sb.toString()
                    sb.setLength(0)
                }
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
        val date = c[0].trim()
        val time = c[1].trim()
        val typeZh = c[2].trim()
        val category = c[3].trim()
        val amount = c[4].trim().toFloatOrNull() ?: return null
        val ts = parseTimestamp(date, time) ?: return null
        val type = when (typeZh) {
            "摄入" -> "intake"
            "排出" -> "output"
            else -> return null
        }
        val note = c.getOrNull(7)?.trim().orEmpty()
        return IntakeOutputRecord(
            type = type,
            category = category,
            amount = amount,
            time = ts,
            note = note
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
        if (sys != null && (sys < 0 || sys > 300)) return null
        if (dia != null && (dia < 0 || dia > 300)) return null
        if (hr != null && (hr < 0 || hr > 300)) return null
        if (sys == null && dia == null && hr == null && wt == null && bs == null) return null
        val note = c.getOrNull(7)?.trim().orEmpty()
        return VitalSignsRecord(
            systolic = sys,
            diastolic = dia,
            heartRate = hr,
            weight = wt,
            bloodSugar = bs,
            note = note,
            time = ts
        )
    }

    private fun parseCustomCategory(c: List<String>): CustomCategory? {
        if (c.size < 3) return null
        val type = when (c[0].trim()) {
            "摄入" -> "intake"
            "排出" -> "output"
            else -> return null
        }
        val name = c[1].trim()
        if (name.isBlank()) return null
        val rawWaterPercent = c[2].trim()
        val waterPercent = if (type == "output") {
            rawWaterPercent.toFloatOrNull() ?: 0f
        } else {
            rawWaterPercent.toFloatOrNull() ?: return null
        }
        return CustomCategory(
            type = type,
            name = name,
            waterPercent = waterPercent.coerceIn(0f, 100f)
        )
    }

    private fun parseMedRec(c: List<String>): RawMedRecord? {
        if (c.size >= 9) {
            val date = c[0].trim()
            val planned = c[1].trim()
            val actual = c[2].trim()
            val name = c[3].trim()
            val dose = c[4].trim().toFloatOrNull() ?: return null
            val unit = c[5].trim()
            val statusZh = c[8].trim()
            parseMedicationStatus(statusZh) ?: return null
            if (name.isEmpty()) return null
            val plannedTs = parseTimestamp(date, planned) ?: return null
            val actualTs = if (actual.isBlank()) null else parseTimestamp(date, actual)
            return RawMedRecord(
                scheduledTimeMs = plannedTs,
                actualTimeMs = actualTs,
                plannedTimeStr = planned,
                medications = listOf(MedicationDetail(name, dose, unit)),
                statusZh = statusZh
            )
        }
        if (c.size < 4) return null
        val date = c[0].trim()
        val planned = c[1].trim()
        val statusZh = c[2].trim()
        parseMedicationStatus(statusZh) ?: return null
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
        if (c.size < 6) return null
        val time = c[0].trim()
        val name = c[1].trim()
        val dose = c[2].trim().toFloatOrNull() ?: return null
        val unit = c[3].trim()
        val spec = Regex("([\\d.]+)").find(c[4].trim())?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val method = c[5].trim()
        if (time.isBlank() || name.isBlank()) return null
        return RawSetting(
            time = time,
            name = name,
            dosage = dose,
            unit = unit,
            specification = spec,
            method = method
        )
    }

    private fun parseTimestamp(date: String, time: String): Long? {
        return try {
            ymdHm.parse("$date $time")?.time
        } catch (_: Throwable) {
            try {
                ymd.parse(date)?.time
            } catch (_: Throwable) {
                null
            }
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
                    MedicationDetail(name = text, dosage = 0f, unit = "")
                }
            }
    }

    private fun intakeDuplicateKey(record: IntakeOutputRecord): IntakeDuplicateKey =
        IntakeDuplicateKey(
            timestamp = DateUtils.formatYmdHm(record.time),
            type = record.type,
            category = record.category.trim(),
            amount = formatIntakeAmount(record.amount)
        )

    private fun vitalDuplicateKey(record: VitalSignsRecord): VitalDuplicateKey =
        VitalDuplicateKey(
            timestamp = DateUtils.formatYmdHm(record.time),
            systolic = record.systolic,
            diastolic = record.diastolic,
            heartRate = record.heartRate,
            weight = record.weight?.let(::formatSingleDecimal),
            bloodSugar = record.bloodSugar?.let(::formatSingleDecimal)
        )

    private fun medicationIdentityKey(
        scheduleId: Long,
        name: String,
        dosage: Float,
        unit: String,
    ): MedicationIdentityKey = MedicationIdentityKey(
        scheduleId = scheduleId,
        name = name.trim(),
        dosage = formatMedicationAmount(dosage),
        unit = unit.trim()
    )

    private fun medicationSettingKey(raw: RawSetting): MedicationSettingKey =
        MedicationSettingKey(
            time = raw.time.trim(),
            name = raw.name.trim(),
            dosage = formatMedicationAmount(raw.dosage),
            unit = raw.unit.trim(),
            specification = formatSpecification(raw.specification),
            method = raw.method.trim()
        )

    private fun medicationSettingKey(time: String, medication: Medication): MedicationSettingKey =
        MedicationSettingKey(
            time = time.trim(),
            name = medication.name.trim(),
            dosage = formatMedicationAmount(medication.dosage),
            unit = medication.unit.trim(),
            specification = formatSpecification(medication.specification),
            method = medication.method.trim()
        )

    private fun formatIntakeAmount(amount: Float): String =
        if (amount == amount.toInt().toFloat()) amount.toInt().toString() else amount.toString()

    private fun formatSingleDecimal(value: Float): String =
        String.format(Locale.CHINA, "%.1f", value)

    private fun formatSpecification(value: Float): String =
        if (value > 0f) String.format(Locale.CHINA, "%.0f", value) else ""

    private fun customCategoryKey(category: CustomCategory): CustomCategoryKey =
        CustomCategoryKey(
            type = category.type.trim(),
            name = category.name.trim()
        )
}
