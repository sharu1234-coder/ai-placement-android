package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.CareerDecisionSimulationDto

interface CareerDecisionRepository {
    suspend fun simulate(roleIds: List<Int>, weeklyHours: Int?): CareerDecisionSimulationDto
}
