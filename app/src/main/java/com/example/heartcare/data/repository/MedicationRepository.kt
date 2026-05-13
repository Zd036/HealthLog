package com.example.heartcare.data.repository

import com.example.heartcare.data.local.dao.MedicationDao
import com.example.heartcare.data.local.dao.MedicationRecordDao
import com.example.heartcare.data.local.entity.Medication
import com.example.heartcare.data.local.entity.MedicationRecord
import com.example.heartcare.data.local.entity.MedicationSchedule
import kotlinx.coroutines.flow.Flow

class MedicationRepository(
    private val medDao: MedicationDao,
    private val recordDao: MedicationRecordDao
) {
    fun getAllSchedules(): Flow<List<MedicationSchedule>> = medDao.getAllSchedules()
    fun getEnabledSchedules(): Flow<List<MedicationSchedule>> = medDao.getEnabledSchedules()
    fun getMedicationsForSchedule(scheduleId: Long): Flow<List<Medication>> =
        medDao.getMedicationsForSchedule(scheduleId)

    suspend fun getMedicationsForScheduleSync(scheduleId: Long): List<Medication> =
        medDao.getMedicationsForScheduleSync(scheduleId)

    suspend fun getScheduleById(id: Long): MedicationSchedule? = medDao.getScheduleById(id)

    suspend fun insertSchedule(s: MedicationSchedule): Long = medDao.insertSchedule(s)
    suspend fun updateSchedule(s: MedicationSchedule) = medDao.updateSchedule(s)
    suspend fun deleteSchedule(s: MedicationSchedule) = medDao.deleteSchedule(s)

    suspend fun insertMedication(m: Medication): Long = medDao.insertMedication(m)
    suspend fun updateMedication(m: Medication) = medDao.updateMedication(m)
    suspend fun deleteMedication(m: Medication) = medDao.deleteMedication(m)

    fun getAllMedications(): Flow<List<Medication>> = medDao.getAllMedications()

    suspend fun insertRecord(r: MedicationRecord): Long = recordDao.insert(r)
    suspend fun updateRecord(r: MedicationRecord) = recordDao.update(r)
    fun getRecordsBetween(start: Long, end: Long): Flow<List<MedicationRecord>> =
        recordDao.getBetween(start, end)

    suspend fun getRecordsByScheduleAndDay(scheduleId: Long, start: Long, end: Long): List<MedicationRecord> =
        recordDao.getByScheduleAndDay(scheduleId, start, end)

    fun allRecords(): Flow<List<MedicationRecord>> = recordDao.getAll()
}
