package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.PlacementPathTopicDto
import com.aiplacementos.app.domain.repository.PlacementPathRepository

class FakePlacementPathRepository : PlacementPathRepository {
    var currentResult: (suspend () -> PlacementPathDto?)? = null
    var historyResult: (suspend () -> List<PlacementPathDto>)? = null
    var generateResult: (suspend () -> PlacementPathDto)? = null
    var startTopicResult: (suspend (Int) -> PlacementPathTopicDto)? = null
    var practiceResult: (suspend (Int) -> Int)? = null
    var markCompleteResult: (suspend (Int) -> PlacementPathTopicDto)? = null

    var lastStartedTopicId: Int? = null
    var lastPracticedTopicId: Int? = null
    var lastCompletedTopicId: Int? = null

    override suspend fun getCurrent(): PlacementPathDto? {
        val fn = requireNotNull(currentResult) { "currentResult not configured" }
        return fn()
    }

    override suspend fun getHistory(): List<PlacementPathDto> {
        val fn = requireNotNull(historyResult) { "historyResult not configured" }
        return fn()
    }

    override suspend fun generate(): PlacementPathDto {
        val fn = requireNotNull(generateResult) { "generateResult not configured" }
        return fn()
    }

    override suspend fun startTopic(topicId: Int): PlacementPathTopicDto {
        lastStartedTopicId = topicId
        val fn = requireNotNull(startTopicResult) { "startTopicResult not configured" }
        return fn(topicId)
    }

    override suspend fun practice(topicId: Int): Int {
        lastPracticedTopicId = topicId
        val fn = requireNotNull(practiceResult) { "practiceResult not configured" }
        return fn(topicId)
    }

    override suspend fun markComplete(topicId: Int): PlacementPathTopicDto {
        lastCompletedTopicId = topicId
        val fn = requireNotNull(markCompleteResult) { "markCompleteResult not configured" }
        return fn(topicId)
    }
}
