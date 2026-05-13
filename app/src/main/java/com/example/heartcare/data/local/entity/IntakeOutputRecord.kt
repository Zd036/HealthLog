package com.example.heartcare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 出入量记录 */
@Entity(tableName = "intake_output_records")
data class IntakeOutputRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "intake" or "output"
    val category: String, // "饮用水"、"尿液"、"大便"
    val amount: Float, // 毫升数或次数
    val time: Long,
    val note: String = ""
)
