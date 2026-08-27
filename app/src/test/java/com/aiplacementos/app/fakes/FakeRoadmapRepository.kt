package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.RoadmapDto
import com.aiplacementos.app.data.remote.RoadmapTaskDto
import com.aiplacementos.app.domain.repository.RoadmapRepository

class FakeRoadmapRepository : RoadmapRepository {
    var generateResult: (suspend (Int?, Int) -> RoadmapDto)? = null
    var currentResult: (suspend (Int?) -> RoadmapDto?)? = null
    var updateTaskStatusResult: (suspend (Int, String) -> RoadmapTaskDto)? = null
    var reassessResult: (suspend (Int) -> RoadmapDto)? = null

    var lastGenerateArgs: Pair<Int?, Int>? = null

    override suspend fun generateRoadmap(jdId: Int?, durationDays: Int): RoadmapDto {
        lastGenerateArgs = jdId to durationDays
        val fn = requireNotNull(generateResult) { "generateResult not configured" }
        return fn(jdId, durationDays)
    }

    override suspend fun getCurrentRoadmap(jdId: Int?): RoadmapDto? {
        val fn = requireNotNull(currentResult) { "currentResult not configured" }
        return fn(jdId)
    }

    override suspend fun updateTaskStatus(taskId: Int, status: String): RoadmapTaskDto {
        val fn = requireNotNull(updateTaskStatusResult) { "updateTaskStatusResult not configured" }
        return fn(taskId, status)
    }

    override suspend fun reassessRoadmap(roadmapId: Int): RoadmapDto {
        val fn = requireNotNull(reassessResult) { "reassessResult not configured" }
        return fn(roadmapId)
    }
}
