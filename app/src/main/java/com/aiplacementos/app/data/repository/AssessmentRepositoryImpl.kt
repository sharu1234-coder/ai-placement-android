package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.AssessmentBlueprintDto
import com.aiplacementos.app.data.remote.AssessmentsApi
import com.aiplacementos.app.data.remote.PrepareMeRequestDto
import com.aiplacementos.app.domain.repository.AssessmentRepository

class AssessmentRepositoryImpl(private val assessmentsApi: AssessmentsApi) : AssessmentRepository {
    override suspend fun createBlueprint(roleId: Int?, jdId: Int?): AssessmentBlueprintDto =
        assessmentsApi.createBlueprint(PrepareMeRequestDto(roleId = roleId, jdId = jdId))
}
