package com.rooftop.healthlog.ui.medication

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rooftop.healthlog.HealthLogApp
import com.rooftop.healthlog.data.local.entity.Medication
import com.rooftop.healthlog.data.local.entity.MedicationSchedule
import com.rooftop.healthlog.worker.MedicationReminderScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScheduleWithMeds(val schedule: MedicationSchedule, val medications: List<Medication>)

class MedicationViewModel(private val app: HealthLogApp) : AndroidViewModel(app) {

    private val repo = app.medicationRepository

    /** 所有时间点及其药品 */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val schedules: StateFlow<List<ScheduleWithMeds>> =
        repo.getAllSchedules().flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList())
            else combine(list.map { s ->
                repo.getMedicationsForSchedule(s.id).map { ScheduleWithMeds(s, it) }
            }) { it.toList() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleEnabled(s: MedicationSchedule, enabled: Boolean) {
        viewModelScope.launch {
            repo.updateSchedule(s.copy(enabled = enabled))
            MedicationReminderScheduler.rescheduleAll(app)
        }
    }

    fun upsertSchedule(time: String, scheduleId: Long?, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val id = if (scheduleId == null) {
                repo.insertSchedule(MedicationSchedule(time = time))
            } else {
                val existing = repo.getScheduleById(scheduleId)
                if (existing != null) {
                    repo.updateSchedule(existing.copy(time = time))
                }
                scheduleId
            }
            MedicationReminderScheduler.rescheduleAll(app)
            onDone(id)
        }
    }

    fun deleteSchedule(s: MedicationSchedule) {
        viewModelScope.launch {
            repo.deleteSchedule(s)
            MedicationReminderScheduler.rescheduleAll(app)
        }
    }

    fun upsertMedication(m: Medication) {
        viewModelScope.launch {
            if (m.id == 0L) repo.insertMedication(m) else repo.updateMedication(m)
        }
    }

    fun deleteMedication(m: Medication) {
        viewModelScope.launch { repo.deleteMedication(m) }
    }

    suspend fun getMedicationsFor(scheduleId: Long): List<Medication> =
        repo.getMedicationsForScheduleSync(scheduleId)
}
