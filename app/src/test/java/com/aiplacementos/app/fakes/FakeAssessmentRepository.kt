package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.AnswerResultDto
import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.AssessmentDto
import com.aiplacementos.app.data.remote.AssessmentHistoryItemDto
import com.aiplacementos.app.data.remote.AssessmentResultDto
import com.aiplacementos.app.data.remote.NextQuestionResponseDto
import com.aiplacementos.app.domain.repository.AssessmentRepository

class FakeAssessmentRepository : AssessmentRepository {
    var createBlueprintResult: (suspend (Int?, Int?, String?) -> AssessmentBlueprintDto)? = null
    var startAssessmentResult: (suspend (Int) -> AssessmentDto)? = null
    var nextQuestionResult: (suspend (Int) -> NextQuestionResponseDto)? = null
    var submitAnswerResult: (suspend (Int, Int, String, Int?) -> AnswerResultDto)? = null
    var getResultResult: (suspend (Int) -> AssessmentResultDto)? = null
    var historyResult: (suspend () -> List<AssessmentHistoryItemDto>)? = null

    var lastCreateBlueprintArgs: Triple<Int?, Int?, String?>? = null

    override suspend fun createBlueprint(roleId: Int?, jdId: Int?, category: String?): AssessmentBlueprintDto {
        lastCreateBlueprintArgs = Triple(roleId, jdId, category)
        val fn = requireNotNull(createBlueprintResult) { "createBlueprintResult not configured" }
        return fn(roleId, jdId, category)
    }

    override suspend fun startAssessment(blueprintId: Int): AssessmentDto {
        val fn = requireNotNull(startAssessmentResult) { "startAssessmentResult not configured" }
        return fn(blueprintId)
    }

    override suspend fun nextQuestion(assessmentId: Int): NextQuestionResponseDto {
        val fn = requireNotNull(nextQuestionResult) { "nextQuestionResult not configured" }
        return fn(assessmentId)
    }

    override suspend fun submitAnswer(
        assessmentId: Int,
        questionId: Int,
        studentAnswer: String,
        timeTakenSeconds: Int?,
    ): AnswerResultDto {
        val fn = requireNotNull(submitAnswerResult) { "submitAnswerResult not configured" }
        return fn(assessmentId, questionId, studentAnswer, timeTakenSeconds)
    }

    override suspend fun getResult(assessmentId: Int): AssessmentResultDto {
        val fn = requireNotNull(getResultResult) { "getResultResult not configured" }
        return fn(assessmentId)
    }

    override suspend fun getHistory(): List<AssessmentHistoryItemDto> {
        val fn = requireNotNull(historyResult) { "historyResult not configured" }
        return fn()
    }
}
