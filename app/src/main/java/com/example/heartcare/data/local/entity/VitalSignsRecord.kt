package com.example.heartcare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 体征记录 */
@Entity(tableName = "vital_signs_records")
data class VitalSignsRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systolic: Int?,
    val diastolic: Int?,
    val heartRate: Int?,
    val weight: Float?, // 斤
    val bloodSugar: Float?, // mmol/L
    val note: String = "",
    val time: Long
)
