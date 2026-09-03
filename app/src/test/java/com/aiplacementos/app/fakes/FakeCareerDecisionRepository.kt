package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.CareerDecisionSimulationDto
import com.aiplacementos.app.domain.repository.CareerDecisionRepository

class FakeCareerDecisionRepository : CareerDecisionRepository {
    var simulateResult: (suspend (List<Int>, Int?) -> CareerDecisionSimulationDto)? = null
    var lastRoleIds: List<Int>? = null
    var lastWeeklyHours: Int? = null

    override suspend fun simulate(roleIds: List<Int>, weeklyHours: Int?): CareerDecisionSimulationDto {
        lastRoleIds = roleIds
        lastWeeklyHours = weeklyHours
        val fn = requireNotNull(simulateResult) { "simulateResult not configured" }
        return fn(roleIds, weeklyHours)
    }
}
