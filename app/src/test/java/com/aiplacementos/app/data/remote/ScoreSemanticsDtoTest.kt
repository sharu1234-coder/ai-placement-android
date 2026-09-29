package com.aiplacementos.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 2F on the wire: the renamed percentages decode, and an unmeasurable one arrives as null
 * rather than as a confident zero.
 *
 * These are real decode tests against [ApiJson] -- the same instance the app uses -- because
 * `coerceInputValues` is deliberately OFF. That setting is what makes a nullable field
 * meaningful here: it means a `null` in the payload stays null instead of being quietly
 * replaced by a type default, which for an Int would be exactly the 0 this phase removed.
 *
 * The distinction every test below is built around:
 *
 *     measured zero   the server computed the number and it is 0
 *     unmeasurable    the server could not compute it, and says so with null
 */
class ScoreSemanticsDtoTest {

    // ======================================================================================
    // Career Path summary -- PREPARATION PROGRESS
    // ======================================================================================

    private fun pathSummary(preparation: String): String = """
        {
          "id": 1, "role_family_key": "data_analyst", "display_role_name": "Data Analyst",
          "is_primary": true, "preparation_percent": $preparation,
          "completed_items": 0, "total_items": 0, "generated_via": "catalog",
          "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"
        }
    """.trimIndent()

    @Test
    fun `a path with no checklist items decodes preparation as null, not zero`() {
        val summary = ApiJson.decodeFromString<CareerPathSummaryDto>(pathSummary("null"))

        assertNull("nothing to divide by must not become 0%", summary.preparationPercent)
        assertEquals(0, summary.totalItems)
    }

    @Test
    fun `a path with items and none complete decodes as a measured zero`() {
        val body = pathSummary("0").replace("\"total_items\": 0", "\"total_items\": 10")

        val summary = ApiJson.decodeFromString<CareerPathSummaryDto>(body)

        assertNotNull("a real 0 must survive as a number", summary.preparationPercent)
        assertEquals(0, summary.preparationPercent)
    }

    @Test
    fun `a partially complete path decodes its percentage`() {
        val body = pathSummary("30")
            .replace("\"completed_items\": 0", "\"completed_items\": 3")
            .replace("\"total_items\": 0", "\"total_items\": 10")

        val summary = ApiJson.decodeFromString<CareerPathSummaryDto>(body)

        assertEquals(30, summary.preparationPercent)
    }

    @Test
    fun `a summary that omits preparation entirely still decodes`() {
        // An older backend, or one that drops the key rather than sending null. Without the
        // declared default this throws and the saved-roles screen fails to load.
        val body = pathSummary("null").replace("\"preparation_percent\": null,", "")

        val summary = ApiJson.decodeFromString<CareerPathSummaryDto>(body)

        assertNull(summary.preparationPercent)
    }

    // ======================================================================================
    // Career Decision -- EVIDENCE COVERAGE
    // ======================================================================================

    private fun decisionRole(coverage: String, classification: String, hours: String): String = """
        {
          "role_id": 1, "display_role_name": "Data Analyst",
          "evidence_coverage_percent": $coverage,
          "classification": $classification,
          "biggest_gap_skill": null,
          "estimated_hours_to_strengthen": $hours,
          "is_estimate": true, "estimated_weeks": null, "reasons": []
        }
    """.trimIndent()

    @Test
    fun `a role with no defined skills decodes every unmeasurable field as null`() {
        val role = ApiJson.decodeFromString<CareerDecisionRoleDto>(
            decisionRole("null", "null", "null"),
        )

        assertNull(role.evidenceCoveragePercent)
        assertNull("an unmeasurable role must not be classified", role.classification)
        assertNull("nor given an hours estimate", role.estimatedHoursToStrengthen)
    }

    @Test
    fun `a measured role with zero coverage keeps its classification and its zero`() {
        val role = ApiJson.decodeFromString<CareerDecisionRoleDto>(
            decisionRole("0", "\"stretch\"", "40.0"),
        )

        assertEquals(0, role.evidenceCoveragePercent)
        assertNotNull(role.evidenceCoveragePercent)
        assertEquals("stretch", role.classification)
    }

    @Test
    fun `a strong role decodes normally`() {
        val role = ApiJson.decodeFromString<CareerDecisionRoleDto>(
            decisionRole("80", "\"best_fit\"", "5.0"),
        )

        assertEquals(80, role.evidenceCoveragePercent)
        assertEquals("best_fit", role.classification)
    }

    // ======================================================================================
    // Progress -- PERFORMANCE
    // ======================================================================================

    private fun dashboard(score: String): String = """
        {
          "performance_score": $score, "completed_assessments": 0, "completed_interviews": 0,
          "roadmap_tasks_done": 0, "roadmap_tasks_total": 0, "top_weaknesses": [], "trend": []
        }
    """.trimIndent()

    @Test
    fun `no test and no interview decodes performance as null`() {
        val body = ApiJson.decodeFromString<ProgressDashboardDto>(dashboard("null"))

        assertNull("not measured is not a performance of zero", body.performanceScore)
    }

    @Test
    fun `a test actually scored zero decodes as a measured zero`() {
        val body = ApiJson.decodeFromString<ProgressDashboardDto>(dashboard("0.0"))

        assertNotNull(body.performanceScore)
        assertEquals(0.0, body.performanceScore!!, 0.0001)
    }

    @Test
    fun `a real performance decodes unchanged`() {
        val body = ApiJson.decodeFromString<ProgressDashboardDto>(dashboard("0.68"))

        assertEquals(0.68, body.performanceScore!!, 0.0001)
    }
}
