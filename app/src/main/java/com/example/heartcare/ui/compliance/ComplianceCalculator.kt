package com.example.heartcare.ui.compliance

import com.example.heartcare.data.local.entity.Medication
import com.example.heartcare.data.local.entity.MedicationRecord
import com.example.heartcare.data.local.entity.MedicationSchedule
import com.example.heartcare.utils.DateUtils

/** 单药依从性统计（按药品名汇总，即使该药已被删除亦计入历史） */
data class MedicationCompliance(
    val medicationName: String,
    val expected: Int,
    val actual: Int
) {
    val rate: Float get() = if (expected <= 0) 0f else actual.toFloat() / expected
}

data class ComplianceSummary(
    val days: Int,
    val totalExpected: Int,
    val totalActual: Int,
    val perMedication: List<MedicationCompliance>
) {
    val overallRate: Float get() = if (totalExpected <= 0) 0f else totalActual.toFloat() / totalExpected
}

data class ComplianceReportData(
    val sevenDay: ComplianceSummary,
    val thirtyDay: ComplianceSummary
)

object ComplianceCalculator {

    /**
     * 计算最近 days 天（含今日）的依从性汇总。
     * 规则：
     * - 某药品的"起始日期" = min(该名称最早一条 MedicationRecord.scheduledTime, medication 当前存在则视为创建日 = 现在)
     *   即从用户首次记录该药品的日子起统计
     * - 每天应服次数 = 所有启用的 MedicationSchedule 数
     *   （删除的 schedule 若历史记录存在则这部分记录仍计入 actual）
     *   因为 Room 模式里 schedule 仅保留启用定时点，我们以"已存在记录+启用 schedule"两者并集来估算
     * - 实服 = status == "taken"
     */
    fun build(
        now: Long,
        days: Int,
        schedules: List<MedicationSchedule>,
        medications: List<Medication>,
        records: List<MedicationRecord>
    ): ComplianceSummary {
        val rangeStart = DateUtils.daysAgoStart(days - 1)
        val rangeEnd = now

        // 按药品名称分组
        val byName: Map<String, List<MedicationRecord>> =
            records.filter { it.scheduledTime in rangeStart..rangeEnd }
                   .groupBy { it.medicationName }

        // 收集所有"名称"：当前 medication 列表 + 历史记录中出现过的
        val allNames = buildSet {
            addAll(medications.map { it.name })
            addAll(records.map { it.medicationName })
        }.filter { it.isNotBlank() }

        val enabledSchedulesPerDay = schedules.count { it.enabled }
            .coerceAtLeast(1) // 避免 0
        val perMed = mutableListOf<MedicationCompliance>()

        for (name in allNames) {
            // 起始日：该药首次出现的 day start
            val firstSeen = records.filter { it.medicationName == name }
                .minOfOrNull { it.scheduledTime }
                ?: continue
            val effectiveStart = maxOf(rangeStart, DateUtils.dayStartOf(firstSeen))
            val effectiveDays =
                ((rangeEnd - effectiveStart) / (24L * 3600_000L)).toInt() + 1
            if (effectiveDays <= 0) continue
            val expected = effectiveDays * enabledSchedulesPerDay
            val actual = byName[name]?.count { it.status == "taken" } ?: 0
            perMed += MedicationCompliance(name, expected, actual)
        }

        val totalExpected = perMed.sumOf { it.expected }
        val totalActual = perMed.sumOf { it.actual }
        return ComplianceSummary(
            days = days,
            totalExpected = totalExpected,
            totalActual = totalActual,
            perMedication = perMed.sortedByDescending { it.rate }
        )
    }
}
