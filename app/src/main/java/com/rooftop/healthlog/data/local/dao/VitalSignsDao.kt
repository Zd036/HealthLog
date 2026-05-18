package com.rooftop.healthlog.data.local.dao

import androidx.room.*
import com.rooftop.healthlog.data.local.entity.VitalSignsRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalSignsDao {
    @Insert
    suspend fun insert(record: VitalSignsRecord): Long

    @Update
    suspend fun update(record: VitalSignsRecord)

    @Delete
    suspend fun delete(record: VitalSignsRecord)

    @Query("SELECT * FROM vital_signs_records ORDER BY time DESC LIMIT 1")
    fun getLatest(): Flow<VitalSignsRecord?>

    @Query("SELECT * FROM vital_signs_records WHERE time >= :start AND time < :end ORDER BY time DESC")
    fun getBetween(start: Long, end: Long): Flow<List<VitalSignsRecord>>

    @Query("SELECT * FROM vital_signs_records WHERE weight IS NOT NULL AND time >= :start AND time < :end ORDER BY time DESC LIMIT 1")
    suspend fun getWeightInRange(start: Long, end: Long): VitalSignsRecord?

    @Query(
        "SELECT * FROM vital_signs_records " +
            "WHERE weight IS NOT NULL AND time < :currentTime AND time >= :startTime " +
            "ORDER BY time DESC LIMIT 1"
    )
    suspend fun getPreviousWeightWithinRange(currentTime: Long, startTime: Long): VitalSignsRecord?

    /** 历史页仅显示最近 500 条，避免全量加载影响滚动性能。 */
    @Query("SELECT * FROM vital_signs_records ORDER BY time DESC LIMIT 500")
    fun getRecentRecords(): Flow<List<VitalSignsRecord>>

    /** 导出时仍使用全量数据，不能受历史页 500 条限制影响。 */
    @Query("SELECT * FROM vital_signs_records ORDER BY time DESC")
    fun getAllRecords(): Flow<List<VitalSignsRecord>>

    /** 历史页用于判断是否需要显示“仅展示最近 500 条”的提示。 */
    @Query("SELECT COUNT(*) FROM vital_signs_records")
    fun countAllRecords(): Flow<Int>

    /** 导入时按“时间 + 六项体征字段”判断重复。 */
    @Query(
        "SELECT COUNT(*) FROM vital_signs_records " +
            "WHERE time = :time " +
            "AND ((:systolic IS NULL AND systolic IS NULL) OR systolic = :systolic) " +
            "AND ((:diastolic IS NULL AND diastolic IS NULL) OR diastolic = :diastolic) " +
            "AND ((:heartRate IS NULL AND heartRate IS NULL) OR heartRate = :heartRate) " +
            "AND ((:weight IS NULL AND weight IS NULL) OR weight = :weight) " +
            "AND ((:bloodSugar IS NULL AND bloodSugar IS NULL) OR bloodSugar = :bloodSugar)"
    )
    suspend fun countDuplicate(
        time: Long,
        systolic: Int?,
        diastolic: Int?,
        heartRate: Int?,
        weight: Float?,
        bloodSugar: Float?
    ): Int
}
