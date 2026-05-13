package com.example.heartcare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 服药记录 */
@Entity(tableName = "medication_records")
data class MedicationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val medicationId: Long,
    val medicationName: String,
    val dosage: Float,
    val unit: String,
    val scheduledTime: Long,
    val actualTime: Long?,
    val status: String // "taken" or "missed"
)
