package com.aiplacementos.app.ui.atscheck

import org.junit.Assert.assertEquals
import org.junit.Test

class AtsScoreBandsTest {
    @Test
    fun `scoreBandLabel covers every backend machine key`() {
        assertEquals("Excellent", scoreBandLabel("excellent"))
        assertEquals("Very Good", scoreBandLabel("very_good"))
        assertEquals("Good", scoreBandLabel("good"))
        assertEquals("Needs Improvement", scoreBandLabel("needs_improvement"))
        assertEquals("Needs Attention", scoreBandLabel("needs_attention"))
    }

    @Test
    fun `atsCategoryLabel covers all 8 scoring categories`() {
        assertEquals("ATS Compatibility", atsCategoryLabel("parsing"))
        assertEquals("Structure", atsCategoryLabel("structure"))
        assertEquals("Content Quality", atsCategoryLabel("content"))
        assertEquals("Skills", atsCategoryLabel("skills"))
        assertEquals("Experience", atsCategoryLabel("experience"))
        assertEquals("Projects", atsCategoryLabel("projects"))
        assertEquals("Contact Info", atsCategoryLabel("contact"))
        assertEquals("Readability", atsCategoryLabel("readability"))
    }

    @Test
    fun `sectionLabel covers all 9 report sections`() {
        assertEquals("Summary", sectionLabel("summary"))
        assertEquals("Skills", sectionLabel("skills"))
        assertEquals("Experience", sectionLabel("experience"))
        assertEquals("Education", sectionLabel("education"))
        assertEquals("Projects", sectionLabel("projects"))
        assertEquals("Certifications", sectionLabel("certifications"))
        assertEquals("Achievements", sectionLabel("achievements"))
        assertEquals("Languages", sectionLabel("languages"))
        assertEquals("Contact Information", sectionLabel("contact"))
    }
}
