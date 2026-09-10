package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.data.remote.JobListingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure display helpers.
 *
 * Every test here guards the same rule from a different angle: the UI may only state facts the
 * provider actually supplied. A salary the employer never advertised, or a posted date we could
 * not parse, must produce *nothing* rather than a plausible-looking placeholder.
 */
private fun listing(
    salaryMin: Int? = null,
    salaryMax: Int? = null,
    currency: String? = "INR",
    period: String? = null,
    predicted: Boolean = false,
) = JobListingDto(
    id = 1,
    provider = "fake",
    url = "https://x.com/1",
    title = "Software Engineer",
    salaryMin = salaryMin,
    salaryMax = salaryMax,
    salaryCurrency = currency,
    salaryPeriod = period,
    salaryIsPredicted = predicted,
    fetchedAt = "2026-09-07T10:00:00",
)

class FormatSalaryTest {
    @Test
    fun `no salary at all produces no tile`() {
        assertNull(formatSalary(listing()))
    }

    @Test
    fun `a range is compacted into lakhs, the convention Indian adverts use`() {
        assertEquals("₹9L-16L", formatSalary(listing(salaryMin = 900_000, salaryMax = 1_600_000)))
    }

    @Test
    fun `a fractional range keeps one decimal rather than rounding away the difference`() {
        assertEquals("₹7.5L-12.5L", formatSalary(listing(salaryMin = 750_000, salaryMax = 1_250_000)))
    }

    @Test
    fun `a minimum alone reads as a floor, not a range`() {
        assertEquals("₹9L+", formatSalary(listing(salaryMin = 900_000)))
    }

    @Test
    fun `a maximum alone reads as a ceiling`() {
        assertEquals("Up to ₹12L", formatSalary(listing(salaryMax = 1_200_000)))
    }

    @Test
    fun `a predicted salary is labelled an estimate`() {
        // The difference between a figure the employer published and one the provider
        // modelled. Presenting the second as the first would be fabricated data.
        val result = formatSalary(listing(salaryMin = 900_000, salaryMax = 1_600_000, predicted = true))
        assertTrue(result!!.endsWith("est."))
    }

    @Test
    fun `an unknown currency code is shown rather than silently assumed to be rupees`() {
        val result = formatSalary(listing(salaryMin = 50_000, currency = "AUD"))
        assertTrue(result!!.startsWith("AUD "))
    }

    @Test
    fun `a missing currency does not print a stray symbol`() {
        val result = formatSalary(listing(salaryMin = 900_000, currency = null))
        assertEquals("9L+", result)
    }

    @Test
    fun `an hourly rate is not shown as though it were the annual figure`() {
        // Careerjet advertises by the hour, the day, the week and the month, and sends the
        // period with the number. It used to be dropped, so ₹500 an hour and ₹500 a year
        // rendered identically -- a figure wrong by a factor of about two thousand, in the
        // one place a student decides whether a job is worth applying for.
        assertEquals("₹500+/hr", formatSalary(listing(salaryMin = 500, period = "hour")))
        assertEquals("₹40K+/mo", formatSalary(listing(salaryMin = 40_000, period = "month")))
        assertEquals("₹12K+/wk", formatSalary(listing(salaryMin = 12_000, period = "week")))
        assertEquals("₹2K+/day", formatSalary(listing(salaryMin = 2_000, period = "day")))
    }

    @Test
    fun `an annual salary carries no suffix, because that is how this market quotes them`() {
        assertEquals("₹9L+", formatSalary(listing(salaryMin = 900_000, period = "year")))
    }

    @Test
    fun `an unstated period is left unlabelled rather than assumed to be annual in words`() {
        // Null means the provider did not say. Writing "per year" there would be exactly the
        // guess the backend adapter refuses to make.
        assertEquals("₹9L+", formatSalary(listing(salaryMin = 900_000, period = null)))
        assertEquals("₹9L+", formatSalary(listing(salaryMin = 900_000, period = "fortnight")))
    }

    @Test
    fun `an estimate that is also hourly says both things`() {
        assertEquals(
            "₹500+/hr est.",
            formatSalary(listing(salaryMin = 500, period = "hour", predicted = true)),
        )
    }
}

class RelativePostedLabelTest {
    // 2026-09-07 as days since epoch (verified against Python's date arithmetic, not
    // eyeballed), so these tests do not depend on the wall clock.
    private val today = 20_703L

    @Test
    fun `today, yesterday and a recent date read naturally`() {
        assertEquals("Today", relativePostedLabel("2026-09-07T09:00:00", today))
        assertEquals("1 day ago", relativePostedLabel("2026-09-06T09:00:00", today))
        assertEquals("5 days ago", relativePostedLabel("2026-09-02T09:00:00", today))
    }

    @Test
    fun `anything older than a month is not given false precision`() {
        assertEquals("Over a month ago", relativePostedLabel("2026-06-01T09:00:00", today))
    }

    @Test
    fun `a date-only value parses too, since not every provider sends a time`() {
        assertEquals("Today", relativePostedLabel("2026-09-07", today))
    }

    @Test
    fun `an unparseable value produces no label at all`() {
        // Falling back to "recently" would state a freshness we cannot establish.
        assertNull(relativePostedLabel("not a date", today))
        assertNull(relativePostedLabel("", today))
    }

    @Test
    fun `a future date produces no label rather than a negative age`() {
        // Provider clock skew is real; "-3 days ago" would be worse than saying nothing.
        assertNull(relativePostedLabel("2026-09-10T09:00:00", today))
    }
}

class LabelTest {
    @Test
    fun `work mode and employment type map to human labels`() {
        assertEquals("Remote", workModeLabel("remote"))
        assertEquals("On-site", workModeLabel("onsite"))
        assertEquals("Full-time", employmentTypeLabel("full_time"))
        assertEquals("Internship", employmentTypeLabel("internship"))
    }

    @Test
    fun `an unrecognised value is passed through rather than dropped or guessed`() {
        assertEquals("job_share", employmentTypeLabel("job_share"))
        assertEquals("flexible", workModeLabel("flexible"))
    }

    @Test
    fun `seniority is expressed in the years-of-experience terms adverts use`() {
        assertEquals("0-1 years", seniorityLabel("fresher"))
        assertEquals("6+ years", seniorityLabel("senior"))
    }
}

class MatchPercentTest {
    @Test
    fun `the backend's 0 to 1 score becomes a whole percentage`() {
        assertEquals(90, sampleMatch(totalScore = 0.9f).matchPercent())
        assertEquals(0, sampleMatch(totalScore = 0f).matchPercent())
        assertEquals(100, sampleMatch(totalScore = 1f).matchPercent())
    }

    @Test
    fun `an out-of-range score is clamped rather than displayed as 120 percent`() {
        assertEquals(100, sampleMatch(totalScore = 1.4f).matchPercent())
        assertEquals(0, sampleMatch(totalScore = -0.5f).matchPercent())
    }
}
