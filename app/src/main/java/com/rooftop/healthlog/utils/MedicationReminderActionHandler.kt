package com.rooftop.healthlog.utils

import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.MedicationRecord

/**
 * 用药提醒动作处理：
 * - 通知按钮与提醒详情页都复用同一套写库逻辑
 * - 仍以 scheduleId + scheduledAt 做幂等控制，避免重复标记
 */
object MedicationReminderActionHandler {

    suspend fun markTaken(
        app: HealthLogApp,
        scheduleId: Long,
        scheduledAt: Long
    ): Boolean {
        val meds = app.medicationRepository.getMedicationsForScheduleSync(scheduleId)
        if (meds.isEmpty()) return false
        val now = System.currentTimeMillis()
        val records = meds.map { med ->
            MedicationRecord(
                scheduleId = scheduleId,
                medicationId = med.id,
                medicationName = med.name,
                dosage = med.dosage,
                unit = med.unit,
                scheduledTime = scheduledAt,
                actualTime = now,
                status = MEDICATION_STATUS_TAKEN
            )
        }
        return app.medicationRepository.insertRecordsIfNotRecorded(
            scheduleId = scheduleId,
            scheduledTime = scheduledAt,
            records = records
        )
    }

    suspend fun markMissed(
        app: HealthLogApp,
        scheduleId: Long,
        scheduledAt: Long
    ): Boolean {
        val meds = app.medicationRepository.getMedicationsForScheduleSync(scheduleId)
        if (meds.isEmpty()) return false
        val records = meds.map { med ->
            MedicationRecord(
                scheduleId = scheduleId,
                medicationId = med.id,
                medicationName = med.name,
                dosage = med.dosage,
                unit = med.unit,
                scheduledTime = scheduledAt,
                actualTime = null,
                status = MEDICATION_STATUS_MISSED
            )
        }
        return app.medicationRepository.insertRecordsIfNotRecorded(
            scheduleId = scheduleId,
            scheduledTime = scheduledAt,
            records = records
        )
    }
}
