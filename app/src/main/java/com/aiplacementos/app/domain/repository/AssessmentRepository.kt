package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.AnswerResultDto
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.AssessmentDto
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.data.remote.AssessmentResultDto
import com.aiplacementos.app.data.remote.NextQuestionResponseDto

interface AssessmentRepository {
    /** [idempotencyKey] names the logical operation, so a transport replay of it is free. */
    suspend fun createBlueprint(
        roleId: Int?,
        jdId: Int?,
        category: String? = null,
        idempotencyKey: String? = null,
    ): AssessmentBlueprintDto
    /** [idempotencyKey] identifies the logical operation, so a transport replay of it is free. */
    suspend fun startAssessment(blueprintId: Int, idempotencyKey: String? = null): AssessmentDto
    suspend fun nextQuestion(assessmentId: Int): NextQuestionResponseDto
    suspend fun submitAnswer(
        assessmentId: Int,
        questionId: Int,
        studentAnswer: String,
        timeTakenSeconds: Int?,
    ): AnswerResultDto
    suspend fun getResult(assessmentId: Int): AssessmentResultDto
    suspend fun getHistory(): List<AssessmentHistoryItemDto>
}
