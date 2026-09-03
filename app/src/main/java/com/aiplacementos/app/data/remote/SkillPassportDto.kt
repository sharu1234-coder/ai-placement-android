package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EvidenceItemDto(
    val source: String, // assessment/practice/project/interview/resume
    @SerialName("source_id") val sourceId: Int? = null,
    val label: String,
    val score: Float? = null,
    @SerialName("detected_at") val detectedAt: String? = null,
)

@Serializable
data class SkillPassportItemDto(
    val skill: String,
    val level: String? = null, // null when state == "claimed" -- no evidence to base a level on
    @SerialName("confidence_percent") val confidencePercent: Int,
    val state: String, // claimed/supported/strong/verified/stale
    @SerialName("last_verified_at") val lastVerifiedAt: String? = null,
    val evidence: List<EvidenceItemDto> = emptyList(),
    @SerialName("next_action_hint") val nextActionHint: String,
)
