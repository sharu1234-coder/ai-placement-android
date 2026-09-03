package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.CareerDecisionApi
import com.aiplacementos.app.data.remote.CareerDecisionSimulationDto
import com.aiplacementos.app.data.remote.SimulateCareerDecisionsRequestDto
import com.aiplacementos.app.domain.repository.CareerDecisionRepository

class CareerDecisionRepositoryImpl(private val careerDecisionApi: CareerDecisionApi) : CareerDecisionRepository {
    override suspend fun simulate(roleIds: List<Int>, weeklyHours: Int?): CareerDecisionSimulationDto =
        careerDecisionApi.simulate(SimulateCareerDecisionsRequestDto(roleIds, weeklyHours))
}
