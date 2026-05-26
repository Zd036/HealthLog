package com.rooftop.healthlog.data.local.dao

import androidx.room.*
import com.rooftop.healthlog.data.local.entity.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsSync(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettings)

    @Query("UPDATE app_settings SET lastDismissedThreeDayAlertDate = :day WHERE id = 1")
    suspend fun updateLastDismissedThreeDayAlertDate(day: Long)

    @Query("UPDATE app_settings SET lastAutoBackupAt = :time WHERE id = 1")
    suspend fun updateLastAutoBackupAt(time: Long)
}
