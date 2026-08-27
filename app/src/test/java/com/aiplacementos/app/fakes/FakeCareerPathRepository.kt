package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.CareerPathDto
import com.aiplacementos.app.data.remote.CareerPathProjectDto
import com.aiplacementos.app.data.remote.CareerPathSummaryDto
import com.aiplacementos.app.data.remote.RoleCatalogEntryDto
import com.aiplacementos.app.data.remote.SkillItemDto
import com.aiplacementos.app.domain.repository.CareerPathRepository

/** Suspend-lambda pattern (not Result<T>) -- matches FakePlacementPathRepository's style,
 * since several Career Path flows (regenerate, set-current, apply-jd) need call-count- or
 * argument-dependent behavior across a single test, not just one fixed response. */
class FakeCareerPathRepository : CareerPathRepository {
    var roleCatalogResult: (suspend () -> List<RoleCatalogEntryDto>)? = null
    var roleCategoriesResult: (suspend () -> List<String>)? = null
    var generateResult: (suspend (Int?, String?) -> CareerPathDto)? = null
    var allResult: (suspend () -> List<CareerPathSummaryDto>)? = null
    var currentResult: (suspend () -> CareerPathDto?)? = null
    var pathResult: (suspend (Int) -> CareerPathDto)? = null
    var setCurrentResult: (suspend (Int) -> CareerPathDto)? = null
    var refreshResult: (suspend (Int) -> CareerPathDto)? = null
    var skillsResult: (suspend (Int) -> List<SkillItemDto>)? = null
    var projectsResult: (suspend (Int) -> List<CareerPathProjectDto>)? = null
    var practiceResult: (suspend (Int) -> Int)? = null
    var markSkillItemResult: (suspend (Int, String) -> SkillItemDto)? = null
    var markProjectResult: (suspend (Int, String) -> CareerPathProjectDto)? = null
    var applyJdResult: (suspend (Int, Int) -> CareerPathDto)? = null

    var lastGenerateRoleId: Int? = null
    var lastGenerateCustomRole: String? = null
    var lastSetCurrentPathId: Int? = null
    var lastRefreshPathId: Int? = null
    var lastPracticedItemId: Int? = null
    var lastMarkedSkillItemId: Int? = null
    var lastMarkedProjectId: Int? = null
    var lastAppliedJdId: Int? = null

    override suspend fun getRoleCatalog(category: String?, query: String?): List<RoleCatalogEntryDto> {
        val fn = requireNotNull(roleCatalogResult) { "roleCatalogResult not configured" }
        return fn()
    }

    override suspend fun getRoleCategories(): List<String> {
        val fn = requireNotNull(roleCategoriesResult) { "roleCategoriesResult not configured" }
        return fn()
    }

    override suspend fun generate(roleId: Int?, customRole: String?): CareerPathDto {
        lastGenerateRoleId = roleId
        lastGenerateCustomRole = customRole
        val fn = requireNotNull(generateResult) { "generateResult not configured" }
        return fn(roleId, customRole)
    }

    override suspend fun getAll(): List<CareerPathSummaryDto> {
        val fn = requireNotNull(allResult) { "allResult not configured" }
        return fn()
    }

    override suspend fun getCurrent(): CareerPathDto? {
        val fn = requireNotNull(currentResult) { "currentResult not configured" }
        return fn()
    }

    override suspend fun getPath(pathId: Int): CareerPathDto {
        val fn = requireNotNull(pathResult) { "pathResult not configured" }
        return fn(pathId)
    }

    override suspend fun setCurrent(pathId: Int): CareerPathDto {
        lastSetCurrentPathId = pathId
        val fn = requireNotNull(setCurrentResult) { "setCurrentResult not configured" }
        return fn(pathId)
    }

    override suspend fun refresh(pathId: Int): CareerPathDto {
        lastRefreshPathId = pathId
        val fn = requireNotNull(refreshResult) { "refreshResult not configured" }
        return fn(pathId)
    }

    override suspend fun getSkills(pathId: Int): List<SkillItemDto> {
        val fn = requireNotNull(skillsResult) { "skillsResult not configured" }
        return fn(pathId)
    }

    override suspend fun getProjects(pathId: Int): List<CareerPathProjectDto> {
        val fn = requireNotNull(projectsResult) { "projectsResult not configured" }
        return fn(pathId)
    }

    override suspend fun practiceSkillItem(itemId: Int): Int {
        lastPracticedItemId = itemId
        val fn = requireNotNull(practiceResult) { "practiceResult not configured" }
        return fn(itemId)
    }

    override suspend fun markSkillItemStatus(itemId: Int, status: String): SkillItemDto {
        lastMarkedSkillItemId = itemId
        val fn = requireNotNull(markSkillItemResult) { "markSkillItemResult not configured" }
        return fn(itemId, status)
    }

    override suspend fun markProjectStatus(projectId: Int, status: String): CareerPathProjectDto {
        lastMarkedProjectId = projectId
        val fn = requireNotNull(markProjectResult) { "markProjectResult not configured" }
        return fn(projectId, status)
    }

    override suspend fun applyJd(pathId: Int, jdId: Int): CareerPathDto {
        lastAppliedJdId = jdId
        val fn = requireNotNull(applyJdResult) { "applyJdResult not configured" }
        return fn(pathId, jdId)
    }
}
