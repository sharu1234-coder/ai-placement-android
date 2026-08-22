package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.AssessmentBlueprintDto

interface AssessmentRepository {
    suspend fun createBlueprint(roleId: Int?, jdId: Int?): AssessmentBlueprintDto
}
