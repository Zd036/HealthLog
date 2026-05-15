package com.rooftop.healthlog.utils

import java.util.Locale

const val MEDICATION_STATUS_TAKEN = "taken"
const val MEDICATION_STATUS_MISSED = "missed"

fun formatMedicationAmount(amount: Float): String {
    return if (amount == amount.toInt().toFloat()) {
        amount.toInt().toString()
    } else {
        String.format(Locale.CHINA, "%.1f", amount)
    }
}

fun medicationDoseLabel(dosage: Float, unit: String): String =
    "${formatMedicationAmount(dosage)}$unit"

fun medicationSpecificationLabel(specification: Float, unit: String): String? {
    if (specification <= 0f) return null
    return "每${unit}含${formatMedicationAmount(specification)}mg"
}

fun medicationDetailLabel(
    dosage: Float,
    unit: String,
    specification: Float,
    method: String,
): String {
    return buildList {
        add(medicationDoseLabel(dosage, unit))
        medicationSpecificationLabel(specification, unit)?.let(::add)
        method.trim().takeIf { it.isNotEmpty() }?.let(::add)
    }.joinToString(" · ")
}

fun medicationStatusLabel(status: String): String = when (status) {
    MEDICATION_STATUS_TAKEN -> "已服用"
    MEDICATION_STATUS_MISSED -> "漏服"
    else -> status
}

fun parseMedicationStatus(raw: String): String? = when (raw.trim()) {
    MEDICATION_STATUS_TAKEN,
    "已服用",
    "已按时服用",
    "按时服用" -> MEDICATION_STATUS_TAKEN

    MEDICATION_STATUS_MISSED,
    "已漏服",
    "漏服" -> MEDICATION_STATUS_MISSED

    else -> null
}
