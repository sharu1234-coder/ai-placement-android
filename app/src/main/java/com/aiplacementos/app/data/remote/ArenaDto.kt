package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArenaChallengeDto(
    val key: String,
    val title: String,
    val category: String,
    val description: String,
    @SerialName("question_count") val questionCount: Int,
    @SerialName("time_limit_seconds") val timeLimitSeconds: Int,
    @SerialName("personal_best_score") val personalBestScore: Double? = null,
)

@Serializable
data class ArenaAttemptStartDto(
    @SerialName("arena_attempt_id") val arenaAttemptId: Int,
    @SerialName("assessment_id") val assessmentId: Int,
    @SerialName("challenge_key") val challengeKey: String,
    @SerialName("time_limit_seconds") val timeLimitSeconds: Int,
)

@Serializable
data class CompleteArenaAttemptRequestDto(@SerialName("time_taken_seconds") val timeTakenSeconds: Int)

@Serializable
data class ArenaAttemptResultDto(
    @SerialName("arena_attempt_id") val arenaAttemptId: Int,
    val score: Double,
    @SerialName("timed_out") val timedOut: Boolean,
    @SerialName("is_personal_best") val isPersonalBest: Boolean,
    @SerialName("previous_best_score") val previousBestScore: Double? = null,
)

@Serializable
data class ArenaAttemptHistoryItemDto(
    @SerialName("arena_attempt_id") val arenaAttemptId: Int,
    val score: Double,
    @SerialName("time_taken_seconds") val timeTakenSeconds: Int?,
    @SerialName("timed_out") val timedOut: Boolean,
    @SerialName("is_personal_best") val isPersonalBest: Boolean,
    @SerialName("completed_at") val completedAt: String,
)

@Serializable
data class ArenaChallengeHistoryDto(
    @SerialName("challenge_key") val challengeKey: String,
    @SerialName("personal_best_score") val personalBestScore: Double? = null,
    val attempts: List<ArenaAttemptHistoryItemDto>,
)
