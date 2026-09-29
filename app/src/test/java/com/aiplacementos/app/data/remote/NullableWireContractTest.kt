package com.aiplacementos.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-contract cover for the two places the backend legitimately sends null and the client
 * used to refuse to parse it.
 *
 * These decode real JSON strings with [ApiJson] -- the same instance NetworkModule hands to
 * Retrofit -- rather than constructing DTOs in Kotlin. That distinction is the entire point.
 * Both bugs these tests pin down were already "covered" by tests that built the DTO directly
 * from non-null Kotlin values, which type-checks happily and proves nothing about what happens
 * when a null actually arrives on the wire. The failure only exists during deserialization, so
 * only deserialization can catch it.
 *
 * Each contract is tested twice: once with nulls present, and once fully populated, so fixing
 * the null case cannot quietly regress the ordinary one.
 */
class NullableWireContractTest {

    // ----------------------------------------------------------------------------------
    // S1 -- an ungraded Recruiter Attack session
    // ----------------------------------------------------------------------------------

    /**
     * The exact body recruiter_attack_service.get_results returns when the AI grader fails:
     * feedback text, and every score deliberately left null rather than invented. Before the
     * DTO declared these nullable this threw, so the screen's "we couldn't grade this" branch
     * was unreachable and the student saw a load error instead of an explanation.
     */
    @Test
    fun `an ungraded recruiter attack result decodes with every score null`() {
        val body = """
            {
              "technical_defense": null,
              "project_ownership": null,
              "specificity": null,
              "communication": null,
              "overall_score": null,
              "feedback": "We couldn't generate detailed AI feedback for this session right now. You answered 4/4 questions -- try again later for a full evaluation.",
              "used_fallback": true
            }
        """.trimIndent()

        val results = ApiJson.decodeFromString<RecruiterAttackResultsDto>(body)

        assertNull(results.technicalDefense)
        assertNull(results.projectOwnership)
        assertNull(results.specificity)
        assertNull(results.communication)
        assertNull(results.overallScore)
        assertTrue(results.usedFallback)
        assertTrue(results.feedback.isNotBlank())
    }

    @Test
    fun `a graded recruiter attack result still decodes with real scores`() {
        val body = """
            {
              "technical_defense": 0.9,
              "project_ownership": 0.85,
              "specificity": 0.8,
              "communication": 0.75,
              "overall_score": 0.82,
              "feedback": "Strong, specific defense of the claim.",
              "used_fallback": false
            }
        """.trimIndent()

        val results = ApiJson.decodeFromString<RecruiterAttackResultsDto>(body)

        assertEquals(0.9, results.technicalDefense!!, 0.0001)
        assertEquals(0.85, results.projectOwnership!!, 0.0001)
        assertEquals(0.8, results.specificity!!, 0.0001)
        assertEquals(0.75, results.communication!!, 0.0001)
        assertEquals(0.82, results.overallScore!!, 0.0001)
        assertTrue(!results.usedFallback)
    }

    // ----------------------------------------------------------------------------------
    // S2 -- Career Path readiness for a student who hasn't done everything yet
    // ----------------------------------------------------------------------------------

    /**
     * What a brand-new student's Career Path actually returns: skills and projects measured,
     * the other three signals genuinely absent. `job_readiness` is a non-null nested field on
     * CareerPathDto, so before the fix this null took the whole Career Path response down with
     * it -- Career Path Home, Detail, Readiness, and the Home screen's career card.
     *
     * `contributing_signals` and `total_signals` are the denominator behind overall_percent.
     * They were sent by the backend and not modelled here, so ignoreUnknownKeys discarded them
     * and the screen could not say how much of the average was actually measured.
     */
    @Test
    fun `a new student's readiness decodes with the unmeasured signals still null`() {
        val body = """
            {
              "overall_percent": 12,
              "contributing_signals": 2,
              "total_signals": 5,
              "skills_percent": 10,
              "projects_percent": 0,
              "resume_percent": null,
              "practice_percent": null,
              "interview_percent": null,
              "top_focus_areas": ["SQL"],
              "is_estimate": false
            }
        """.trimIndent()

        val readiness = ApiJson.decodeFromString<JobReadinessDto>(body)

        assertNull(readiness.resumePercent)
        assertNull(readiness.practicePercent)
        assertNull(readiness.interviewPercent)
        // The measured signals must stay measured -- a real zero is not the same as no data.
        assertEquals(0, readiness.projectsPercent)
        assertEquals(10, readiness.skillsPercent)
        assertEquals(12, readiness.overallPercent)
        // The denominator and the nulls have to tell the same story: three signals absent
        // above, three missing from the denominator here.
        assertEquals(2, readiness.contributingSignals)
        assertEquals(5, readiness.totalSignals)
        assertEquals(3, readiness.totalSignals - readiness.contributingSignals)
    }

    @Test
    fun `a fully measured readiness still decodes with every percent present`() {
        val body = """
            {
              "overall_percent": 64,
              "skills_percent": 70,
              "projects_percent": 50,
              "resume_percent": 80,
              "practice_percent": 60,
              "interview_percent": 60,
              "top_focus_areas": ["SQL", "Airflow"],
              "is_estimate": true
            }
        """.trimIndent()

        val readiness = ApiJson.decodeFromString<JobReadinessDto>(body)

        assertEquals(80, readiness.resumePercent)
        assertEquals(60, readiness.practicePercent)
        assertEquals(60, readiness.interviewPercent)
        assertTrue(readiness.isEstimate)
        // This body omits the denominator, so the Kotlin defaults apply. They are the same
        // literals JobReadinessRead declares on the backend, which is the point: a response
        // that leaves the fields out lands on the value the server itself would have sent.
        assertEquals(5, readiness.contributingSignals)
        assertEquals(5, readiness.totalSignals)
    }

    /**
     * The nesting is what made S2 severe, so it is asserted directly: a null three levels down
     * must not fail the enclosing CareerPathDto that every Career Path screen depends on.
     */
    @Test
    fun `a career path carrying an unmeasured readiness decodes as a whole`() {
        val body = """
            {
              "id": 7,
              "role_family_key": "data_engineer",
              "display_role_name": "Data Engineer",
              "is_primary": true,
              "generated_via": "catalog",
              "role_overview": {
                "display_name": "Data Engineer",
                "category": "data",
                "short_explanation": "Builds and runs data pipelines.",
                "responsibilities": ["Build pipelines"],
                "core_skills": ["SQL", "Python"],
                "tools": ["Airflow"],
                "difficulty": "moderate",
                "entry_level_expectations": "One solid pipeline project.",
                "interview_focus": ["SQL"],
                "portfolio_expectations": "A public repo."
              },
              "phases": [],
              "skill_gaps": [],
              "interview_prep": [],
              "created_at": "2026-09-21T10:00:00",
              "updated_at": "2026-09-21T10:00:00",
              "job_readiness": {
                "overall_percent": 12,
                "skills_percent": 10,
                "projects_percent": 0,
                "resume_percent": null,
                "practice_percent": null,
                "interview_percent": null,
                "top_focus_areas": []
              }
            }
        """.trimIndent()

        val path = ApiJson.decodeFromString<CareerPathDto>(body)

        assertEquals(7, path.id)
        assertNull(path.jobReadiness.resumePercent)
        assertNull(path.jobReadiness.practicePercent)
        assertNull(path.jobReadiness.interviewPercent)
    }
}
