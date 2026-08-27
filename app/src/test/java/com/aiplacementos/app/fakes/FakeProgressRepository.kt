package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.domain.repository.ProgressRepository

class FakeProgressRepository : ProgressRepository {
    var dashboardResult: Result<ProgressDashboardDto> = Result.success(
        ProgressDashboardDto(
            readinessScore = null,
            completedAssessments = 0,
            completedInterviews = 0,
            roadmapTasksDone = 0,
            roadmapTasksTotal = 0,
            topWeaknesses = emptyList(),
            trend = emptyList(),
        )
    )

    override suspend fun getDashboard(): ProgressDashboardDto = dashboardResult.getOrThrow()
}
