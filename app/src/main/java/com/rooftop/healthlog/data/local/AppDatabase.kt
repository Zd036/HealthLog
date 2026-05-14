package com.rooftop.healthlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rooftop.healthlog.data.local.dao.*
import com.rooftop.healthlog.data.local.entity.*

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
    version = 4,
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

        /** v2 → v3：新增 app_settings.enableRefillReminder 列（历史版本保留） */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN " +
                        "enableRefillReminder INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * 修改点1：v3 → v4 删除药品库存/购药提醒字段，同时移除 app_settings 中的全局购药提醒开关。
         * 开发阶段即使 fallbackToDestructiveMigration 生效，这个迁移也能覆盖正常升级路径。
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medications_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scheduleId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        dosage REAL NOT NULL,
                        unit TEXT NOT NULL,
                        specification REAL NOT NULL,
                        method TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(scheduleId) REFERENCES medication_schedules(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO medications_new (id, scheduleId, name, dosage, unit, specification, method, notes)
                    SELECT id, scheduleId, name, dosage, unit, specification, method, notes
                    FROM medications
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE medications")
                db.execSQL("ALTER TABLE medications_new RENAME TO medications")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medications_scheduleId ON medications(scheduleId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_settings_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        fontSize TEXT NOT NULL,
                        enableIntakeOutput INTEGER NOT NULL,
                        intakeReminderTimes TEXT NOT NULL,
                        outputReminderTimes TEXT NOT NULL,
                        lastDismissedThreeDayAlertDate INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO app_settings_new (
                        id, fontSize, enableIntakeOutput, intakeReminderTimes, outputReminderTimes, lastDismissedThreeDayAlertDate
                    )
                    SELECT id, fontSize, enableIntakeOutput, intakeReminderTimes, outputReminderTimes, lastDismissedThreeDayAlertDate
                    FROM app_settings
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE app_settings")
                db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "healthlog.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
