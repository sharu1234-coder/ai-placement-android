package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ArenaAttemptResultDto
import com.aiplacementos.app.data.remote.ArenaAttemptStartDto
import com.aiplacementos.app.data.remote.ArenaChallengeDto
import com.aiplacementos.app.data.remote.ArenaChallengeHistoryDto

interface ArenaRepository {
    suspend fun listChallenges(): List<ArenaChallengeDto>
    suspend fun startAttempt(
        challengeKey: String,
        idempotencyKey: String? = null,
    ): ArenaAttemptStartDto
    suspend fun completeAttempt(arenaAttemptId: Int, timeTakenSeconds: Int): ArenaAttemptResultDto
    suspend fun getHistory(challengeKey: String): ArenaChallengeHistoryDto
}
