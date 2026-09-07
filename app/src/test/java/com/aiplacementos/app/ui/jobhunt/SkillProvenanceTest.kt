package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.data.remote.SkillProvenanceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client's half of the "claim vs. proof" rule.
 *
 * The backend decides which bucket a skill is in; these tests guard what the client does with
 * that verdict — that it never merges the two evidence groups, never invents a label for a
 * status it does not recognise, and orders the groups so a well-matched job does not open on
 * its gaps.
 */
private fun item(skill: String, status: String) = SkillProvenanceDto(skill = skill, status = status)

class GroupProvenanceTest {
    @Test
    fun `groups are ordered strongest ground first`() {
        val grouped = groupProvenance(
            listOf(
                item("kubernetes", "missing"),
                item("python", "verified_evidence"),
                item("sql", "on_resume"),
            )
        )
        assertEquals(listOf("verified_evidence", "on_resume", "missing"), grouped.map { it.first })
    }

    @Test
    fun `empty buckets are dropped rather than rendered as empty headings`() {
        val grouped = groupProvenance(listOf(item("python", "verified_evidence")))
        assertEquals(1, grouped.size)
        assertEquals("verified_evidence", grouped.first().first)
    }

    @Test
    fun `nothing at all produces no groups, so the card can hide itself`() {
        assertTrue(groupProvenance(emptyList()).isEmpty())
    }

    @Test
    fun `a listed skill and a demonstrated one never land in the same group`() {
        // The whole point of the feature: typing "Kubernetes" on a resume must not render
        // identically to having passed an assessment on it.
        val grouped = groupProvenance(
            listOf(item("python", "verified_evidence"), item("kubernetes", "on_resume"))
        )
        assertEquals(2, grouped.size)
        assertEquals(listOf("python"), grouped[0].second.map { it.skill })
        assertEquals(listOf("kubernetes"), grouped[1].second.map { it.skill })
    }

    @Test
    fun `an unrecognised status from a newer backend is dropped, not shown raw`() {
        assertTrue(groupProvenance(listOf(item("python", "some_new_bucket"))).isEmpty())
    }
}

class ProvenanceCopyTest {
    @Test
    fun `each bucket has its own heading`() {
        val titles = listOf("verified_evidence", "on_resume", "missing", "unclear")
            .map { provenanceGroupTitle(it) }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `the resume group says these are listed, not confirmed`() {
        assertTrue(provenanceGroupSubtitle("on_resume").contains("listed"))
        assertTrue(provenanceGroupSubtitle("on_resume").contains("Nothing has confirmed"))
    }

    @Test
    fun `the unclear group asks for data rather than telling the student they lack skills`() {
        // This bucket appears when we hold no skills for the student. Saying "you're missing
        // these" would be a statement about them we have no basis for.
        val subtitle = provenanceGroupSubtitle("unclear")
        assertTrue(subtitle.contains("Add a resume"))
        assertTrue(!subtitle.contains("missing"))
    }
}

class RankingLabelTextTest {
    @Test
    fun `each backend label maps to honest wording about fit, not outcome`() {
        assertEquals("Best match", rankingLabelText("best_match"))
        assertEquals("Strong fit", rankingLabelText("strong_fit"))
        assertEquals("Stretch opportunity", rankingLabelText("stretch_opportunity"))
        assertEquals("Skill-build opportunity", rankingLabelText("skill_build_opportunity"))
    }

    @Test
    fun `no label at all produces no chip`() {
        assertNull(rankingLabelText(null))
    }

    @Test
    fun `an unknown label is dropped rather than shown as a raw enum`() {
        assertNull(rankingLabelText("great_fit_v2"))
    }
}

class SignalOriginNoteTest {
    @Test
    fun `a value we read off a resume says so and invites correction`() {
        val note = signalOriginNote("resume")!!
        assertTrue(note.contains("resume"))
        assertTrue(note.contains("edit"))
    }

    @Test
    fun `a guess is called a guess`() {
        assertTrue(signalOriginNote("inferred")!!.contains("guess"))
    }

    @Test
    fun `something the student typed gets no caption`() {
        // Captioning their own answer as "explicit" tells them nothing and adds noise.
        assertNull(signalOriginNote("explicit"))
        assertNull(signalOriginNote(null))
    }

    @Test
    fun `an unknown origin says nothing rather than showing a raw enum`() {
        assertNull(signalOriginNote("some_future_source"))
    }
}
