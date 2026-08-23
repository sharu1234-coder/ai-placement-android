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

@Serializable
data class RoleSelectRequestDto(@SerialName("role_id") val roleId: Int)

@Serializable
data class ProfileUpsertDto(
    @SerialName("full_name") val fullName: String? = null,
    val college: String? = null,
    @SerialName("graduation_year") val graduationYear: Int? = null,
)

@Serializable
data class ProfileDto(
    val id: Int,
    @SerialName("full_name") val fullName: String?,
    @SerialName("target_role_id") val targetRoleId: Int?,
    val college: String?,
    @SerialName("graduation_year") val graduationYear: Int?,
)

@Serializable
data class SkillCreateDto(val name: String, val proficiency: String? = null)

@Serializable
data class SkillDto(val id: Int, val name: String, val proficiency: String? = null)

@Serializable
data class AccountDeleteRequestDto(val password: String)
