package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ArenaAttemptResultDto
import com.aiplacementos.app.data.remote.ArenaAttemptStartDto
import com.aiplacementos.app.data.remote.ArenaChallengeDto
import com.aiplacementos.app.data.remote.ArenaChallengeHistoryDto
import com.aiplacementos.app.domain.repository.ArenaRepository

class FakeArenaRepository : ArenaRepository {
    /** Every Idempotency-Key this fake was handed, in order. A test asserting these are
     * equal across retries is what proves the caller holds one key per logical operation,
     * and a test asserting they differ is what proves a deliberate new action gets a new one. */
    val keys = mutableListOf<String?>()

    var listChallengesResult: (suspend () -> List<ArenaChallengeDto>)? = null
    var startAttemptResult: (suspend (String) -> ArenaAttemptStartDto)? = null
    var completeAttemptResult: (suspend (Int, Int) -> ArenaAttemptResultDto)? = null
    var getHistoryResult: (suspend (String) -> ArenaChallengeHistoryDto)? = null

    var lastElapsed: Int? = null

    override suspend fun listChallenges(): List<ArenaChallengeDto> {
        val fn = requireNotNull(listChallengesResult) { "listChallengesResult not configured" }
        return fn()
    }

    override suspend fun startAttempt(
        challengeKey: String,
        idempotencyKey: String?,
    ): ArenaAttemptStartDto {
        keys += idempotencyKey
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
