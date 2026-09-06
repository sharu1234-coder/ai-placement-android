plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    // com.github.ben-manes.versions 0.61.0 is deliberately absent, but the reason has changed
    // -- do not re-derive the old one from this file's history. It used to break
    // kspDevDebugKotlin for every variant with "Parameter specified as non-null is null:
    // NonSynchronizedMetricsContainer.report", and that comment said to revisit after the
    // next Kotlin/KSP/AGP bump. That bump happened (Kotlin 2.3.21 / KSP 2.3.11 / AGP 8.13.2),
    // and the plugin was re-tested on it: kspDevDebugKotlin now succeeds, so the technical
    // blocker is genuinely gone. It stays out for a different reason -- Dependabot's own PRs
    // on this repo were failing CI for unrelated reasons when this plugin was first wanted as
    // a compensating control (see .github/workflows/ci.yml's Firebase-config step), that is
    // fixed, and a real PR per stale dependency beats an informational report nobody reads.
    // If you do want it back, it works: add it here and a `dependencyUpdates` step to CI.
}
