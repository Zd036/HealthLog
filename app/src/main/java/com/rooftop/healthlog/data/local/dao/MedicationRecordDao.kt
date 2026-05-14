package com.rooftop.healthlog.data.local.dao

import androidx.room.*
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationRecordDao {
    @Insert
    suspend fun insert(record: MedicationRecord): Long

    @Insert
    suspend fun insertAll(records: List<MedicationRecord>)

    @Update
    suspend fun update(record: MedicationRecord)

    @Query("SELECT * FROM medication_records WHERE scheduledTime BETWEEN :start AND :end ORDER BY scheduledTime DESC")
    fun getBetween(start: Long, end: Long): Flow<List<MedicationRecord>>

    @Query("SELECT * FROM medication_records WHERE scheduleId = :scheduleId AND scheduledTime BETWEEN :start AND :end")
    suspend fun getByScheduleAndDay(scheduleId: Long, start: Long, end: Long): List<MedicationRecord>

    @Query("SELECT * FROM medication_records ORDER BY scheduledTime DESC LIMIT 500")
    fun getAll(): Flow<List<MedicationRecord>>

    /** 修改点2：查询今天某个时间点是否已被处理（taken 或 missed 都算已处理）。 */
    @Query("SELECT COUNT(*) FROM medication_records WHERE scheduleId = :scheduleId AND scheduledTime = :scheduledTime AND status IN ('taken', 'missed')")
    suspend fun countRecordedToday(scheduleId: Long, scheduledTime: Long): Int

    /**
     * 修改点2：以"时间点"为粒度防重复写入。
     * 先整体检查，再批量插入该时间点下的所有药品记录，避免只插入第一条。
     */
    @Transaction
    suspend fun insertIfNotRecorded(
        scheduleId: Long,
        scheduledTime: Long,
        records: List<MedicationRecord>
    ): Boolean {
        if (countRecordedToday(scheduleId, scheduledTime) > 0) return false
        insertAll(records)
        return true
    }
}
