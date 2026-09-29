package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.data.remote.ApiJson
import com.aiplacementos.app.data.remote.JobListingDto
import com.aiplacementos.app.data.remote.ManualJobCreateDto
import com.aiplacementos.app.data.remote.SavedJobDto
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire contract for a manually entered job, decoded with the app's real [ApiJson].
 *
 * These matter more than usual here because Phase 4 made a field that was mandatory for the
 * life of the product optional. `ApiJson` deliberately leaves `coerceInputValues` off, so a
 * JSON null for a non-nullable Kotlin property throws rather than quietly becoming "" -- which
 * is the behaviour that keeps "the provider did not say" distinguishable from "the value is
 * empty", and which means a DTO that did not follow the server here would crash the tracker
 * for every student with a campus-placement job in it.
 */
class ManualJobDtoTest {
    private fun savedJobJson(listingBody: String, source: String = "manual") = """
        {
          "id": 12,
          "status": "saved",
          "source": "$source",
          "created_at": "2026-09-25T10:00:00",
          "updated_at": "2026-09-25T10:00:00",
          "listing": $listingBody
        }
    """.trimIndent()

    @Test
    fun `a manually entered job with no link decodes rather than throwing`() {
        val json = savedJobJson(
            """
            {
              "id": 3,
              "provider": "manual",
              "url": null,
              "title": "Campus placement -- SDE",
              "company": "Contoso",
              "fetched_at": "2026-09-25T10:00:00"
            }
            """.trimIndent(),
        )

        val saved = ApiJson.decodeFromString<SavedJobDto>(json)

        assertNull("no link is null, not an empty string", saved.listing.url)
        assertEquals("manual", saved.listing.provider)
        assertEquals("manual", saved.source)
    }

    @Test
    fun `a listing whose url key is absent entirely still decodes`() {
        // A server that omits a null field rather than sending it explicitly is the same fact
        // arriving a different way, and the client must not tell them apart.
        val json = savedJobJson(
            """
            {
              "id": 3,
              "provider": "manual",
              "title": "Referral -- Data Engineer",
              "fetched_at": "2026-09-25T10:00:00"
            }
            """.trimIndent(),
        )

        val saved = ApiJson.decodeFromString<SavedJobDto>(json)

        assertNull(saved.listing.url)
        assertNull("an unstated company stays unstated", saved.listing.company)
    }

    @Test
    fun `a provider listing still carries its link`() {
        val json = savedJobJson(
            """
            {
              "id": 4,
              "provider": "adzuna",
              "url": "https://jobs.example.com/4",
              "title": "Data Engineer",
              "fetched_at": "2026-09-25T10:00:00"
            }
            """.trimIndent(),
            source = "recommended",
        )

        val saved = ApiJson.decodeFromString<SavedJobDto>(json)

        assertEquals("https://jobs.example.com/4", saved.listing.url)
        assertEquals("recommended", saved.source)
    }

    @Test
    fun `a manual job has no match, and none is invented`() {
        val json = savedJobJson(
            """
            {
              "id": 3,
              "provider": "manual",
              "title": "Campus placement -- SDE",
              "fetched_at": "2026-09-25T10:00:00"
            }
            """.trimIndent(),
        )

        val saved = ApiJson.decodeFromString<SavedJobDto>(json)

        assertNull("nothing has scored this job, so there is no percentage to show", saved.match)
    }

    @Test
    fun `a manual job is never marked stale`() {
        val listing = ApiJson.decodeFromString<JobListingDto>(
            """
            {
              "id": 3,
              "provider": "manual",
              "title": "Campus placement -- SDE",
              "fetched_at": "2026-09-25T10:00:00"
            }
            """.trimIndent(),
        )

        assertTrue(!listing.isStale)
    }

    @Test
    fun `the create request omits the fields the student left empty`() {
        // Sent as absent rather than as "": the two mean different things to the server, and
        // an empty string would become a company named nothing at all.
        val encoded = ApiJson.encodeToString(ManualJobCreateDto(title = "Data Engineer"))

        assertTrue(encoded.contains("\"title\":\"Data Engineer\""))
        assertTrue("company must not be sent as an empty string", !encoded.contains("\"company\""))
        assertTrue(!encoded.contains("\"url\""))
    }

    @Test
    fun `the create request sends the stage the student chose`() {
        val encoded = ApiJson.encodeToString(
            ManualJobCreateDto(title = "Data Engineer", status = "interview"),
        )

        assertTrue(encoded.contains("\"status\":\"interview\""))
    }

    @Test
    fun `the create request never sends provider internals`() {
        // Provenance is the server's fact, not the client's claim, and provider/provider_job_id
        // describe how a listing was ingested -- this one was typed.
        val encoded = ApiJson.encodeToString(
            ManualJobCreateDto(title = "Data Engineer", company = "Contoso"),
        )

        for (forbidden in listOf("provider", "provider_job_id", "source", "profile_id")) {
            assertTrue("$forbidden must not appear in the request", !encoded.contains(forbidden))
        }
    }
}
