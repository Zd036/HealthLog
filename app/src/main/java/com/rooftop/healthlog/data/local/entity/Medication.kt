package com.rooftop.healthlog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 药品信息 */
@Entity(
    tableName = "medications",
    foreignKeys = [ForeignKey(
        entity = MedicationSchedule::class,
        parentColumns = ["id"],
        childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("scheduleId")]
)
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val name: String,
    val dosage: Float, // 单次服用量
    val unit: String,
    val specification: Float, // 规格 mg/片
    val method: String,
    val notes: String = ""
)
