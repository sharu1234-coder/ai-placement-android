package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoleDto(
    val id: Int,
    val name: String,
    val category: String? = null,
    val description: String? = null,
)

/** Exactly one of [roleId]/[customRole] must be set -- mirrors the backend's
 * RoleSelectRequest validator (a catalog pick or a free-text role, never both). */
@Serializable
data class RoleSelectRequestDto(
    @SerialName("role_id") val roleId: Int? = null,
    @SerialName("custom_role") val customRole: String? = null,
)

@Serializable
data class SecondaryRoleSelectRequestDto(@SerialName("role_id") val roleId: Int)

@Serializable
data class ProfileUpsertDto(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("mobile_number") val mobileNumber: String? = null,
    val college: String? = null,
    @SerialName("graduation_year") val graduationYear: Int? = null,
    val degree: String? = null,
    val branch: String? = null,
    @SerialName("experience_level") val experienceLevel: String? = null,
    @SerialName("preparation_goals") val preparationGoals: List<String>? = null,
)

@Serializable
data class ProfileDto(
    val id: Int,
    @SerialName("full_name") val fullName: String?,
    val email: String,
    @SerialName("mobile_number") val mobileNumber: String?,
    @SerialName("target_role_id") val targetRoleId: Int?,
    @SerialName("secondary_target_role_id") val secondaryTargetRoleId: Int?,
    @SerialName("custom_target_role") val customTargetRole: String?,
    val college: String?,
    @SerialName("graduation_year") val graduationYear: Int?,
    val degree: String?,
    val branch: String?,
    @SerialName("experience_level") val experienceLevel: String?,
    @SerialName("preparation_goals") val preparationGoals: List<String>?,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean,
)

@Serializable
data class SkillCreateDto(val name: String, val proficiency: String? = null)

@Serializable
data class SkillDto(val id: Int, val name: String, val proficiency: String? = null)

@Serializable
data class AccountDeleteRequestDto(val password: String)
