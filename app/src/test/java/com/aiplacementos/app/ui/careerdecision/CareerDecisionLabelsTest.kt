package com.aiplacementos.app.ui.careerdecision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F12-08. Career Decision Lab shows `evidence_coverage_percent` -- the share of a role's required
 * skills the student's evidence backs. Its paywall and its intro described that number as
 * "evidence-backed readiness", which names a different measurement: readiness in this product is
 * JobReadinessRead.overall_percent, a five-signal average over skills, projects, resume, interview
 * and assessments. This screen reads none of those last three.
 *
 * These assertions are about meaning, not about a banned word. Each one fails for a reason a
 * reader can state: the copy stops naming the metric it shows, or it starts claiming a measurement
 * the screen never took, or the card and the prose above it stop agreeing.
 */
class CareerDecisionLabelsTest {

    /** The two halves of what this number is: whose evidence, and how much of the role it covers. */
    private fun namesEvidenceCoverage(copy: String): Boolean =
        copy.contains("evidence", ignoreCase = true) && copy.contains("cover", ignoreCase = true)

    @Test
    fun `the paywall describes evidence coverage, which is what the screen behind it shows`() {
        assertTrue(
            "the paywall no longer names the metric a buyer would get: $CAREER_DECISION_LAB_PAYWALL_DESCRIPTION",
            namesEvidenceCoverage(CAREER_DECISION_LAB_PAYWALL_DESCRIPTION),
        )
    }

    @Test
    fun `the intro describes evidence coverage, which is the number rendered under it`() {
        assertTrue(
            "the intro no longer names the metric it introduces: $CAREER_DECISION_LAB_INTRO",
            namesEvidenceCoverage(CAREER_DECISION_LAB_INTRO),
        )
    }

    @Test
    fun `the card label and the prose that explains it agree on what the number is`() {
        assertTrue(namesEvidenceCoverage(EVIDENCE_COVERAGE_LABEL))
        // All three describe one measurement. If any one of them is reworded back to a different
        // metric, the screen starts saying two things about a single bar -- which is how the
        // paywall and this label drifted apart in the first place.
        assertTrue(namesEvidenceCoverage(CAREER_DECISION_LAB_PAYWALL_DESCRIPTION))
        assertTrue(namesEvidenceCoverage(CAREER_DECISION_LAB_INTRO))
    }

    @Test
    fun `neither string calls it readiness, which is a different metric on other screens`() {
        // Not a style rule. "Readiness" is live and correct elsewhere (Career Path's Job Readiness,
        // the ATS readiness score, job-hunt application readiness) and each of those is a genuine
        // five-signal or per-listing measurement. Using it here would point a student at the wrong
        // one.
        for (copy in listOf(CAREER_DECISION_LAB_PAYWALL_DESCRIPTION, CAREER_DECISION_LAB_INTRO, EVIDENCE_COVERAGE_LABEL)) {
            assertFalse("still calls evidence coverage readiness: $copy", copy.contains("readiness", ignoreCase = true))
            assertFalse("still claims job-readiness: $copy", copy.contains("job-ready", ignoreCase = true))
        }
    }

    @Test
    fun `the copy does not claim to have measured the signals this screen never reads`() {
        // Evidence coverage is computed from skill evidence only. Readiness averages five signals,
        // three of which this screen has no access to -- so the copy must not imply it weighed them.
        for (copy in listOf(CAREER_DECISION_LAB_PAYWALL_DESCRIPTION, CAREER_DECISION_LAB_INTRO)) {
            assertFalse(copy.contains("interview", ignoreCase = true))
            assertFalse(copy.contains("resume", ignoreCase = true))
            assertFalse(copy.contains("assessment", ignoreCase = true))
        }
    }

    @Test
    fun `the intro still refuses to predict hiring outcomes`() {
        // The original copy's one genuinely good clause, kept verbatim through the rewrite.
        assertTrue(CAREER_DECISION_LAB_INTRO.contains("never a guess at your odds of getting hired"))
    }

    @Test
    fun `no string promises an outcome the product cannot deliver`() {
        for (copy in listOf(CAREER_DECISION_LAB_PAYWALL_DESCRIPTION, CAREER_DECISION_LAB_INTRO)) {
            assertFalse(copy.contains("guarantee", ignoreCase = true))
            assertFalse(copy.contains("will get hired", ignoreCase = true))
            assertFalse(copy.contains("recruiter-approved", ignoreCase = true))
        }
    }

    @Test
    fun `the copy uses the same vocabulary as the backend reason for the same number`() {
        // career_decision_service builds "N% of <role>'s required skills are backed by real
        // evidence." for this very figure. The card and the sentence underneath it should not read
        // as two different measurements, so both Android strings anchor on the same two nouns.
        for (copy in listOf(CAREER_DECISION_LAB_PAYWALL_DESCRIPTION, CAREER_DECISION_LAB_INTRO)) {
            assertTrue("does not mention required skills: $copy", copy.contains("required skills", ignoreCase = true))
            assertTrue("does not mention evidence: $copy", copy.contains("evidence", ignoreCase = true))
        }
    }

    @Test
    fun `the label is the exact text rendered on the comparison bar`() {
        assertEquals("Evidence coverage", EVIDENCE_COVERAGE_LABEL)
    }
}
