package com.aiplacementos.app.ui.jobhunt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The advanced filter set.
 *
 * Two rules are worth guarding here. Blank text must never become a filter -- sending
 * `keywords=""` would narrow a search to nothing while looking to the student like they had
 * typed something. And `usesPremiumFields` must track the backend's own gated list exactly:
 * it only labels controls, but a label that disagrees with the server would promise a free
 * user something the server then refuses.
 */
class JobFiltersTest {
    @Test
    fun `an empty filter set sends nothing at all`() {
        val request = JobFilters().toRequest()
        assertNull(request.keywords)
        assertNull(request.location)
        assertNull(request.workMode)
        assertNull(request.employmentType)
        assertNull(request.experienceLevel)
        assertNull(request.salaryMin)
        assertNull(request.maxDaysOld)
    }

    @Test
    fun `blank text is dropped rather than sent as an empty string`() {
        val request = JobFilters(keywords = "   ", location = "").toRequest()
        assertNull(request.keywords)
        assertNull(request.location)
    }

    @Test
    fun `every set field reaches the request`() {
        val request = JobFilters(
            keywords = "data engineer",
            location = "Bengaluru",
            workMode = "remote",
            employmentType = "internship",
            experienceLevel = "fresher",
            salaryMin = 600_000,
            maxDaysOld = 7,
        ).toRequest()
        assertEquals("data engineer", request.keywords)
        assertEquals("Bengaluru", request.location)
        assertEquals("remote", request.workMode)
        assertEquals("internship", request.employmentType)
        assertEquals("fresher", request.experienceLevel)
        assertEquals(600_000, request.salaryMin)
        assertEquals(7, request.maxDaysOld)
    }

    @Test
    fun `the active count reflects what the student actually chose`() {
        assertEquals(0, JobFilters().activeCount)
        assertEquals(0, JobFilters(keywords = "  ").activeCount)
        assertEquals(2, JobFilters(workMode = "remote", salaryMin = 500_000).activeCount)
    }

    @Test
    fun `the premium flag matches the backend's gated fields exactly`() {
        // Backend: _PREMIUM_FILTER_FIELDS = salary_min, employment_type, experience_level,
        // max_days_old. Keywords, location and work mode are free.
        assertTrue(JobFilters(salaryMin = 1).usesPremiumFields)
        assertTrue(JobFilters(employmentType = "internship").usesPremiumFields)
        assertTrue(JobFilters(experienceLevel = "fresher").usesPremiumFields)
        assertTrue(JobFilters(maxDaysOld = 7).usesPremiumFields)

        assertFalse(JobFilters(keywords = "python").usesPremiumFields)
        assertFalse(JobFilters(location = "Pune").usesPremiumFields)
        assertFalse(JobFilters(workMode = "remote").usesPremiumFields)
    }

    @Test
    fun `the premium flag is never used to block a request`() {
        // Entitlement is the server's decision. A premium filter set by a free user must still
        // be sent, so the server can answer with its own upgrade response rather than the
        // client quietly deciding on its behalf.
        val filters = JobFilters(salaryMin = 900_000)
        assertTrue(filters.usesPremiumFields)
        assertEquals(900_000, filters.toRequest().salaryMin)
    }
}
