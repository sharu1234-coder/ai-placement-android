package com.aiplacementos.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The payloads Home is built from, decoded as they actually arrive.
 *
 * Mocked Kotlin objects cannot catch the failure this file exists for. `ApiJson` has
 * `coerceInputValues` deliberately OFF, and that single setting is what keeps a `null` from
 * becoming a type default on the way in -- which for an Int is exactly the 0 that Phases 2F and
 * 2G spent two passes removing. A hand-built DTO in a ViewModel test would render the same
 * assertions green whether or not that setting survived.
 *
 * Each section below decodes the three states Home has to tell apart:
 *
 *     null   nothing has been measured
 *     0      something was measured and the answer was zero
 *     n      a real reading
 */
class HomeSpineDtoTest {

    // ======================================================================================
    // The target, and its application
    // ======================================================================================

    private fun goal(status: String, applicationStatus: String): String = """
        {
          "id": 1, "title": "Data Engineer", "company": "Contoso", "location": "Hyderabad",
          "status": "$status", "is_analyzed": true, "preparation_state": "in_progress",
          "application_status": $applicationStatus,
          "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-03-01T00:00:00Z"
        }
    """.trimIndent()

    @Test
    fun `a goal with no linked application decodes its status as null`() {
        val decoded = ApiJson.decodeFromString<CareerGoalListItemDto>(goal("active", "null"))

        assertNull("not linked must stay null -- it is not 'has not applied'", decoded.applicationStatus)
        assertEquals("active", decoded.status)
    }

    @Test
    fun `every application stage decodes unchanged`() {
        for (stage in listOf("saved", "applied", "assessment", "interview", "offer", "rejected", "closed")) {
            val decoded = ApiJson.decodeFromString<CareerGoalListItemDto>(goal("active", "\"$stage\""))

            assertEquals(stage, decoded.applicationStatus)
        }
    }

    @Test
    fun `an archived goal decodes its real status rather than defaulting to active`() {
        val decoded = ApiJson.decodeFromString<CareerGoalListItemDto>(goal("archived", "null"))

        assertEquals("archived", decoded.status)
    }

    @Test
    fun `a goal that omits location still decodes`() {
        val body = goal("active", "null").replace("\"location\": \"Hyderabad\",", "")

        val decoded = ApiJson.decodeFromString<CareerGoalListItemDto>(body)

        assertNull(decoded.location)
        assertEquals("Data Engineer", decoded.title)
    }

    // ======================================================================================
    // Evidence coverage
    // ======================================================================================

    private fun twin(coverage: String): String = """
        {
          "has_role": true, "role_source": "career_path",
          "evidence_coverage_percent": $coverage
        }
    """.trimIndent()

    @Test
    fun `evidence coverage decodes null as null`() {
        assertNull(ApiJson.decodeFromString<CareerTwinDto>(twin("null")).evidenceCoveragePercent)
    }

    @Test
    fun `evidence coverage decodes a measured zero as zero`() {
        val decoded = ApiJson.decodeFromString<CareerTwinDto>(twin("0"))

        assertNotNull(decoded.evidenceCoveragePercent)
        assertEquals(0, decoded.evidenceCoveragePercent)
    }

    @Test
    fun `evidence coverage decodes a real reading`() {
        assertEquals(73, ApiJson.decodeFromString<CareerTwinDto>(twin("73")).evidenceCoveragePercent)
    }

    // ======================================================================================
    // Preparation
    // ======================================================================================

    private fun path(preparation: String, completed: Int, total: Int): String = """
        {
          "id": 1, "role_family_key": "data_engineer", "display_role_name": "Data Engineer",
          "is_primary": true, "preparation_percent": $preparation,
          "completed_items": $completed, "total_items": $total, "generated_via": "catalog",
          "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"
        }
    """.trimIndent()

    @Test
    fun `preparation with no checklist decodes as null, not zero`() {
        val decoded = ApiJson.decodeFromString<CareerPathSummaryDto>(path("null", 0, 0))

        assertNull(decoded.preparationPercent)
        assertEquals(0, decoded.totalItems)
    }

    @Test
    fun `preparation with a checklist and nothing done decodes as a measured zero`() {
        val decoded = ApiJson.decodeFromString<CareerPathSummaryDto>(path("0", 0, 10))

        assertNotNull(decoded.preparationPercent)
        assertEquals(0, decoded.preparationPercent)
        assertEquals(10, decoded.totalItems)
    }

    @Test
    fun `preparation in progress decodes its percentage and counts`() {
        val decoded = ApiJson.decodeFromString<CareerPathSummaryDto>(path("60", 6, 10))

        assertEquals(60, decoded.preparationPercent)
        assertEquals(6, decoded.completedItems)
    }

    // ======================================================================================
    // Performance
    // ======================================================================================

    private fun dashboard(score: String): String = """
        {
          "performance_score": $score, "completed_assessments": 0, "completed_interviews": 0,
          "roadmap_tasks_done": 0, "roadmap_tasks_total": 0, "top_weaknesses": [], "trend": []
        }
    """.trimIndent()

    @Test
    fun `performance decodes null as null`() {
        assertNull(ApiJson.decodeFromString<ProgressDashboardDto>(dashboard("null")).performanceScore)
    }

    @Test
    fun `performance decodes a genuine zero as zero`() {
        val decoded = ApiJson.decodeFromString<ProgressDashboardDto>(dashboard("0.0"))

        assertNotNull(decoded.performanceScore)
        assertEquals(0.0, decoded.performanceScore!!, 0.0001)
    }

    @Test
    fun `performance decodes its 0 to 1 scale unchanged`() {
        val decoded = ApiJson.decodeFromString<ProgressDashboardDto>(dashboard("0.68"))

        assertEquals(0.68, decoded.performanceScore!!, 0.0001)
    }

    // ======================================================================================
    // Next Best Action
    // ======================================================================================

    @Test
    fun `an available next action decodes its title and destination`() {
        val body = """
            {
              "has_recommendation": true, "title": "Practice technical interview",
              "action_type": "practice", "estimated_minutes": 20,
              "destination": "interview", "reasons": ["SQL was flagged in your last interview."]
            }
        """.trimIndent()

        val decoded = ApiJson.decodeFromString<RecommendedActionDto>(body)

        assertEquals("Practice technical interview", decoded.title)
        assertEquals("interview", decoded.destination)
        assertEquals(1, decoded.reasons.size)
    }

    @Test
    fun `an action with nowhere to send the student decodes a null destination`() {
        // Home must render no button rather than one that goes nowhere.
        val body = """
            {
              "has_recommendation": true, "title": "Keep going", "action_type": "maintenance"
            }
        """.trimIndent()

        val decoded = ApiJson.decodeFromString<RecommendedActionDto>(body)

        assertNull(decoded.destination)
    }

    @Test
    fun `no recommendation decodes as has_recommendation false`() {
        val body = """
            {
              "has_recommendation": false, "title": "Select a target role to get a personalized next action.",
              "action_type": "maintenance"
            }
        """.trimIndent()

        val decoded = ApiJson.decodeFromString<RecommendedActionDto>(body)

        assertEquals(false, decoded.hasRecommendation)
    }

    // ======================================================================================
    // Today's Mission
    // ======================================================================================

    @Test
    fun `today's mission decodes its status and estimate`() {
        val body = """
            {
              "id": 5, "mission_date": "2026-03-01", "tier": "repeated_weakness",
              "title": "Practice SQL joins", "action_type": "practice",
              "estimated_minutes": 15, "status": "not_started"
            }
        """.trimIndent()

        val decoded = ApiJson.decodeFromString<MissionDto>(body)

        assertEquals("not_started", decoded.status)
        assertEquals(15, decoded.estimatedMinutes)
    }

    @Test
    fun `a completed mission decodes its completion rather than being inferred`() {
        val body = """
            {
              "id": 5, "mission_date": "2026-03-01", "title": "Practice SQL joins",
              "action_type": "practice", "status": "completed",
              "completed_at": "2026-03-01T10:00:00Z"
            }
        """.trimIndent()

        val decoded = ApiJson.decodeFromString<MissionDto>(body)

        assertEquals("completed", decoded.status)
        assertNotNull(decoded.completedAt)
    }
}
