package com.aiplacementos.app.ui.careerpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareerPathLabelsTest {
    @Test
    fun `phaseLabel covers all 5 fixed phases`() {
        assertEquals("Foundations", phaseLabel("foundations"))
        assertEquals("Core Skills", phaseLabel("core_skills"))
        assertEquals("Projects", phaseLabel("projects"))
        assertEquals("Interview Preparation", phaseLabel("interview_prep"))
        assertEquals("Job Ready", phaseLabel("job_ready"))
    }

    @Test
    fun `statusLabel covers all 3 statuses`() {
        assertEquals("Completed", statusLabel("completed"))
        assertEquals("In Progress", statusLabel("in_progress"))
        assertEquals("Not Started", statusLabel("not_started"))
    }

    @Test
    fun `skillGapStatusLabel covers all 4 gap statuses`() {
        assertEquals("Strong", skillGapStatusLabel("strong"))
        assertEquals("Needs Improvement", skillGapStatusLabel("needs_improvement"))
        assertEquals("Missing", skillGapStatusLabel("missing"))
        assertEquals("Recommended", skillGapStatusLabel("recommended"))
    }

    @Test
    fun `interviewCategoryLabel covers all 5 interview categories`() {
        assertEquals("Technical", interviewCategoryLabel("technical"))
        assertEquals("HR", interviewCategoryLabel("hr"))
        assertEquals("Project-Based", interviewCategoryLabel("project"))
        assertEquals("Behavioral", interviewCategoryLabel("behavioral"))
        assertEquals("Role-Specific", interviewCategoryLabel("role_specific"))
    }

    @Test
    fun `generatedViaLabel discloses ai_generated and fallback tiers but not catalog`() {
        assertNull(generatedViaLabel("catalog"))
        assertEquals(true, generatedViaLabel("ai_generated")?.isNotBlank())
        assertEquals(true, generatedViaLabel("fallback")?.isNotBlank())
    }
}
