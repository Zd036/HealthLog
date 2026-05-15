package com.rooftop.healthlog.utils

import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import java.util.Locale

private const val ONE_DAY_MS = 24L * 3600_000L

enum class WeightTrend { UP, DOWN, FLAT }

data class WeightTrendInfo(
    val trend: WeightTrend,
    val diff: Float,
    val warningMessage: String?
)

data class VitalLine(
    val text: String,
    val abnormal: Boolean = false
)

enum class RecentVitalKind {
    BLOOD_PRESSURE,
    HEART_RATE,
    WEIGHT,
    BLOOD_SUGAR
}

data class RecentVitalItem(
    val kind: RecentVitalKind,
    val timeText: String,
    val nameText: String,
    val valueText: String,
    val deltaText: String? = null,
    val abnormal: Boolean,
    val time: Long
)

enum class VitalAlertKind {
    SYSTOLIC,
    DIASTOLIC,
    HEART_RATE,
    BLOOD_SUGAR,
    WEIGHT_GAIN
}

data class VitalAlert(
    val kind: VitalAlertKind,
    val message: String
)

private data class DailyVitalAlertAggregate(
    val count: Int,
    val latestTime: Long,
    val latestValue: String
)

fun isSystolicAbnormal(value: Int): Boolean = value > 140 || value < 90

fun isDiastolicAbnormal(value: Int): Boolean = value > 90 || value < 60

fun isBloodPressureAbnormal(systolic: Int?, diastolic: Int?): Boolean =
    (systolic?.let(::isSystolicAbnormal) == true) ||
        (diastolic?.let(::isDiastolicAbnormal) == true)

fun isHeartRateAbnormal(value: Int?): Boolean =
    value?.let { it > 100 || it < 60 } == true

fun isBloodSugarAbnormal(value: Float?): Boolean =
    value?.let { it > 11.1f || it < 3.9f } == true

fun weightGainDelta(currentWeight: Float?, previousWeight: Float?): Float? =
    if (currentWeight == null || previousWeight == null) null else currentWeight - previousWeight

fun isRapidWeightGain(delta: Float?): Boolean = delta != null && delta >= 1f

fun rapidWeightGainMessage(delta: Float): String =
    "24小时内体重增加 ${"%.1f".format(Locale.CHINA, delta)} 斤，提示可能存在水潴留，请及时就医检查"

fun computeWeightTrend(currentWeight: Float?, previousWeight: Float?): WeightTrendInfo? {
    if (currentWeight == null) return null
    if (previousWeight == null) return WeightTrendInfo(WeightTrend.FLAT, 0f, null)
    val diff = weightGainDelta(currentWeight, previousWeight) ?: 0f
    val absDiff = kotlin.math.abs(diff)
    val trend = when {
        absDiff < 0.1f -> WeightTrend.FLAT
        diff > 0f -> WeightTrend.UP
        else -> WeightTrend.DOWN
    }
    val warningMessage = when {
        isRapidWeightGain(diff) -> rapidWeightGainMessage(diff)
        else -> null
    }
    return WeightTrendInfo(trend, diff, warningMessage)
}

fun buildVitalLines(latestVital: VitalSignsRecord?, previousWeight: Float?): List<VitalLine> {
    if (latestVital == null) return listOf(VitalLine("今日暂无体征记录"))

    val lines = mutableListOf<VitalLine>()
    if (latestVital.systolic != null && latestVital.diastolic != null) {
        val abnormal = isBloodPressureAbnormal(latestVital.systolic, latestVital.diastolic)
        lines += VitalLine(
            text = "高压/低压 ${latestVital.systolic}/${latestVital.diastolic} mmHg",
            abnormal = abnormal
        )
    }
    latestVital.heartRate?.let {
        lines += VitalLine(
            text = "脉率 $it 次/分",
            abnormal = isHeartRateAbnormal(it)
        )
    }
    latestVital.weight?.let { weight ->
        lines += VitalLine(
            text = "体重 ${"%.1f".format(Locale.CHINA, weight)} 斤",
            abnormal = isRapidWeightGain(weightGainDelta(weight, previousWeight))
        )
    }
    latestVital.bloodSugar?.let {
        lines += VitalLine(
            text = "血糖 ${"%.1f".format(Locale.CHINA, it)} mmol/L",
            abnormal = isBloodSugarAbnormal(it)
        )
    }
    return lines
}

fun buildRecentVitalItems(
    todayVitals: List<VitalSignsRecord>,
    weightWindowVitals: List<VitalSignsRecord>
): List<RecentVitalItem> {
    if (todayVitals.isEmpty()) return emptyList()
    val latestFirst = todayVitals.sortedByDescending { it.time }
    val items = mutableListOf<RecentVitalItem>()

    latestFirst.firstOrNull { it.weight != null }?.let { record ->
        val previousWeight = previousWeightRecordWithin24Hours(weightWindowVitals, record.time)?.weight
        val trend = computeWeightTrend(record.weight, previousWeight)
        val diffText = trend?.takeIf { previousWeight != null }?.let {
            val sign = if (it.diff > 0f) "+" else ""
            "$sign${"%.1f".format(Locale.CHINA, it.diff)} 斤"
        }
        items += RecentVitalItem(
            kind = RecentVitalKind.WEIGHT,
            timeText = DateUtils.formatHm(record.time),
            nameText = "体重",
            valueText = "${"%.1f".format(Locale.CHINA, record.weight)} 斤",
            deltaText = diffText,
            abnormal = isRapidWeightGain(weightGainDelta(record.weight, previousWeight)),
            time = record.time
        )
    }

    latestFirst.firstOrNull { it.systolic != null && it.diastolic != null }?.let { record ->
        items += RecentVitalItem(
            kind = RecentVitalKind.BLOOD_PRESSURE,
            timeText = DateUtils.formatHm(record.time),
            nameText = "血压",
            valueText = "${record.systolic}/${record.diastolic} mmHg",
            abnormal = isBloodPressureAbnormal(record.systolic, record.diastolic),
            time = record.time
        )
    }

    latestFirst.firstOrNull { it.heartRate != null }?.let { record ->
        items += RecentVitalItem(
            kind = RecentVitalKind.HEART_RATE,
            timeText = DateUtils.formatHm(record.time),
            nameText = "脉率",
            valueText = "${record.heartRate} 次/分",
            abnormal = isHeartRateAbnormal(record.heartRate),
            time = record.time
        )
    }

    latestFirst.firstOrNull { it.bloodSugar != null }?.let { record ->
        items += RecentVitalItem(
            kind = RecentVitalKind.BLOOD_SUGAR,
            timeText = DateUtils.formatHm(record.time),
            nameText = "血糖",
            valueText = "${"%.1f".format(Locale.CHINA, record.bloodSugar)} mmol/L",
            abnormal = isBloodSugarAbnormal(record.bloodSugar),
            time = record.time
        )
    }

    return items
}

fun buildVitalAlerts(vital: VitalSignsRecord?, previousWeight: Float?): List<String> {
    return buildVitalAlertDetails(vital, previousWeight).map { it.message }
}

fun buildVitalAlertDetails(vital: VitalSignsRecord?, previousWeight: Float?): List<VitalAlert> {
    if (vital == null) return emptyList()
    val alerts = mutableListOf<VitalAlert>()
    val sys = vital.systolic
    val dia = vital.diastolic
    if (sys != null && isSystolicAbnormal(sys)) {
        alerts += VitalAlert(VitalAlertKind.SYSTOLIC, "血压异常，高压 $sys mmHg")
    }
    if (dia != null && isDiastolicAbnormal(dia)) {
        alerts += VitalAlert(VitalAlertKind.DIASTOLIC, "血压异常，低压 $dia mmHg")
    }
    val hr = vital.heartRate
    if (isHeartRateAbnormal(hr)) {
        alerts += VitalAlert(VitalAlertKind.HEART_RATE, "脉率异常，$hr 次/分")
    }
    val sugar = vital.bloodSugar
    if (isBloodSugarAbnormal(sugar)) {
        alerts += VitalAlert(
            VitalAlertKind.BLOOD_SUGAR,
            "血糖异常，${"%.1f".format(Locale.CHINA, sugar)} mmol/L"
        )
    }
    val weight = vital.weight
    val trend = computeWeightTrend(weight, previousWeight)
    if (trend?.warningMessage != null) {
        alerts += VitalAlert(VitalAlertKind.WEIGHT_GAIN, trend.warningMessage)
    }
    return alerts
}

fun buildDailyVitalAlertMessages(alertWindowVitals: List<VitalSignsRecord>): List<String> {
    if (alertWindowVitals.isEmpty()) return emptyList()
    val ordered = alertWindowVitals.sortedBy { it.time }
    val todayStart = DateUtils.todayStart()
    val map = linkedMapOf<VitalAlertKind, DailyVitalAlertAggregate>()

    fun upsert(kind: VitalAlertKind, time: Long, value: String) {
        val current = map[kind]
        map[kind] = if (current == null) {
            DailyVitalAlertAggregate(1, time, value)
        } else {
            DailyVitalAlertAggregate(current.count + 1, time, value)
        }
    }

    for (record in ordered) {
        if (record.time < todayStart) continue
        record.systolic?.takeIf(::isSystolicAbnormal)?.let {
            upsert(VitalAlertKind.SYSTOLIC, record.time, "$it mmHg")
        }
        record.diastolic?.takeIf(::isDiastolicAbnormal)?.let {
            upsert(VitalAlertKind.DIASTOLIC, record.time, "$it mmHg")
        }
        record.heartRate?.takeIf { isHeartRateAbnormal(it) }?.let {
            upsert(VitalAlertKind.HEART_RATE, record.time, "$it 次/分")
        }
        record.bloodSugar?.takeIf { isBloodSugarAbnormal(it) }?.let {
            upsert(VitalAlertKind.BLOOD_SUGAR, record.time, "${"%.1f".format(Locale.CHINA, it)} mmol/L")
        }
        record.weight?.let { weight ->
            val previousWeight = previousWeightRecordWithin24Hours(ordered, record.time)?.weight
            val delta = weightGainDelta(weight, previousWeight)
            if (isRapidWeightGain(delta)) {
                upsert(
                    VitalAlertKind.WEIGHT_GAIN,
                    record.time,
                    "较24小时前增加 ${"%.1f".format(Locale.CHINA, delta)} 斤（当前 ${"%.1f".format(Locale.CHINA, weight)} 斤）"
                )
            }
        }
    }

    val order = listOf(
        VitalAlertKind.SYSTOLIC,
        VitalAlertKind.DIASTOLIC,
        VitalAlertKind.HEART_RATE,
        VitalAlertKind.BLOOD_SUGAR,
        VitalAlertKind.WEIGHT_GAIN
    )
    return order.mapNotNull { kind ->
        val item = map[kind] ?: return@mapNotNull null
        val timeText = DateUtils.formatHm(item.latestTime)
        when (kind) {
            VitalAlertKind.SYSTOLIC ->
                "高压今日异常 ${item.count} 次，最近一次异常值 为 ${item.latestValue}（$timeText）"
            VitalAlertKind.DIASTOLIC ->
                "低压今日异常 ${item.count} 次，最近一次异常值 为 ${item.latestValue}（$timeText）"
            VitalAlertKind.HEART_RATE ->
                "脉率今日异常 ${item.count} 次，最近一次异常值 为 ${item.latestValue}（$timeText）"
            VitalAlertKind.BLOOD_SUGAR ->
                "血糖今日异常 ${item.count} 次，最近一次异常值 为 ${item.latestValue}（$timeText）"
            VitalAlertKind.WEIGHT_GAIN ->
                "体重今日异常 ${item.count} 次，最近一次异常值 为 ${item.latestValue}（$timeText）"
        }
    }
}

fun previousWeightRecordWithin24Hours(
    records: List<VitalSignsRecord>,
    currentTime: Long
): VitalSignsRecord? = records.asSequence()
    .filter { it.weight != null && it.time < currentTime && it.time >= currentTime - ONE_DAY_MS }
    .maxByOrNull { it.time }
