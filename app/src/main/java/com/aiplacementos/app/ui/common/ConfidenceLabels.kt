package com.aiplacementos.app.ui.common

/**
 * Turns the backend's coarse confidence band into student-facing text.
 *
 * The Evidence Engine's confidence is not a measurement on a continuous scale: it takes
 * exactly five values (0/30/45/70/90), one per evidence state. Rendering that as
 * "70% confidence" told students we had a precision we simply do not have -- two skills shown
 * at "70%" are not 70% of anything, they are both just "strong". The backend now sends an
 * explicit band alongside the number; these helpers render the band, and the raw percent is
 * kept only for bar lengths and sorting.
 *
 * Unknown values degrade to a neutral phrase rather than crashing or showing a raw key, so a
 * band the backend adds later is still readable.
 */
fun confidenceBandLabel(band: String): String = when (band) {
    "very_high" -> "Very high confidence"
    "high" -> "High confidence"
    "moderate" -> "Moderate confidence"
    "low" -> "Low confidence"
    "none" -> "Not yet evidenced"
    else -> "Confidence unavailable"
}

/** The same bands as a short adjective, for tight spaces like chips and list rows. */
fun confidenceBandShortLabel(band: String): String = when (band) {
    "very_high" -> "Very high"
    "high" -> "High"
    "moderate" -> "Moderate"
    "low" -> "Low"
    "none" -> "None"
    else -> "Unknown"
}
