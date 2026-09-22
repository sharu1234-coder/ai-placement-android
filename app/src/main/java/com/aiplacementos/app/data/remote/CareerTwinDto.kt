package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillTwinItemDto(
    val skill: String,
    val weight: Float,
    @SerialName("measured_score") val measuredScore: Float? = null,
    // Only ever one of five values (0/30/45/70/90), one per state -- use it for bar lengths
    // and ordering, never as displayed text. Show confidenceBand instead.
    @SerialName("confidence_percent") val confidencePercent: Int,
    // none/low/moderate/high/very_high -- the honest resolution of the value above.
    // Defaulted so an older backend that doesn't send it still deserializes.
    @SerialName("confidence_band") val confidenceBand: String = "none",
    val state: String, // claimed/supported/strong/verified/stale
    // A real verification date -- self-asserted evidence (resume keyword, typed-in project)
    // never sets this, so it can be null even when evidence exists.
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
    // Evidence sources the backend could not read at all ("external" = GitHub, "resume"), as
    // opposed to sources it read and found nothing in. The distinction is the point: a skill
    // with no evidence because we never looked is not a skill the student lacks.
    @SerialName("unavailable_sources") val unavailableSources: List<String> = emptyList(),
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
    @SerialName("unavailable_sources") val unavailableSources: List<String> = emptyList(),
)
