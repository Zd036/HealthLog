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

    @Query("SELECT * FROM vital_signs_records WHERE time BETWEEN :start AND :end ORDER BY time DESC")
    fun getBetween(start: Long, end: Long): Flow<List<VitalSignsRecord>>

    @Query("SELECT * FROM vital_signs_records WHERE weight IS NOT NULL AND time BETWEEN :start AND :end ORDER BY time DESC LIMIT 1")
    suspend fun getWeightInRange(start: Long, end: Long): VitalSignsRecord?

    @Query("SELECT * FROM vital_signs_records ORDER BY time DESC LIMIT 500")
    fun getAll(): Flow<List<VitalSignsRecord>>
}
