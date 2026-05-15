package com.rooftop.healthlog.data.repository

import com.rooftop.healthlog.data.local.dao.MedicationDao
import com.rooftop.healthlog.data.local.dao.MedicationRecordDao
import com.rooftop.healthlog.data.local.entity.Medication
import com.rooftop.healthlog.data.local.entity.MedicationRecord
import com.rooftop.healthlog.data.local.entity.MedicationSchedule
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

    suspend fun hasMedicationsForSchedule(scheduleId: Long): Boolean =
        medDao.getMedicationsForScheduleSync(scheduleId).isNotEmpty()

    suspend fun getScheduleById(id: Long): MedicationSchedule? = medDao.getScheduleById(id)

    suspend fun insertSchedule(s: MedicationSchedule): Long = medDao.insertSchedule(s)
    suspend fun updateSchedule(s: MedicationSchedule) = medDao.updateSchedule(s)
    suspend fun deleteSchedule(s: MedicationSchedule) = medDao.deleteSchedule(s)

    suspend fun insertMedication(m: Medication): Long = medDao.insertMedication(m)
    suspend fun updateMedication(m: Medication) = medDao.updateMedication(m)
    suspend fun deleteMedication(m: Medication) = medDao.deleteMedication(m)

    fun getAllMedications(): Flow<List<Medication>> = medDao.getAllMedications()

    suspend fun insertRecord(r: MedicationRecord): Long = recordDao.insert(r)
    suspend fun insertRecordsIfNotRecorded(
        scheduleId: Long,
        scheduledTime: Long,
        records: List<MedicationRecord>
    ): Boolean = recordDao.insertIfNotRecorded(scheduleId, scheduledTime, records)

    suspend fun updateRecord(r: MedicationRecord) = recordDao.update(r)
    fun getRecordsBetween(start: Long, end: Long): Flow<List<MedicationRecord>> =
        recordDao.getBetween(start, end)

    suspend fun getRecordsByScheduleAndDay(scheduleId: Long, start: Long, end: Long): List<MedicationRecord> =
        recordDao.getByScheduleAndDay(scheduleId, start, end)

    /** 修改点2：今天该时间点是否已被处理（已服用或漏服）。 */
    suspend fun countRecordedTodayForSchedule(scheduleId: Long, scheduledTime: Long): Int =
        recordDao.countRecordedToday(scheduleId, scheduledTime)

    /** 历史记录页仅取最近 500 条，避免全量数据拖慢列表。 */
    fun getRecentRecordsForDisplay(): Flow<List<MedicationRecord>> = recordDao.getRecentRecords()

    /** 导出功能仍需读取全量数据。 */
    fun getAllRecordsForExport(): Flow<List<MedicationRecord>> = recordDao.getAllRecords()

    /** 历史页根据总数决定是否显示“最近 500 条”提示。 */
    fun countAllRecordEntries(): Flow<Int> = recordDao.countAllRecords()

    /** 导入时按业务字段判断重复。 */
    suspend fun countDuplicateRecord(record: MedicationRecord): Int =
        recordDao.countDuplicate(
            scheduleId = record.scheduleId,
            scheduledTime = record.scheduledTime,
            medicationId = record.medicationId,
            status = record.status
        )
}
