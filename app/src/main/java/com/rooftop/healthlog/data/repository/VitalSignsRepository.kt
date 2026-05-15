package com.rooftop.healthlog.data.repository

import com.rooftop.healthlog.data.local.dao.VitalSignsDao
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import com.rooftop.healthlog.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class VitalSignsRepository(private val dao: VitalSignsDao) {
    suspend fun insert(r: VitalSignsRecord): Long = dao.insert(r)
    fun latest(): Flow<VitalSignsRecord?> = dao.getLatest()
    fun between(start: Long, end: Long): Flow<List<VitalSignsRecord>> = dao.getBetween(start, end)
    fun todayRecords(): Flow<List<VitalSignsRecord>> {
        val start = DateUtils.todayStart()
        val end = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return dao.getBetween(start, end)
    }
    suspend fun getWeightInRange(start: Long, end: Long): VitalSignsRecord? =
        dao.getWeightInRange(start, end)
    suspend fun getPreviousWeightWithin24Hours(currentTime: Long): VitalSignsRecord? =
        dao.getPreviousWeightWithinRange(currentTime, currentTime - 24L * 3600_000L)

    /** 历史记录页仅取最近 500 条，避免全量数据拖慢列表。 */
    fun getRecentRecordsForDisplay(): Flow<List<VitalSignsRecord>> = dao.getRecentRecords()

    /** 导出功能仍需读取全量数据。 */
    fun getAllRecordsForExport(): Flow<List<VitalSignsRecord>> = dao.getAllRecords()

    /** 历史页根据总数决定是否显示“最近 500 条”提示。 */
    fun countAllRecords(): Flow<Int> = dao.countAllRecords()

    /** 导入时按业务字段判断重复。 */
    suspend fun countDuplicate(record: VitalSignsRecord): Int =
        dao.countDuplicate(
            time = record.time,
            systolic = record.systolic,
            diastolic = record.diastolic,
            heartRate = record.heartRate,
            weight = record.weight,
            bloodSugar = record.bloodSugar
        )
}
