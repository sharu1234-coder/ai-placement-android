package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.ArenaApi
import com.aiplacementos.app.data.remote.ArenaAttemptResultDto
import com.aiplacementos.app.data.remote.ArenaAttemptStartDto
import com.aiplacementos.app.data.remote.ArenaChallengeDto
import com.aiplacementos.app.data.remote.ArenaChallengeHistoryDto
import com.aiplacementos.app.data.remote.CompleteArenaAttemptRequestDto
import com.aiplacementos.app.domain.repository.ArenaRepository

class ArenaRepositoryImpl(private val arenaApi: ArenaApi) : ArenaRepository {
    override suspend fun listChallenges(): List<ArenaChallengeDto> = arenaApi.listChallenges()

    override suspend fun startAttempt(challengeKey: String): ArenaAttemptStartDto = arenaApi.startAttempt(challengeKey)

    override suspend fun completeAttempt(arenaAttemptId: Int, timeTakenSeconds: Int): ArenaAttemptResultDto =
        arenaApi.completeAttempt(arenaAttemptId, CompleteArenaAttemptRequestDto(timeTakenSeconds))

    override suspend fun getHistory(challengeKey: String): ArenaChallengeHistoryDto = arenaApi.getHistory(challengeKey)
}
