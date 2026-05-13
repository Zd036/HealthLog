package com.example.heartcare.data.repository

import com.example.heartcare.data.local.dao.SettingsDao
import com.example.heartcare.data.local.entity.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: SettingsDao) {

    /** 设置流，若尚未写入则返回默认值 */
    val settings: Flow<AppSettings> = dao.getSettings().map { it ?: AppSettings() }

    suspend fun getOrDefault(): AppSettings = dao.getSettingsSync() ?: AppSettings()

    suspend fun update(settings: AppSettings) = dao.upsert(settings)

    suspend fun setFontSize(size: String) {
        val cur = getOrDefault()
        dao.upsert(cur.copy(fontSize = size))
    }

    suspend fun setEnableIntakeOutput(enabled: Boolean) {
        val cur = getOrDefault()
        dao.upsert(cur.copy(enableIntakeOutput = enabled))
    }

    suspend fun setIntakeReminderTimes(times: String) {
        val cur = getOrDefault()
        dao.upsert(cur.copy(intakeReminderTimes = times))
    }

    suspend fun setOutputReminderTimes(times: String) {
        val cur = getOrDefault()
        dao.upsert(cur.copy(outputReminderTimes = times))
    }

    /** 记录用户已关闭"连续 3 天出入量不平衡"警告的日期 */
    suspend fun setThreeDayAlertDismissed(dayStart: Long) {
        // 确保一行存在，再 UPDATE 局部字段（避免全表替换丢失其他字段）
        if (dao.getSettingsSync() == null) dao.upsert(AppSettings())
        dao.updateLastDismissedThreeDayAlertDate(dayStart)
    }
}
