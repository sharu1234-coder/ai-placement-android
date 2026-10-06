package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.CareerPathProjectDto
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.data.remote.SkillItemDto

/** Deliberately independent of PlacementPathRepository -- Career Path never reads or writes
 * Placement Path/Roadmap data, and vice versa. */
interface CareerPathRepository {
    suspend fun getRoleCatalog(category: String? = null, query: String? = null): List<RoleCatalogEntryDto>
    suspend fun getRoleCategories(): List<String>
    suspend fun generate(roleId: Int?, customRole: String?): CareerPathDto
    suspend fun getAll(): List<CareerPathSummaryDto>
    suspend fun getCurrent(): CareerPathDto?
    suspend fun getPath(pathId: Int): CareerPathDto
    suspend fun setCurrent(pathId: Int): CareerPathDto
    suspend fun refresh(
        pathId: Int,
        idempotencyKey: String? = null,
    ): CareerPathDto
    suspend fun getProjects(pathId: Int): List<CareerPathProjectDto>
    suspend fun practiceSkillItem(itemId: Int): Int // returns blueprintId
    suspend fun markSkillItemStatus(itemId: Int, status: String): SkillItemDto
    suspend fun markProjectStatus(projectId: Int, status: String): CareerPathProjectDto
    suspend fun applyJd(pathId: Int, jdId: Int): CareerPathDto
}
