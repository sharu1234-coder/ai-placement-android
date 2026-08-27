package com.aiplacementos.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.aiplacementos.app.database.dao.AlarmConfigDao
import com.aiplacementos.app.database.dao.ChallengeDao
import com.aiplacementos.app.database.dao.NotificationDao
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import com.aiplacementos.app.database.entity.ChallengeEntity
import com.aiplacementos.app.database.entity.ChallengeQuestionEntity
import com.aiplacementos.app.database.entity.NotificationEntity

/**
 * The app's only local SQL database -- everything else stays backend-fetched-on-demand per
 * the existing architecture (see NetworkModule's own doc comment). Originally built for the
 * Placement Accountability Alarm (schedules + resumable challenge state must survive process
 * death and work instantly when the alarm fires); also now holds the local-only Notification
 * Center table (Phase 5) since there's no backend notification feed to sync against.
 */
@Database(
    entities = [
        AlarmConfigEntity::class,
        ChallengeEntity::class,
        ChallengeQuestionEntity::class,
        NotificationEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmConfigDao(): AlarmConfigDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS notifications (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "body TEXT, " +
                        "receivedAt INTEGER NOT NULL, " +
                        "read INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_configs ADD COLUMN title TEXT")
                db.execSQL("ALTER TABLE alarm_configs ADD COLUMN type TEXT NOT NULL DEFAULT 'custom'")
                db.execSQL("ALTER TABLE alarm_configs ADD COLUMN notes TEXT")
            }
        }

        @Volatile
        private var instance: AlarmDatabase? = null

        fun getInstance(context: Context): AlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "placement_alarm.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
