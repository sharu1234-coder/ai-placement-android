package com.aiplacementos.app.ui.careerpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertEquals("On Your Resume", skillGapStatusLabel("strong"))
        assertEquals("Needs Improvement", skillGapStatusLabel("needs_improvement"))
        assertEquals("Missing", skillGapStatusLabel("missing"))
        assertEquals("Recommended", skillGapStatusLabel("recommended"))
    }

    @Test
    fun `the resume-match status is never labelled Strong, which means something stricter elsewhere`() {
        // Career Path's "strong" wire value means only "this skill is on your resume and
        // nothing flagged it weak" -- a claim. The Skill Passport and Career Twin also show a
        // "Strong" label, but theirs comes from the Evidence Engine and requires two or more
        // sources with at least one the student did not assert about themselves. Rendering
        // both as "Strong" let the weaker claim borrow the stronger one's credibility on the
        // same student's screens. This asserts the words stay distinct, so a future tidy-up
        // that "restores" the shorter label has to fail here first and read this comment.
        assertNotEquals("Strong", skillGapStatusLabel("strong"))
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

    @Test
    fun `readinessCoverageLabel states the real denominator`() {
        assertEquals("Measured from 2 of 5 signals", readinessCoverageLabel(2, 5))
        assertEquals("Measured from 4 of 5 signals", readinessCoverageLabel(4, 5))
    }

    @Test
    fun `readinessCoverageLabel still says so when every signal was measured`() {
        // Going silent at full coverage would make the disclosure itself a bad-news signal,
        // and leave a student unable to tell "all five" from "we stopped telling you".
        assertEquals("Measured from all 5 signals", readinessCoverageLabel(5, 5))
    }

    @Test
    fun `readinessCoverageLabel claims no coverage it does not have`() {
        assertEquals("No signals measured yet", readinessCoverageLabel(0, 5))
        assertEquals("No signals measured yet", readinessCoverageLabel(0, 0))
    }

    @Test
    fun `readinessCoverageLabel never restates the average as a confidence`() {
        // "78% ready" is an average of what was measured. A label reading "78% confident"
        // would turn that into a claim about the result, which is not what was computed.
        listOf(
            readinessCoverageLabel(2, 5),
            readinessCoverageLabel(5, 5),
            readinessCoverageLabel(0, 5),
        ).forEach { label ->
            assertFalse(label, label.contains("%"))
            assertFalse(label, label.contains("confiden", ignoreCase = true))
        }
    }
}
