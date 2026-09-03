package com.aiplacementos.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface CareerTwinApi {
    @GET("career-twin")
    suspend fun getCareerTwin(): CareerTwinDto

    @GET("career-twin/readiness")
    suspend fun getReadiness(): CareerTwinReadinessDto

    @GET("career-twin/skills")
    suspend fun getSkills(): List<SkillTwinItemDto>

    @GET("career-twin/skills/{skill}")
    suspend fun getSkill(@Path("skill") skill: String): SkillTwinItemDto
}
