package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.CareerGoalAnalyzeResponseDto
import com.aiplacementos.app.data.remote.CareerGoalDetailDto
import com.aiplacementos.app.data.remote.CareerGoalListItemDto

/** Deliberately independent of CareerTwinRepository and SkillPassportRepository. All three
 * read the Evidence Engine server-side, and none of them calls into another on the client --
 * the composition happens once, on the server, where the rules live. */
interface CareerGoalRepository {
    suspend fun listGoals(): List<CareerGoalListItemDto>

    suspend fun createGoal(
        title: String,
        company: String,
        location: String?,
        jobDescription: String,
    ): CareerGoalListItemDto

    suspend fun getGoal(careerGoalId: Int): CareerGoalDetailDto

    suspend fun setStatus(careerGoalId: Int, status: String): CareerGoalListItemDto

    suspend fun deleteGoal(careerGoalId: Int)

    suspend fun analyzeGoal(careerGoalId: Int): CareerGoalAnalyzeResponseDto

    /** Link this goal to one of the student's own tracked applications. Runs no AI. */
    suspend fun linkApplication(careerGoalId: Int, savedJobId: Int): CareerGoalListItemDto

    /** Detach the application. Neither the goal nor the tracked job is deleted. */
    suspend fun unlinkApplication(careerGoalId: Int): CareerGoalListItemDto
}
