package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ChallengeAnswerResultDto
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import com.aiplacementos.app.database.entity.ChallengeEntity
import com.aiplacementos.app.domain.repository.ChallengeWithQuestions
import com.aiplacementos.app.domain.repository.PlacementAlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlacementAlarmRepository : PlacementAlarmRepository {
    private val alarmsFlow = MutableStateFlow<List<AlarmConfigEntity>>(emptyList())
    private var nextAlarmId = 1L

    val challenges = mutableMapOf<String, ChallengeWithQuestions>()
    var eligibleResult: Result<Boolean> = Result.success(true)
    var startOrResumeResult: (suspend (clientAlarmId: String, alarmConfigId: Long) -> ChallengeWithQuestions)? = null
    var submitAnswerImpl: (suspend (clientAlarmId: String, questionId: Int, answer: String) -> ChallengeAnswerResultDto)? = null

    data class CreateAlarmArgs(
        val hour: Int,
        val minute: Int,
        val repeatDays: String,
        val title: String?,
        val type: String,
        val notes: String?,
    )

    var lastCreatedAlarm: CreateAlarmArgs? = null
    var lastUpdatedAlarm: AlarmConfigEntity? = null

    /** Lets a test seed an existing alarm (e.g. for edit-mode ViewModel tests) without going
     * through createAlarm. */
    fun seedAlarm(alarm: AlarmConfigEntity) {
        alarmsFlow.value = alarmsFlow.value + alarm
    }

    override fun observeAlarms(): Flow<List<AlarmConfigEntity>> = alarmsFlow

    override suspend fun getAllEnabledAlarms(): List<AlarmConfigEntity> = alarmsFlow.value.filter { it.enabled }

    override suspend fun getAlarm(alarmId: Long): AlarmConfigEntity? = alarmsFlow.value.firstOrNull { it.id == alarmId }

    override suspend fun createAlarm(
        hour: Int,
        minute: Int,
        repeatDays: String,
        title: String?,
        type: String,
        notes: String?,
    ): Long {
        lastCreatedAlarm = CreateAlarmArgs(hour, minute, repeatDays, title, type, notes)
        val id = nextAlarmId++
        alarmsFlow.value = alarmsFlow.value + AlarmConfigEntity(
            id = id, hour = hour, minute = minute, repeatDays = repeatDays, title = title, type = type, notes = notes,
        )
        return id
    }

    override suspend fun updateAlarm(alarm: AlarmConfigEntity) {
        lastUpdatedAlarm = alarm
        alarmsFlow.value = alarmsFlow.value.map { if (it.id == alarm.id) alarm else it }
    }

    override suspend fun setEnabled(alarm: AlarmConfigEntity, enabled: Boolean) {
        alarmsFlow.value = alarmsFlow.value.map { if (it.id == alarm.id) it.copy(enabled = enabled) else it }
    }

    override suspend fun deleteAlarm(alarm: AlarmConfigEntity) {
        alarmsFlow.value = alarmsFlow.value.filterNot { it.id == alarm.id }
    }

    override suspend fun checkEligibility(): Boolean = eligibleResult.getOrThrow()

    override suspend fun activeChallengesByAlarm(): Map<Long, ChallengeEntity> =
        challenges.values.associate { it.challenge.alarmConfigId to it.challenge }

    override suspend fun cachedChallenge(clientAlarmId: String): ChallengeWithQuestions? = challenges[clientAlarmId]

    override suspend fun startOrResumeChallenge(clientAlarmId: String, alarmConfigId: Long): ChallengeWithQuestions {
        val fn = requireNotNull(startOrResumeResult) { "startOrResumeResult not configured" }
        val result = fn(clientAlarmId, alarmConfigId)
        challenges[clientAlarmId] = result
        return result
    }

    override suspend fun submitAnswer(
        clientAlarmId: String,
        questionId: Int,
        studentAnswer: String,
    ): ChallengeAnswerResultDto {
        val fn = requireNotNull(submitAnswerImpl) { "submitAnswerImpl not configured" }
        return fn(clientAlarmId, questionId, studentAnswer)
    }
}
