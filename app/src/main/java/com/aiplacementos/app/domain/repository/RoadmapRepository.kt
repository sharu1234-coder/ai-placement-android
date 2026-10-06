package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.RoadmapTaskDto

interface RoadmapRepository {
    /** [idempotencyKey] names the logical operation, so a transport replay of it is free. */
    suspend fun generateRoadmap(
        jdId: Int?,
        durationDays: Int,
        idempotencyKey: String? = null,
    ): RoadmapDto
    suspend fun getCurrentRoadmap(jdId: Int?): RoadmapDto?
    suspend fun updateTaskStatus(taskId: Int, status: String): RoadmapTaskDto
    suspend fun reassessRoadmap(roadmapId: Int, idempotencyKey: String? = null): RoadmapDto
}
