package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.RoadmapTaskDto

interface RoadmapRepository {
    suspend fun generateRoadmap(jdId: Int?, durationDays: Int): RoadmapDto
    suspend fun getCurrentRoadmap(jdId: Int?): RoadmapDto?
    suspend fun updateTaskStatus(taskId: Int, status: String): RoadmapTaskDto
    suspend fun reassessRoadmap(roadmapId: Int): RoadmapDto
}
