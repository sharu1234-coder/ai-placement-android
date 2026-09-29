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
    // EVIDENCE COVERAGE: how much of this role's required skills the student's evidence
    // supports. Null when the role defines no skills at all -- the comparison cannot be made,
    // which is different from making it and getting 0.
    @SerialName("evidence_coverage_percent") val evidenceCoveragePercent: Int? = null,
    // Null exactly when evidenceCoveragePercent is null: a role that cannot be measured
    // cannot be classified either, and defaulting it to "stretch" would turn missing
    // configuration into a verdict about the student.
    val classification: String? = null, // best_fit | alternative | stretch
    @SerialName("biggest_gap_skill") val biggestGapSkill: String?,
    // Null, not 0.0, for an unmeasurable role -- "0 hours to close the gap" reads as
    // "you are already there".
    @SerialName("estimated_hours_to_strengthen") val estimatedHoursToStrengthen: Double? = null,
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
