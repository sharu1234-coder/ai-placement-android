package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.AnswerResultDto
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.AssessmentDto
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.data.remote.AssessmentResultDto
import com.aiplacementos.app.data.remote.AssessmentsApi
import com.aiplacementos.app.data.remote.NextQuestionResponseDto
import com.aiplacementos.app.data.remote.PrepareMeRequestDto
import com.aiplacementos.app.data.remote.StartAssessmentRequestDto
import com.aiplacementos.app.data.remote.SubmitAnswerRequestDto
import com.aiplacementos.app.domain.repository.AssessmentRepository

class AssessmentRepositoryImpl(private val assessmentsApi: AssessmentsApi) : AssessmentRepository {
    override suspend fun createBlueprint(roleId: Int?, jdId: Int?, category: String?): AssessmentBlueprintDto =
        assessmentsApi.createBlueprint(PrepareMeRequestDto(roleId = roleId, jdId = jdId, category = category))

    override suspend fun startAssessment(blueprintId: Int): AssessmentDto =
        assessmentsApi.startAssessment(StartAssessmentRequestDto(blueprintId = blueprintId))

    override suspend fun nextQuestion(assessmentId: Int): NextQuestionResponseDto =
        assessmentsApi.nextQuestion(assessmentId)

    override suspend fun submitAnswer(
        assessmentId: Int,
        questionId: Int,
        studentAnswer: String,
        timeTakenSeconds: Int?,
    ): AnswerResultDto =
        assessmentsApi.submitAnswer(
            assessmentId,
            SubmitAnswerRequestDto(
                questionId = questionId,
                studentAnswer = studentAnswer,
                timeTakenSeconds = timeTakenSeconds,
            ),
        )

    override suspend fun getResult(assessmentId: Int): AssessmentResultDto =
        assessmentsApi.getResult(assessmentId)

    override suspend fun getHistory(): List<AssessmentHistoryItemDto> = assessmentsApi.getHistory()
}
