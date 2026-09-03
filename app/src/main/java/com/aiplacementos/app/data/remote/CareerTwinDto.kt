package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillTwinItemDto(
    val skill: String,
    val weight: Float,
    @SerialName("measured_score") val measuredScore: Float? = null,
    @SerialName("confidence_percent") val confidencePercent: Int,
    val state: String, // claimed/supported/strong/verified/stale
    @SerialName("last_verified_at") val lastVerifiedAt: String? = null,
)

@Serializable
data class CareerTwinDto(
    @SerialName("has_role") val hasRole: Boolean,
    @SerialName("role_family_key") val roleFamilyKey: String? = null,
    @SerialName("display_role_name") val displayRoleName: String? = null,
    @SerialName("role_source") val roleSource: String, // career_path/target_role/none
    @SerialName("readiness_percent") val readinessPercent: Int? = null,
    @SerialName("readiness_trend_percent") val readinessTrendPercent: Int? = null,
    @SerialName("is_estimate") val isEstimate: Boolean = false,
    val reasons: List<String> = emptyList(),
    val skills: List<SkillTwinItemDto> = emptyList(),
    @SerialName("biggest_gap_skill") val biggestGapSkill: String? = null,
    @SerialName("next_action_hint") val nextActionHint: String? = null,
    @SerialName("weak_skill_tags") val weakSkillTags: List<String> = emptyList(),
)

@Serializable
data class CareerTwinReadinessDto(
    @SerialName("has_role") val hasRole: Boolean,
    @SerialName("role_family_key") val roleFamilyKey: String? = null,
    @SerialName("display_role_name") val displayRoleName: String? = null,
    @SerialName("readiness_percent") val readinessPercent: Int? = null,
    @SerialName("readiness_trend_percent") val readinessTrendPercent: Int? = null,
    @SerialName("is_estimate") val isEstimate: Boolean = false,
    val reasons: List<String> = emptyList(),
    @SerialName("biggest_gap_skill") val biggestGapSkill: String? = null,
    @SerialName("next_action_hint") val nextActionHint: String? = null,
)
