package com.example.heartcare.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.heartcare.data.local.dao.*
import com.example.heartcare.data.local.entity.*

@Database(
    entities = [
        IntakeOutputRecord::class,
        MedicationSchedule::class,
        Medication::class,
        MedicationRecord::class,
        VitalSignsRecord::class,
        AppSettings::class,
        CustomCategory::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun intakeOutputDao(): IntakeOutputDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationRecordDao(): MedicationRecordDao
    abstract fun vitalSignsDao(): VitalSignsDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v1 → v2：新增 app_settings.lastDismissedThreeDayAlertDate 列 */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN " +
                        "lastDismissedThreeDayAlertDate INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "heartcare.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
