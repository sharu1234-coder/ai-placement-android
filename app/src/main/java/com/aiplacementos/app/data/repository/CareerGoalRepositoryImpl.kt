package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.CareerGoalAnalyzeResponseDto
import com.aiplacementos.app.data.remote.CareerGoalCreateRequest
import com.aiplacementos.app.data.remote.CareerGoalDetailDto
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.data.remote.CareerGoalUpdateRequest
import com.aiplacementos.app.data.remote.CareerGoalsApi
import com.aiplacementos.app.data.remote.LinkApplicationRequest
import com.aiplacementos.app.domain.repository.CareerGoalRepository

class CareerGoalRepositoryImpl(private val careerGoalsApi: CareerGoalsApi) : CareerGoalRepository {
    override suspend fun listGoals(): List<CareerGoalListItemDto> = careerGoalsApi.listGoals()

    override suspend fun createGoal(
        title: String,
        company: String,
        location: String?,
        jobDescription: String,
    ): CareerGoalListItemDto = careerGoalsApi.createGoal(
        CareerGoalCreateRequest(
            title = title,
            company = company,
            location = location,
            jobDescription = jobDescription,
        ),
    )

    override suspend fun getGoal(careerGoalId: Int): CareerGoalDetailDto =
        careerGoalsApi.getGoal(careerGoalId)

    /** Status is the only field the app currently edits after creation, so the PATCH is sent
     * with that field alone -- every other field omitted means "leave alone" server-side. */
    override suspend fun setStatus(careerGoalId: Int, status: String): CareerGoalListItemDto =
        careerGoalsApi.updateGoal(careerGoalId, CareerGoalUpdateRequest(status = status))

    override suspend fun deleteGoal(careerGoalId: Int) = careerGoalsApi.deleteGoal(careerGoalId)

    override suspend fun analyzeGoal(careerGoalId: Int): CareerGoalAnalyzeResponseDto =
        careerGoalsApi.analyzeGoal(careerGoalId)

    override suspend fun linkApplication(careerGoalId: Int, savedJobId: Int): CareerGoalListItemDto =
        careerGoalsApi.linkApplication(careerGoalId, LinkApplicationRequest(savedJobId = savedJobId))

    override suspend fun unlinkApplication(careerGoalId: Int): CareerGoalListItemDto =
        careerGoalsApi.unlinkApplication(careerGoalId)
}
