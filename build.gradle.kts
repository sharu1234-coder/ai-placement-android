plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    // com.github.ben-manes.versions 0.61.0 was tried here as a dependency-staleness report
    // (see the release-candidate audit) but reverted: applying it breaks kspDevDebugKotlin
    // for every variant with "Parameter specified as non-null is null:
    // NonSynchronizedMetricsContainer.report" -- a real incompatibility with this project's
    // current Kotlin/KSP/AGP combo, confirmed by toggling the plugin on/off with everything
    // else held constant. Not worth breaking every Kotlin compile for an informational report;
    // revisit after the next Kotlin/KSP/AGP version bump.
}
