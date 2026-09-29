package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.GenerateRoadmapRequestDto
import com.aiplacementos.app.data.remote.RoadmapApi
import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.RoadmapTaskDto
import com.aiplacementos.app.data.remote.UpdateRoadmapTaskRequestDto
import com.aiplacementos.app.domain.repository.RoadmapRepository

class RoadmapRepositoryImpl(private val roadmapApi: RoadmapApi) : RoadmapRepository {
    override suspend fun generateRoadmap(jdId: Int?, durationDays: Int): RoadmapDto =
        roadmapApi.generateRoadmap(GenerateRoadmapRequestDto(jdId = jdId, durationDays = durationDays))

    override suspend fun getCurrentRoadmap(jdId: Int?): RoadmapDto? = roadmapApi.getCurrentRoadmap(jdId).value

    override suspend fun updateTaskStatus(taskId: Int, status: String): RoadmapTaskDto =
        roadmapApi.updateTask(taskId, UpdateRoadmapTaskRequestDto(status = status))

    override suspend fun reassessRoadmap(roadmapId: Int): RoadmapDto = roadmapApi.reassessRoadmap(roadmapId)
}
