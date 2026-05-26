package com.rooftop.healthlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 应用设置 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val fontSize: String = "large",
    val enableIntakeOutput: Boolean = true,
    val intakeReminderTimes: String = "", // 用逗号分隔的时间字符串，如"08:00,14:00,20:00"
    val outputReminderTimes: String = "",
    /** 最近一次用户关闭"连续 3 天出入量不平衡"红色警告的日期（当天 00:00 时间戳） */
    val lastDismissedThreeDayAlertDate: Long = 0L,
    /** 最近一次自动备份完成时间 */
    val lastAutoBackupAt: Long = 0L
)
