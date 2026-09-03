package com.aiplacementos.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface SkillPassportApi {
    @GET("skill-passport")
    suspend fun listSkills(): List<SkillPassportItemDto>

    @GET("skill-passport/{skill}")
    suspend fun getSkill(@Path("skill") skill: String): SkillPassportItemDto
}
