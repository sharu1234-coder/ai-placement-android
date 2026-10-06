package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.NextRecruiterAttackTurnResponseDto
import com.aiplacementos.app.data.remote.RecruiterAttackResultsDto
import com.aiplacementos.app.data.remote.RecruiterAttackSessionDto
import com.aiplacementos.app.data.remote.RecruiterAttackSessionListItemDto
import com.aiplacementos.app.data.remote.RecruiterAttackTurnDto

interface RecruiterAttackRepository {
    suspend fun startSession(
        sourceType: String,
        projectBuildId: Int?,
        claimText: String?,
        idempotencyKey: String? = null,
    ): RecruiterAttackSessionDto
    suspend fun listSessions(): List<RecruiterAttackSessionListItemDto>
    suspend fun nextTurn(sessionId: Int): NextRecruiterAttackTurnResponseDto
    suspend fun answer(sessionId: Int, turnId: Int, answerText: String): RecruiterAttackTurnDto
    suspend fun getResults(sessionId: Int): RecruiterAttackResultsDto
}
