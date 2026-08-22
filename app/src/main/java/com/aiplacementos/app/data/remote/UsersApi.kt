package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface UsersApi {
    @GET("roles")
    suspend fun getRoles(): List<RoleDto>

    @GET("users/me")
    suspend fun getMyProfile(): ProfileDto

    @PUT("users/me")
    suspend fun updateMyProfile(@Body request: ProfileUpsertDto): ProfileDto

    @POST("users/me/role")
    suspend fun selectRole(@Body request: RoleSelectRequestDto): ProfileDto

    @POST("users/me/skills")
    suspend fun addSkill(@Body request: SkillCreateDto): SkillDto

    @GET("users/me/skills")
    suspend fun listSkills(): List<SkillDto>
}
