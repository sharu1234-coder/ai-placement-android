package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.NextRecruiterAttackTurnResponseDto
import com.aiplacementos.app.data.remote.RecruiterAttackResultsDto
import com.aiplacementos.app.data.remote.RecruiterAttackSessionDto
import com.aiplacementos.app.data.remote.RecruiterAttackSessionListItemDto
import com.aiplacementos.app.data.remote.RecruiterAttackTurnDto
import com.aiplacementos.app.domain.repository.RecruiterAttackRepository

class FakeRecruiterAttackRepository : RecruiterAttackRepository {
    var startSessionResult: (suspend (String, Int?, String?) -> RecruiterAttackSessionDto)? = null
    var listSessionsResult: (suspend () -> List<RecruiterAttackSessionListItemDto>)? = null
    var nextTurnResult: (suspend (Int) -> NextRecruiterAttackTurnResponseDto)? = null
    var answerResult: (suspend (Int, Int, String) -> RecruiterAttackTurnDto)? = null
    var getResultsResult: (suspend (Int) -> RecruiterAttackResultsDto)? = null

    override suspend fun startSession(sourceType: String, projectBuildId: Int?, claimText: String?): RecruiterAttackSessionDto {
        val fn = requireNotNull(startSessionResult) { "startSessionResult not configured" }
        return fn(sourceType, projectBuildId, claimText)
    }

    override suspend fun listSessions(): List<RecruiterAttackSessionListItemDto> {
        val fn = requireNotNull(listSessionsResult) { "listSessionsResult not configured" }
        return fn()
    }

    override suspend fun nextTurn(sessionId: Int): NextRecruiterAttackTurnResponseDto {
        val fn = requireNotNull(nextTurnResult) { "nextTurnResult not configured" }
        return fn(sessionId)
    }

    override suspend fun answer(sessionId: Int, turnId: Int, answerText: String): RecruiterAttackTurnDto {
        val fn = requireNotNull(answerResult) { "answerResult not configured" }
        return fn(sessionId, turnId, answerText)
    }

    override suspend fun getResults(sessionId: Int): RecruiterAttackResultsDto {
        val fn = requireNotNull(getResultsResult) { "getResultsResult not configured" }
        return fn(sessionId)
    }
}
