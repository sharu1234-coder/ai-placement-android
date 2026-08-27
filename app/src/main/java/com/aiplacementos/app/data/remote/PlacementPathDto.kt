package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class PlacementPathTopicDto(
    val id: Int,
    val stage: String,
    @SerialName("track_key") val trackKey: String,
    @SerialName("topic_key") val topicKey: String,
    val title: String,
    @SerialName("why_it_matters") val whyItMatters: String,
    @SerialName("skill_tag") val skillTag: String,
    val status: String, // locked/not_started/in_progress/completed
    val priority: String, // high/medium/low
    @SerialName("prerequisite_topic_id") val prerequisiteTopicId: Int? = null,
    @SerialName("has_practice") val hasPractice: Boolean,
    @SerialName("completed_via") val completedVia: String? = null,
)

@Serializable
data class PlacementPathStageDto(
    val stage: String,
    val topics: List<PlacementPathTopicDto>,
)

@Serializable
data class NextBestActionDto(
    @SerialName("topic_id") val topicId: Int,
    val title: String,
    val reason: String,
)

@Serializable
data class PlacementPathDto(
    val id: Int,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_topics") val completedTopics: Int,
    @SerialName("total_topics") val totalTopics: Int,
    val stages: List<PlacementPathStageDto>,
    @SerialName("next_best_action") val nextBestAction: NextBestActionDto? = null,
)

/** Single source of truth for "roadmap progress %" -- previously computed independently at
 * 4 call sites with 2 different rounding strategies (truncating integer division vs.
 * Double-then-round), which could show a different percentage for the same underlying data
 * depending on which screen you were looking at. Rounds (5/9 -> 56%), matching the more
 * precise of the two prior implementations. */
fun PlacementPathDto.progressPercent(): Int =
    if (totalTopics > 0) ((completedTopics.toDouble() / totalTopics) * 100).roundToInt() else 0

@Serializable
data class PracticeHandoffDto(
    @SerialName("blueprint_id") val blueprintId: Int,
)

@Serializable
data class UpdatePlacementPathTopicRequestDto(
    val status: String,
)
