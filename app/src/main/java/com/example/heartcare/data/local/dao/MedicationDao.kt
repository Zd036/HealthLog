package com.example.heartcare.data.local.dao

import androidx.room.*
import com.example.heartcare.data.local.entity.Medication
import com.example.heartcare.data.local.entity.MedicationSchedule
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Insert
    suspend fun insertSchedule(s: MedicationSchedule): Long

    @Update
    suspend fun updateSchedule(s: MedicationSchedule)

    @Delete
    suspend fun deleteSchedule(s: MedicationSchedule)

    @Query("SELECT * FROM medication_schedules ORDER BY time ASC")
    fun getAllSchedules(): Flow<List<MedicationSchedule>>

    @Query("SELECT * FROM medication_schedules WHERE enabled = 1 ORDER BY time ASC")
    fun getEnabledSchedules(): Flow<List<MedicationSchedule>>

    @Query("SELECT * FROM medication_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): MedicationSchedule?

    @Insert
    suspend fun insertMedication(m: Medication): Long

    @Update
    suspend fun updateMedication(m: Medication)

    @Delete
    suspend fun deleteMedication(m: Medication)

    @Query("SELECT * FROM medications WHERE scheduleId = :scheduleId")
    fun getMedicationsForSchedule(scheduleId: Long): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE scheduleId = :scheduleId")
    suspend fun getMedicationsForScheduleSync(scheduleId: Long): List<Medication>

    @Query("SELECT * FROM medications")
    fun getAllMedications(): Flow<List<Medication>>
}
