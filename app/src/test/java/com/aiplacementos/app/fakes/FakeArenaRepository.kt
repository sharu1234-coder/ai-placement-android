package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ArenaAttemptResultDto
import com.aiplacementos.app.data.remote.ArenaAttemptStartDto
import com.aiplacementos.app.data.remote.ArenaChallengeDto
import com.aiplacementos.app.data.remote.ArenaChallengeHistoryDto
import com.aiplacementos.app.domain.repository.ArenaRepository

class FakeArenaRepository : ArenaRepository {
    var listChallengesResult: (suspend () -> List<ArenaChallengeDto>)? = null
    var startAttemptResult: (suspend (String) -> ArenaAttemptStartDto)? = null
    var completeAttemptResult: (suspend (Int, Int) -> ArenaAttemptResultDto)? = null
    var getHistoryResult: (suspend (String) -> ArenaChallengeHistoryDto)? = null

    var lastElapsed: Int? = null

    override suspend fun listChallenges(): List<ArenaChallengeDto> {
        val fn = requireNotNull(listChallengesResult) { "listChallengesResult not configured" }
        return fn()
    }

    override suspend fun startAttempt(challengeKey: String): ArenaAttemptStartDto {
        val fn = requireNotNull(startAttemptResult) { "startAttemptResult not configured" }
        return fn(challengeKey)
    }

    override suspend fun completeAttempt(arenaAttemptId: Int, timeTakenSeconds: Int): ArenaAttemptResultDto {
        lastElapsed = timeTakenSeconds
        val fn = requireNotNull(completeAttemptResult) { "completeAttemptResult not configured" }
        return fn(arenaAttemptId, timeTakenSeconds)
    }

    override suspend fun getHistory(challengeKey: String): ArenaChallengeHistoryDto {
        val fn = requireNotNull(getHistoryResult) { "getHistoryResult not configured" }
        return fn(challengeKey)
    }
}
