package com.rooftop.healthlog.data.local.dao

import androidx.room.*
import com.rooftop.healthlog.data.local.entity.IntakeOutputRecord
import com.rooftop.healthlog.data.local.entity.CustomCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeOutputDao {
    @Insert
    suspend fun insert(record: IntakeOutputRecord): Long

    @Update
    suspend fun update(record: IntakeOutputRecord)

    @Delete
    suspend fun delete(record: IntakeOutputRecord)

    /** 按时间区间（如 7:00 - 次日 7:00） */
    @Query("SELECT * FROM intake_output_records WHERE time >= :startTime AND time < :endTime ORDER BY time DESC")
    fun getBetween(startTime: Long, endTime: Long): Flow<List<IntakeOutputRecord>>

    /** 历史页仅显示最近 500 条，避免全量加载影响滚动性能。 */
    @Query("SELECT * FROM intake_output_records ORDER BY time DESC LIMIT 500")
    fun getRecentRecords(): Flow<List<IntakeOutputRecord>>

    /** 导出时仍使用全量数据，不能受历史页 500 条限制影响。 */
    @Query("SELECT * FROM intake_output_records ORDER BY time DESC")
    fun getAllRecords(): Flow<List<IntakeOutputRecord>>

    /** 历史页用于判断是否需要显示“仅展示最近 500 条”的提示。 */
    @Query("SELECT COUNT(*) FROM intake_output_records")
    fun countAllRecords(): Flow<Int>

    /** 导入时按“时间 + 类型 + 类别 + 数值”判断重复。 */
    @Query(
        "SELECT COUNT(*) FROM intake_output_records " +
            "WHERE time = :time AND type = :type AND category = :category AND amount = :amount"
    )
    suspend fun countDuplicate(time: Long, type: String, category: String, amount: Float): Int

    // 自定义类型
    @Insert
    suspend fun insertCategory(c: CustomCategory): Long

    @Query("SELECT * FROM custom_categories WHERE type=:type ORDER BY id ASC")
    fun getCategories(type: String): Flow<List<CustomCategory>>

    @Query("SELECT * FROM custom_categories ORDER BY type ASC, id ASC")
    fun getAllCategories(): Flow<List<CustomCategory>>

    @Delete
    suspend fun deleteCategory(c: CustomCategory)
}
