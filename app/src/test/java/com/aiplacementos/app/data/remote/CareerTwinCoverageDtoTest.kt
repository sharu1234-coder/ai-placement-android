package com.aiplacementos.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 2G on the wire: Career Twin's percentage arrives as `evidence_coverage_percent`, and
 * the three states it can be in stay distinguishable.
 *
 *     unmeasurable   the role defines no skills -- null
 *     measured zero  the calculation ran and the answer is 0
 *     measured value a real percentage
 *
 * These decode against [ApiJson], the same instance the app uses, because `coerceInputValues`
 * is deliberately OFF. That setting is the whole reason a nullable Int is meaningful here: with
 * it on, a `null` would be silently replaced by the type default, which for an Int is exactly
 * the 0 that Phase 2F removed and this phase must not let back in through the decoder.
 *
 * The old field name is covered too, because the rename is a deliberate BREAKING change and a
 * test that proves the break is worth more than one that quietly tolerates it.
 */
class CareerTwinCoverageDtoTest {

    private fun twin(coverage: String, trend: String = "null"): String = """
        {
          "has_role": true,
          "role_source": "career_path",
          "display_role_name": "Data Engineer",
          "evidence_coverage_percent": $coverage,
          "evidence_coverage_trend_percent": $trend
        }
    """.trimIndent()

    // ======================================================================================
    // CareerTwinDto -- the full screen payload
    // ======================================================================================

    @Test
    fun `a role with no defined skills decodes coverage as null, not zero`() {
        val decoded = ApiJson.decodeFromString<CareerTwinDto>(twin("null"))

        assertNull("unmeasurable must not become 0%", decoded.evidenceCoveragePercent)
        assertNull(decoded.evidenceCoverageTrendPercent)
    }

    @Test
    fun `a measured zero survives as a number`() {
        val decoded = ApiJson.decodeFromString<CareerTwinDto>(twin("0"))

        assertNotNull("a real 0 is a measurement and must stay one", decoded.evidenceCoveragePercent)
        assertEquals(0, decoded.evidenceCoveragePercent)
    }

    @Test
    fun `a real coverage figure decodes unchanged`() {
        val decoded = ApiJson.decodeFromString<CareerTwinDto>(twin("73", trend = "6"))

        assertEquals(73, decoded.evidenceCoveragePercent)
        assertEquals(6, decoded.evidenceCoverageTrendPercent)
    }

    @Test
    fun `a negative trend decodes as a negative number rather than being clamped`() {
        // Coverage can genuinely fall -- evidence goes stale. Swallowing the sign would make
        // the trend line only ever tell good news, which is its own kind of dishonesty.
        val decoded = ApiJson.decodeFromString<CareerTwinDto>(twin("40", trend = "-5"))

        assertEquals(-5, decoded.evidenceCoverageTrendPercent)
    }

    @Test
    fun `the old readiness_percent field no longer populates coverage`() {
        // Phase 2G is a deliberate breaking rename, and this pins the break. `ignoreUnknownKeys`
        // means a server still sending the old key is ignored rather than crashing the screen --
        // so the failure mode is a visible "not enough data yet", never a wrong number presented
        // as a right one. That is the correct way for this mismatch to fail.
        val body = """
            {
              "has_role": true,
              "role_source": "career_path",
              "readiness_percent": 73
            }
        """.trimIndent()

        val decoded = ApiJson.decodeFromString<CareerTwinDto>(body)

        assertNull(decoded.evidenceCoveragePercent)
    }

    // ======================================================================================
    // CareerTwinReadinessDto -- the trimmed Home-widget projection
    // ======================================================================================

    @Test
    fun `the home projection decodes null coverage as null`() {
        val body = """{ "has_role": true, "evidence_coverage_percent": null }"""

        val decoded = ApiJson.decodeFromString<CareerTwinReadinessDto>(body)

        assertNull(decoded.evidenceCoveragePercent)
    }

    @Test
    fun `the home projection decodes a measured zero as zero`() {
        val body = """{ "has_role": true, "evidence_coverage_percent": 0 }"""

        val decoded = ApiJson.decodeFromString<CareerTwinReadinessDto>(body)

        assertEquals(0, decoded.evidenceCoveragePercent)
    }

    @Test
    fun `the home projection decodes a real value`() {
        val body = """{ "has_role": true, "evidence_coverage_percent": 73 }"""

        val decoded = ApiJson.decodeFromString<CareerTwinReadinessDto>(body)

        assertEquals(73, decoded.evidenceCoveragePercent)
    }

    // ======================================================================================
    // Career Strategy -- the same number, carried through
    // ======================================================================================

    @Test
    fun `the strategy screen decodes coverage and its two companions`() {
        val body = """
            {
              "is_premium": false, "state": "FOCUS_ON_TARGET_ROLE", "state_reason": "x",
              "strategy_version": 1, "has_role": true,
              "evidence_coverage_percent": 64,
              "evidence_coverage_is_estimate": true,
              "evidence_coverage_reasons": ["3 verified, 1 strong out of 6."]
            }
        """.trimIndent()

        val decoded = ApiJson.decodeFromString<CareerStrategyDto>(body)

        assertEquals(64, decoded.evidenceCoveragePercent)
        assertEquals(true, decoded.evidenceCoverageIsEstimate)
        assertEquals(1, decoded.evidenceCoverageReasons.size)
    }

    @Test
    fun `an unmeasurable role reaches the strategy screen as null, not zero`() {
        val body = """
            {
              "is_premium": false, "state": "FOCUS_ON_TARGET_ROLE", "state_reason": "x",
              "strategy_version": 1, "has_role": true,
              "evidence_coverage_percent": null
            }
        """.trimIndent()

        val decoded = ApiJson.decodeFromString<CareerStrategyDto>(body)

        assertNull(decoded.evidenceCoveragePercent)
        assertEquals(false, decoded.evidenceCoverageIsEstimate)
    }

    @Test
    fun `a job advert keeps its own apply-readiness field, which is a different metric`() {
        // The one place "readiness" is still the right word on this screen: job_hunt's
        // apply-readiness for a single advert, a mean over judged dimensions. Phase 2G left it
        // alone deliberately, and this test exists so a future sweep does not "finish the job"
        // by renaming it into Career Twin's vocabulary.
        val body = """
            {
              "listing_id": 1, "job_title": "Data Engineer", "decision": "APPLY_NOW",
              "headline": "x",
              "readiness_percent": 80, "readiness_band": "strong"
            }
        """.trimIndent()

        val decoded = ApiJson.decodeFromString<JobStrategyDto>(body)

        assertEquals(80, decoded.readinessPercent)
        assertEquals("strong", decoded.readinessBand)
    }
}
