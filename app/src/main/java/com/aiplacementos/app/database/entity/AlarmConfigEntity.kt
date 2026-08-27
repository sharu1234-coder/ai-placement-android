package com.aiplacementos.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Placement Alarm's schedule. Per product requirement, the student only ever configures
 * time + repeat for the actual ring/quiz mechanic -- there is no topic/question-count/
 * difficulty column because none of those are ever user-configurable (see
 * PlacementAlarmService.REQUIRED_QUESTIONS-equivalent on the backend). title/type/notes are
 * purely display metadata layered on top -- every alarm still rings and requires the same
 * 6-question resume-skill quiz to dismiss regardless of type; type is a label/icon only, not
 * a mechanic switch.
 *
 * repeatDays is a comma-separated set of java.util.Calendar day-of-week ints (1=Sunday..
 * 7=Saturday), e.g. "2,3,4,5,6" for weekdays. Empty means a one-shot alarm (fires once, does
 * not re-arm itself).
 */
@Entity(tableName = "alarm_configs")
data class AlarmConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: String = "",
    val enabled: Boolean = true,
    val title: String? = null,
    val type: String = AlarmType.CUSTOM,
    val notes: String? = null,
)

/** String constants (not a Kotlin enum) so Room can store the raw value directly with no
 * TypeConverter. Values match AlarmCreateScreen's FilterChip labels 1:1. */
object AlarmType {
    const val DSA_PRACTICE = "dsa_practice"
    const val SQL_PRACTICE = "sql_practice"
    const val APTITUDE = "aptitude"
    const val TECHNICAL_TEST = "technical_test"
    const val MOCK_INTERVIEW = "mock_interview"
    const val RESUME_REVIEW = "resume_review"
    const val JOB_APPLICATION = "job_application"
    const val CUSTOM = "custom"

    val ALL = listOf(
        DSA_PRACTICE, SQL_PRACTICE, APTITUDE, TECHNICAL_TEST, MOCK_INTERVIEW, RESUME_REVIEW, JOB_APPLICATION, CUSTOM,
    )

    fun label(type: String): String = when (type) {
        DSA_PRACTICE -> "DSA Practice"
        SQL_PRACTICE -> "SQL Practice"
        APTITUDE -> "Aptitude"
        TECHNICAL_TEST -> "Technical Test"
        MOCK_INTERVIEW -> "Mock Interview"
        RESUME_REVIEW -> "Resume Review"
        JOB_APPLICATION -> "Job Application"
        else -> "Custom"
    }
}
