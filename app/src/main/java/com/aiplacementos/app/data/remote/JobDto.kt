package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParseJdRequestDto(@SerialName("raw_text") val rawText: String)

@Serializable
data class RequirementDto(
    val id: Int,
    val category: String,
    val value: String,
    @SerialName("is_explicit") val isExplicit: Boolean,
    val importance: String,
)

@Serializable
data class JobDescriptionParseResponseDto(
    val id: Int,
    @SerialName("role_title") val roleTitle: String,
    @SerialName("detected_role_id") val detectedRoleId: Int?,
    @SerialName("parsing_confidence") val parsingConfidence: Double,
    val requirements: List<RequirementDto>,
)

@Serializable
data class SkillGapDto(
    val id: Int,
    @SerialName("skill_name") val skillName: String,
    @SerialName("gap_severity") val gapSeverity: String,
    val source: String,
)

@Serializable
data class JobMatchDto(
    val id: Int,
    @SerialName("matching_skills") val matchingSkills: List<String>,
    @SerialName("missing_skills") val missingSkills: List<String>,
    @SerialName("match_score") val matchScore: Double,
    @SerialName("skill_gaps") val skillGaps: List<SkillGapDto>,
)
