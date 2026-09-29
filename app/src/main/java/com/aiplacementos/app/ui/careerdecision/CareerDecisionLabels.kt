package com.aiplacementos.app.ui.careerdecision

/**
 * The words Career Decision Lab uses for the number it shows, in one place (F12-08).
 *
 * The number is `evidence_coverage_percent`: the share of a role's required skills that the
 * student's evidence actually backs. It is NOT readiness. Readiness in this product means
 * JobReadinessRead.overall_percent, a five-signal average over skills, projects, resume,
 * interview and assessments -- a different formula over different inputs, still used and still
 * correctly named on the Career Path screens. Phase 2G renamed the backend field off `readiness`
 * for exactly that reason, and the bar on this screen was relabelled with it.
 *
 * Two strings were not, and that is the finding. The paywall a free student sees, and the intro
 * a paying one reads, both described the same number as "evidence-backed readiness" -- a phrase
 * that sounds careful while still naming a stronger measurement than the data supports. A
 * student comparing roles would reasonably read it as "how ready I am for this job", which is a
 * claim this screen never makes and cannot make: it has not looked at their interviews, their
 * resume or their assessments.
 *
 * They live here rather than inline because they drifted from the label once already. One
 * source, shared by the label and the prose that explains it, is what keeps the three saying the
 * same thing -- and it matches the `*Labels.kt` + `*LabelsTest.kt` pattern this module already
 * uses for Career Path and Career Goal copy.
 *
 * The wording deliberately echoes the backend's own generated reason for the same number
 * ("N% of <role>'s required skills are backed by real evidence.", career_decision_service), so
 * the card and the explanation underneath it do not read as two different measurements.
 */

/** What the number is called wherever it is shown on this screen. */
const val EVIDENCE_COVERAGE_LABEL = "Evidence coverage"

/** Shown to a free student, in place of the whole screen. Describes what they would be buying. */
const val CAREER_DECISION_LAB_PAYWALL_DESCRIPTION =
    "Compare 2-3 roles by how much of each one's required skills your evidence already covers, " +
        "and simulate a study plan."

/**
 * Shown at the top of the screen itself. Keeps the original anti-inflation clause verbatim: the
 * comparison is of evidence, never of the odds of being hired.
 */
const val CAREER_DECISION_LAB_INTRO =
    "Pick 2-3 roles to compare how much of each one's required skills your evidence already " +
        "covers -- never a guess at your odds of getting hired."
