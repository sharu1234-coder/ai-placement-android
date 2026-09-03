package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimulateCareerDecisionsRequestDto(
    @SerialName("role_ids") val roleIds: List<Int>,
    @SerialName("weekly_hours") val weeklyHours: Int? = null,
)

@Serializable
data class CareerDecisionRoleDto(
    @SerialName("role_id") val roleId: Int,
    @SerialName("display_role_name") val displayRoleName: String,
    @SerialName("readiness_percent") val readinessPercent: Int,
    val classification: String, // best_fit | alternative | stretch
    @SerialName("biggest_gap_skill") val biggestGapSkill: String?,
    @SerialName("estimated_hours_to_strengthen") val estimatedHoursToStrengthen: Double,
    @SerialName("is_estimate") val isEstimate: Boolean,
    @SerialName("estimated_weeks") val estimatedWeeks: Int?,
    val reasons: List<String>,
)

@Serializable
data class CareerDecisionSimulationDto(
    val roles: List<CareerDecisionRoleDto>,
    @SerialName("shared_skills") val sharedSkills: List<String>,
    @SerialName("weekly_hours_used") val weeklyHoursUsed: Int?,
    val assumptions: List<String>,
)
