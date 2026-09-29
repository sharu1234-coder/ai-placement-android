package com.aiplacementos.app.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire contract for the two fields this app now reasons about.
 *
 * Both are new keys on endpoints that already shipped, so each one is tested twice: once with
 * the key present, and once against a body that predates it. The second case is the one worth
 * having -- a defaulted field that was actually declared non-null would crash the screen for
 * anyone running against an older backend, and a @SerialName typo would silently read as "no
 * extra actions" and "nothing unread", which are exactly the two states that mean "all is
 * well". A quiet false reassurance is the failure mode these tests exist to catch.
 */
class CareerIntelligenceDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an action carries its confidence, destination and the rest of the plan`() {
        val body = """
            {
              "has_recommendation": true,
              "tier": "critical_role_gap",
              "title": "Learn SQL fundamentals",
              "action_type": "learn",
              "estimated_minutes": 15,
              "reasons": ["SQL is a core skill for this role."],
              "skill": "SQL",
              "confidence": "low",
              "destination": "roadmap",
              "additional_actions": [
                {
                  "has_recommendation": true,
                  "tier": "interview_weakness",
                  "title": "Practice interview answers on clarity",
                  "action_type": "practice",
                  "confidence": "moderate",
                  "destination": "interview"
                }
              ],
              "locked_sections": []
            }
        """.trimIndent()

        val action = json.decodeFromString<RecommendedActionDto>(body)

        assertEquals("low", action.confidence)
        assertEquals("roadmap", action.destination)
        assertEquals(1, action.additionalActions.size)
        assertEquals("interview", action.additionalActions.first().destination)
        assertTrue(action.lockedSections.isEmpty())
    }

    @Test
    fun `a free student's body names the locked section`() {
        val body = """
            {
              "has_recommendation": true,
              "title": "Learn SQL fundamentals",
              "action_type": "learn",
              "additional_actions": [],
              "locked_sections": ["additional_actions"]
            }
        """.trimIndent()

        val action = json.decodeFromString<RecommendedActionDto>(body)

        assertTrue(action.additionalActions.isEmpty())
        assertEquals(listOf(LOCKED_SECTION_ADDITIONAL_ACTIONS), action.lockedSections)
    }

    @Test
    fun `an older backend's action body still parses`() {
        val body = """
            {
              "has_recommendation": true,
              "tier": "maintenance",
              "title": "Keep SQL sharp",
              "action_type": "maintenance",
              "estimated_minutes": 10,
              "reasons": [],
              "skill": "SQL"
            }
        """.trimIndent()

        val action = json.decodeFromString<RecommendedActionDto>(body)

        assertNull(action.confidence)
        assertNull(action.destination)
        assertTrue(action.additionalActions.isEmpty())
        assertTrue(action.lockedSections.isEmpty())
    }

    @Test
    fun `the twin reports which evidence sources went unread`() {
        val body = """
            {
              "has_role": true,
              "role_source": "career_path",
              "evidence_coverage_percent": 42,
              "unavailable_sources": ["external", "resume"]
            }
        """.trimIndent()

        val twin = json.decodeFromString<CareerTwinDto>(body)

        assertEquals(listOf("external", "resume"), twin.unavailableSources)
    }

    @Test
    fun `an older twin body reads as nothing unread rather than failing`() {
        val body = """
            {
              "has_role": true,
              "role_source": "career_path",
              "evidence_coverage_percent": 42
            }
        """.trimIndent()

        val twin = json.decodeFromString<CareerTwinDto>(body)

        assertTrue(twin.unavailableSources.isEmpty())
    }

    @Test
    fun `the readiness projection carries the unread sources too`() {
        val body = """
            {
              "has_role": true,
              "evidence_coverage_percent": 42,
              "unavailable_sources": ["external"]
            }
        """.trimIndent()

        val readiness = json.decodeFromString<CareerTwinReadinessDto>(body)

        assertEquals(listOf("external"), readiness.unavailableSources)
    }
}
