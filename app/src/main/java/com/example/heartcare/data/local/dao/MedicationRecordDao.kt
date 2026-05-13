package com.example.heartcare.data.local.dao

import androidx.room.*
import com.example.heartcare.data.local.entity.MedicationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationRecordDao {
    @Insert
    suspend fun insert(record: MedicationRecord): Long

    @Update
    suspend fun update(record: MedicationRecord)

    @Query("SELECT * FROM medication_records WHERE scheduledTime BETWEEN :start AND :end ORDER BY scheduledTime DESC")
    fun getBetween(start: Long, end: Long): Flow<List<MedicationRecord>>

    @Query("SELECT * FROM medication_records WHERE scheduleId = :scheduleId AND scheduledTime BETWEEN :start AND :end")
    suspend fun getByScheduleAndDay(scheduleId: Long, start: Long, end: Long): List<MedicationRecord>

    @Query("SELECT * FROM medication_records ORDER BY scheduledTime DESC LIMIT 500")
    fun getAll(): Flow<List<MedicationRecord>>
}
