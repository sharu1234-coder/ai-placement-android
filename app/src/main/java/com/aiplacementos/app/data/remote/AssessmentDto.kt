package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrepareMeRequestDto(
    @SerialName("role_id") val roleId: Int? = null,
    @SerialName("jd_id") val jdId: Int? = null,
)

@Serializable
data class BlueprintSectionDto(
    val skill: String,
    @SerialName("assessment_type") val assessmentType: String,
    val weight: Double,
    @SerialName("question_count") val questionCount: Int,
    @SerialName("is_gap") val isGap: Boolean,
)

@Serializable
data class AssessmentBlueprintDto(
    val id: Int,
    @SerialName("role_id") val roleId: Int?,
    @SerialName("jd_id") val jdId: Int?,
    val source: String,
    val sections: List<BlueprintSectionDto>,
)
