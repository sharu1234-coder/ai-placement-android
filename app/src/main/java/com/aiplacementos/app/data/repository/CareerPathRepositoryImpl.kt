package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.CareerPathApi
import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.CareerPathProjectDto
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.data.remote.SelectCareerRoleRequestDto
import com.aiplacementos.app.data.remote.SkillItemDto
import com.aiplacementos.app.data.remote.UpdateProjectRequestDto
import com.aiplacementos.app.data.remote.UpdateSkillItemRequestDto
import com.aiplacementos.app.domain.repository.CareerPathRepository

class CareerPathRepositoryImpl(private val careerPathApi: CareerPathApi) : CareerPathRepository {
    override suspend fun getRoleCatalog(category: String?, query: String?): List<RoleCatalogEntryDto> =
        careerPathApi.getRoleCatalog(category, query)

    override suspend fun getRoleCategories(): List<String> = careerPathApi.getRoleCategories()

    override suspend fun generate(roleId: Int?, customRole: String?): CareerPathDto =
        careerPathApi.generate(SelectCareerRoleRequestDto(roleId = roleId, customRole = customRole))

    override suspend fun getAll(): List<CareerPathSummaryDto> = careerPathApi.getAll()

    override suspend fun getCurrent(): CareerPathDto? = careerPathApi.getCurrent()

    override suspend fun getPath(pathId: Int): CareerPathDto = careerPathApi.getPath(pathId)

    override suspend fun setCurrent(pathId: Int): CareerPathDto = careerPathApi.setCurrent(pathId)

    override suspend fun refresh(pathId: Int): CareerPathDto = careerPathApi.refresh(pathId)

    override suspend fun getProjects(pathId: Int): List<CareerPathProjectDto> = careerPathApi.getProjects(pathId)

    override suspend fun practiceSkillItem(itemId: Int): Int = careerPathApi.practiceSkillItem(itemId).blueprintId

    override suspend fun markSkillItemStatus(itemId: Int, status: String): SkillItemDto =
        careerPathApi.updateSkillItem(itemId, UpdateSkillItemRequestDto(status = status))

    override suspend fun markProjectStatus(projectId: Int, status: String): CareerPathProjectDto =
        careerPathApi.updateProject(projectId, UpdateProjectRequestDto(status = status))

    override suspend fun applyJd(pathId: Int, jdId: Int): CareerPathDto = careerPathApi.applyJd(pathId, jdId)
}
