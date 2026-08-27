package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.ChallengeAnswerResultDto
import com.aiplacementos.app.data.remote.ChallengeStateDto
import com.aiplacementos.app.data.remote.PlacementAlarmApi
import com.aiplacementos.app.data.remote.StartChallengeRequestDto
import com.aiplacementos.app.data.remote.SubmitChallengeAnswerRequestDto
import com.aiplacementos.app.database.dao.AlarmConfigDao
import com.aiplacementos.app.database.dao.ChallengeDao
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import com.aiplacementos.app.database.entity.ChallengeEntity
import com.aiplacementos.app.database.entity.ChallengeQuestionEntity
import com.aiplacementos.app.domain.repository.ChallengeWithQuestions
import com.aiplacementos.app.domain.repository.PlacementAlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlacementAlarmRepositoryImpl(
    private val alarmConfigDao: AlarmConfigDao,
    private val challengeDao: ChallengeDao,
    private val api: PlacementAlarmApi,
) : PlacementAlarmRepository {

    override fun observeAlarms(): Flow<List<AlarmConfigEntity>> = alarmConfigDao.observeAll()

    override suspend fun getAllEnabledAlarms(): List<AlarmConfigEntity> = alarmConfigDao.getAllEnabled()

    override suspend fun getAlarm(alarmId: Long): AlarmConfigEntity? = alarmConfigDao.getById(alarmId)

    override suspend fun createAlarm(
        hour: Int,
        minute: Int,
        repeatDays: String,
        title: String?,
        type: String,
        notes: String?,
    ): Long = alarmConfigDao.insert(
        AlarmConfigEntity(
            hour = hour, minute = minute, repeatDays = repeatDays, title = title, type = type, notes = notes,
        )
    )

    override suspend fun updateAlarm(alarm: AlarmConfigEntity) {
        alarmConfigDao.update(alarm)
    }

    override suspend fun setEnabled(alarm: AlarmConfigEntity, enabled: Boolean) {
        alarmConfigDao.update(alarm.copy(enabled = enabled))
    }

    override suspend fun deleteAlarm(alarm: AlarmConfigEntity) {
        alarmConfigDao.delete(alarm)
    }

    override suspend fun checkEligibility(): Boolean = api.checkEligibility().eligible

    override suspend fun activeChallengesByAlarm(): Map<Long, ChallengeEntity> =
        challengeDao.getAllInProgress().associateBy { it.alarmConfigId }

    override suspend fun cachedChallenge(clientAlarmId: String): ChallengeWithQuestions? {
        val challenge = challengeDao.getChallenge(clientAlarmId) ?: return null
        return ChallengeWithQuestions(challenge, challengeDao.getQuestions(clientAlarmId))
    }

    override suspend fun startOrResumeChallenge(
        clientAlarmId: String,
        alarmConfigId: Long,
    ): ChallengeWithQuestions {
        // Idempotent server-side (see backend start_challenge): resuming an already-in-
        // progress challenge for this exact clientAlarmId returns its real current state
        // rather than generating a new random set of questions.
        val state = api.startChallenge(StartChallengeRequestDto(clientAlarmId))
        return persistState(clientAlarmId, alarmConfigId, state)
    }

    override suspend fun submitAnswer(
        clientAlarmId: String,
        questionId: Int,
        studentAnswer: String,
    ): ChallengeAnswerResultDto {
        val challenge = requireNotNull(challengeDao.getChallenge(clientAlarmId)) {
            "submitAnswer called before a challenge was started for $clientAlarmId"
        }
        val challengeId = requireNotNull(challenge.backendChallengeId) {
            "Challenge $clientAlarmId has no backend id yet"
        }

        val result = api.submitAnswer(
            challengeId,
            SubmitChallengeAnswerRequestDto(questionId = questionId, studentAnswer = studentAnswer),
        )

        if (result.isCorrect) {
            challengeDao.markCorrect(clientAlarmId, questionId)
        }
        if (result.challengeCompleted) {
            challengeDao.updateChallenge(challenge.copy(status = "completed"))
        } else {
            // The answer endpoint reports only whether *this* question was correct, not
            // what the next current question is -- re-fetch state to learn it and cache it,
            // same "reveal one at a time" contract as the wrong-answer retry path.
            val refreshed = api.getChallenge(challengeId)
            persistState(clientAlarmId, challenge.alarmConfigId, refreshed)
        }
        return result
    }

    private suspend fun persistState(
        clientAlarmId: String,
        alarmConfigId: Long,
        state: ChallengeStateDto,
    ): ChallengeWithQuestions {
        val existing = challengeDao.getChallenge(clientAlarmId)
        val entity = ChallengeEntity(
            clientAlarmId = clientAlarmId,
            alarmConfigId = alarmConfigId,
            backendChallengeId = state.challengeId,
            status = state.status,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: System.currentTimeMillis(),
        )
        challengeDao.upsertChallenge(entity)

        state.currentQuestion?.let { question ->
            challengeDao.upsertQuestions(
                listOf(
                    ChallengeQuestionEntity(
                        clientAlarmId = clientAlarmId,
                        questionIndex = question.index,
                        backendQuestionId = question.id,
                        skillTag = question.skillTag,
                        prompt = question.prompt,
                        optionsJson = Json.encodeToString(question.options),
                        isCorrect = false,
                    ),
                ),
            )
        }

        return ChallengeWithQuestions(entity, challengeDao.getQuestions(clientAlarmId))
    }
}
