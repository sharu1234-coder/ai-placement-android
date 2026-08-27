package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.AnswerResultDto
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.AssessmentDto
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.data.remote.AssessmentResultDto
import com.aiplacementos.app.data.remote.NextQuestionResponseDto

interface AssessmentRepository {
    suspend fun createBlueprint(roleId: Int?, jdId: Int?, category: String? = null): AssessmentBlueprintDto
    suspend fun startAssessment(blueprintId: Int): AssessmentDto
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
