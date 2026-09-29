package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ChallengeAnswerResultDto
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import com.aiplacementos.app.database.entity.AlarmType
import com.aiplacementos.app.database.entity.ChallengeEntity
import com.aiplacementos.app.database.entity.ChallengeQuestionEntity
import kotlinx.coroutines.flow.Flow

/** A challenge and however much of its question set has been revealed/cached so far. */
data class ChallengeWithQuestions(
    val challenge: ChallengeEntity,
    val questions: List<ChallengeQuestionEntity>,
)

interface PlacementAlarmRepository {
    fun observeAlarms(): Flow<List<AlarmConfigEntity>>
    suspend fun getAlarm(alarmId: Long): AlarmConfigEntity?
    suspend fun createAlarm(
        hour: Int,
        minute: Int,
        repeatDays: String,
        title: String? = null,
        type: String = AlarmType.CUSTOM,
        notes: String? = null,
    ): Long
    suspend fun updateAlarm(alarm: AlarmConfigEntity)
    suspend fun setEnabled(alarm: AlarmConfigEntity, enabled: Boolean)
    suspend fun deleteAlarm(alarm: AlarmConfigEntity)

    /** No manual topic/skill picker exists anywhere -- this only tells the create-alarm
     * screen whether the student has any placement-relevant resume skills yet at all. */
    suspend fun checkEligibility(): Boolean

    /** For AlarmListScreen: which alarms currently have an unfinished challenge, so a
     * "Continue" affordance can be shown on the right row instead of the student having to
     * wait for the notification. */
    suspend fun activeChallengesByAlarm(): Map<Long, ChallengeEntity>

    suspend fun cachedChallenge(clientAlarmId: String): ChallengeWithQuestions?
    suspend fun startOrResumeChallenge(clientAlarmId: String, alarmConfigId: Long): ChallengeWithQuestions
    suspend fun submitAnswer(
        clientAlarmId: String,
        questionId: Int,
        studentAnswer: String,
    ): ChallengeAnswerResultDto
}
