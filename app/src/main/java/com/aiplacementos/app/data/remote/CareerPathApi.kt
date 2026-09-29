package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface CareerPathApi {
    @GET("career-path/role-catalog")
    suspend fun getRoleCatalog(
        @Query("category") category: String? = null,
        @Query("q") query: String? = null,
    ): List<RoleCatalogEntryDto>

    @GET("career-path/role-categories")
    suspend fun getRoleCategories(): List<String>

    @POST("career-path/generate")
    suspend fun generate(@Body request: SelectCareerRoleRequestDto): CareerPathDto

    @GET("career-path/all")
    suspend fun getAll(): List<CareerPathSummaryDto>

    /** Empty until the student picks a role -- see [NullableBody]. */
    @GET("career-path/current")
    suspend fun getCurrent(): NullableBody<CareerPathDto>

    @GET("career-path/{pathId}")
    suspend fun getPath(@Path("pathId") pathId: Int): CareerPathDto

    @PUT("career-path/{pathId}/set-current")
    suspend fun setCurrent(@Path("pathId") pathId: Int): CareerPathDto

    @POST("career-path/{pathId}/refresh")
    suspend fun refresh(@Path("pathId") pathId: Int): CareerPathDto

    @GET("career-path/{pathId}/projects")
    suspend fun getProjects(@Path("pathId") pathId: Int): List<CareerPathProjectDto>

    @POST("career-path/skill-items/{itemId}/practice")
    suspend fun practiceSkillItem(@Path("itemId") itemId: Int): PracticeHandoffDto

    @PATCH("career-path/skill-items/{itemId}")
    suspend fun updateSkillItem(
        @Path("itemId") itemId: Int,
        @Body request: UpdateSkillItemRequestDto,
    ): SkillItemDto

    @PATCH("career-path/projects/{projectId}")
    suspend fun updateProject(
        @Path("projectId") projectId: Int,
        @Body request: UpdateProjectRequestDto,
    ): CareerPathProjectDto

    @POST("career-path/{pathId}/apply-jd/{jdId}")
    suspend fun applyJd(
        @Path("pathId") pathId: Int,
        @Path("jdId") jdId: Int,
    ): CareerPathDto
}
