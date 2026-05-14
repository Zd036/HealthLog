package com.rooftop.healthlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用药时间点 */
@Entity(tableName = "medication_schedules")
data class MedicationSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: String, // "07:00"
    val enabled: Boolean = true
)
