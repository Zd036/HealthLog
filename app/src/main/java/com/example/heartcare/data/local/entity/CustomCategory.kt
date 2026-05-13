package com.example.heartcare.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 自定义出入量类型 */
@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // intake / output
    val name: String,
    val waterPercent: Float // 0~100
)
