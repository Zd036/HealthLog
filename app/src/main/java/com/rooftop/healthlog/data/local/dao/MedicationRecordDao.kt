package com.rooftop.healthlog.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.*
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: MedicationRecord): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllOrAbort(records: List<MedicationRecord>)

    @Update
    suspend fun update(record: MedicationRecord)

    @Query("SELECT * FROM medication_records WHERE scheduledTime >= :start AND scheduledTime < :end ORDER BY scheduledTime DESC")
    fun getBetween(start: Long, end: Long): Flow<List<MedicationRecord>>

    @Query("SELECT * FROM medication_records WHERE scheduleId = :scheduleId AND scheduledTime >= :start AND scheduledTime < :end")
    suspend fun getByScheduleAndDay(scheduleId: Long, start: Long, end: Long): List<MedicationRecord>

    /** 历史页仅显示最近 500 条，避免全量加载影响滚动性能。 */
    @Query("SELECT * FROM medication_records ORDER BY scheduledTime DESC LIMIT 500")
    fun getRecentRecords(): Flow<List<MedicationRecord>>

    /** 导出时仍使用全量数据，不能受历史页 500 条限制影响。 */
    @Query("SELECT * FROM medication_records ORDER BY scheduledTime DESC")
    fun getAllRecords(): Flow<List<MedicationRecord>>

    /** 历史页用于判断是否需要显示“仅展示最近 500 条”的提示。 */
    @Query("SELECT COUNT(*) FROM medication_records")
    fun countAllRecords(): Flow<Int>

    /** 导入时按“计划表 + 计划时间 + 药品 + 状态”判断重复。 */
    @Query(
        "SELECT COUNT(*) FROM medication_records " +
            "WHERE scheduleId = :scheduleId AND scheduledTime = :scheduledTime " +
            "AND medicationId = :medicationId AND status = :status"
    )
    suspend fun countDuplicate(
        scheduleId: Long,
        scheduledTime: Long,
        medicationId: Long,
        status: String
    ): Int

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
        if (records.isEmpty()) return false
        if (countRecordedToday(scheduleId, scheduledTime) > 0) return false
        return try {
            insertAllOrAbort(records)
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }
}
