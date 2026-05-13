package com.example.heartcare.data.local.dao

import androidx.room.*
import com.example.heartcare.data.local.entity.IntakeOutputRecord
import com.example.heartcare.data.local.entity.CustomCategory
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
    @Query("SELECT * FROM intake_output_records WHERE time BETWEEN :startTime AND :endTime ORDER BY time DESC")
    fun getBetween(startTime: Long, endTime: Long): Flow<List<IntakeOutputRecord>>

    @Query("SELECT * FROM intake_output_records ORDER BY time DESC LIMIT 500")
    fun getAll(): Flow<List<IntakeOutputRecord>>

    // 自定义类型
    @Insert
    suspend fun insertCategory(c: CustomCategory): Long

    @Query("SELECT * FROM custom_categories WHERE type=:type")
    fun getCategories(type: String): Flow<List<CustomCategory>>

    @Delete
    suspend fun deleteCategory(c: CustomCategory)
}
