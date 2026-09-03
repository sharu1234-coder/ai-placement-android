package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.NextRecruiterAttackTurnResponseDto
import com.aiplacementos.app.data.remote.RecruiterAttackApi
import com.aiplacementos.app.data.remote.RecruiterAttackResultsDto
import com.aiplacementos.app.data.remote.RecruiterAttackSessionDto
import com.aiplacementos.app.data.remote.RecruiterAttackSessionListItemDto
import com.aiplacementos.app.data.remote.RecruiterAttackTurnDto
import com.aiplacementos.app.data.remote.StartRecruiterAttackSessionRequestDto
import com.aiplacementos.app.data.remote.SubmitRecruiterAttackAnswerRequestDto
import com.aiplacementos.app.domain.repository.RecruiterAttackRepository

class RecruiterAttackRepositoryImpl(private val recruiterAttackApi: RecruiterAttackApi) : RecruiterAttackRepository {
    override suspend fun startSession(
        sourceType: String, projectBuildId: Int?, claimText: String?
    ): RecruiterAttackSessionDto =
        recruiterAttackApi.startSession(StartRecruiterAttackSessionRequestDto(sourceType, projectBuildId, claimText))

    override suspend fun listSessions(): List<RecruiterAttackSessionListItemDto> = recruiterAttackApi.listSessions()

    override suspend fun nextTurn(sessionId: Int): NextRecruiterAttackTurnResponseDto =
        recruiterAttackApi.nextTurn(sessionId)

    override suspend fun answer(sessionId: Int, turnId: Int, answerText: String): RecruiterAttackTurnDto =
        recruiterAttackApi.answer(sessionId, SubmitRecruiterAttackAnswerRequestDto(turnId, answerText))

    override suspend fun getResults(sessionId: Int): RecruiterAttackResultsDto = recruiterAttackApi.getResults(sessionId)
}
