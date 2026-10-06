import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Room's annotation processor. KSP's version string is tied to the exact Kotlin
    // compiler version (see gradle/libs.versions.toml's ksp comment) -- not a normal
    // library bump if the Kotlin version ever moves.
    alias(libs.plugins.ksp)
    // Reads app/src/<flavor>/google-services.json (gitignored -- see .gitignore and this
    // file's own comment further down) and generates the resources/BuildConfig entries the
    // Firebase SDKs below need. Requires that file to exist for every flavor being built --
    // there's no "optional, falls back to unsigned" story here the way keystore.properties
    // has for release signing; see README's Firebase section for how to get one.
    alias(libs.plugins.google.services)
    // Separate from google-services: uploads mapping/symbol files for deobfuscated crash
    // reports and needs to run on every build, including release (where isMinifyEnabled
    // means Crashlytics would otherwise only see obfuscated stack traces).
    alias(libs.plugins.firebase.crashlytics)
    // See root build.gradle.kts for why com.github.ben-manes.versions is NOT applied here.
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
// Emulator default (10.0.2.2 = host loopback). A physical device needs this set to your
// machine's LAN IP: add `API_BASE_URL=http://<lan-ip>:8000/` to local.properties (gitignored).
val devApiBaseUrl = localProperties.getProperty("API_BASE_URL") ?: "http://10.0.2.2:8000/"
// No production backend is deployed yet (that's a later stage per PROJECT_HANDOFF.md's
// production report, section "Production Firebase/backend deployment"). Override via
// `PROD_API_BASE_URL` in local.properties once one exists; the placeholder below makes a
// misconfigured prod build fail obviously (DNS error) rather than silently talk to a dev
// server or localhost.
val prodApiBaseUrl = localProperties.getProperty("PROD_API_BASE_URL")
    ?: "https://api.aiplacementos.example.invalid/"

// Release signing credentials, read from a gitignored keystore.properties (see
// keystore.properties.example) -- never hardcoded, never committed. Deliberately optional:
// if the file isn't present (true for every checkout that hasn't generated a real release
// keystore yet, including this one), `hasReleaseSigningConfig` is false and the release
// build type is left exactly as it was before this change (unsigned) -- nothing regresses
// for anyone who hasn't set this up.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val hasReleaseSigningConfig = keystoreProperties.getProperty("storeFile") != null
if (hasReleaseSigningConfig) {
    // A keystore.properties that exists but is incomplete used to reach signingConfigs with
    // nulls, and the failure then surfaced much later as an opaque error from Gradle or
    // apksigner about a null password -- for a file the developer had just written, where the
    // real cause is one missing line. Fail here instead, naming the keys that are missing and
    // never their values, so nothing a secret could be read out of lands in the build log.
    //
    // This deliberately does NOT change the no-file case: without keystore.properties,
    // hasReleaseSigningConfig stays false, the release build stays unsigned, and nothing is
    // validated -- a fresh checkout and CI on a fork still build exactly as before.
    val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .filter { keystoreProperties.getProperty(it).isNullOrBlank() }
    require(missing.isEmpty()) {
        "keystore.properties exists but has no value for: ${missing.joinToString()}. " +
            "See keystore.properties.example for the keys a release signing config needs."
    }
    val storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
    require(storeFile.isFile) {
        "keystore.properties points storeFile at ${storeFile.path}, which does not exist. " +
            "Generate the keystore first (keystore.properties.example has the keytool " +
            "command) or correct the path -- it is resolved against the project root."
    }
}

android {
    namespace = "com.aiplacementos.app"
    // Compiled against 36, but see targetSdk below -- these are deliberately different.
    // Raised from 35 because firebase-bom 34.18.0's Crashlytics references
    // android.os.ProfilingTrigger, which only exists from API 36; on compileSdk 35 R8 fails
    // the release build outright ("Missing class android.os.ProfilingTrigger"). Compiling
    // against a newer platform does not change how the app behaves on any device -- that is
    // targetSdk's job -- it only lets the compiler and R8 see APIs the libraries reference.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aiplacementos.app"
        minSdk = 26
        // Raised to 36 for Android 16 in this phase. Play requires API 36 for new apps
        // and for updates from 31 August 2026. The device QA the previous note asked for
        // is what justified this: the behaviour-change audit found exactly one change that
        // reaches this app -- predictive back, on by default at 36 -- and edge-to-edge was
        // already enforced here at 35, so it is unchanged by this bump.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Separates "development" and "production" as a real build axis (backend URL; Firebase
    // config -- app/src/dev/google-services.json vs app/src/prod/google-services.json,
    // picked up automatically by the Google Services Gradle plugin, one registered Android
    // app per flavor in the same "ai-placement-os" Firebase project). This is what lets
    // publishing switch dev -> prod Firebase and Ollama -> a paid AI API (backend-side, via
    // AIProvider/AI_PROVIDER) without restructuring the app.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"$devApiBaseUrl\"")
            buildConfigField("String", "ENVIRONMENT", "\"development\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"$prodApiBaseUrl\"")
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Plain JVM unit tests (no Robolectric) would otherwise crash on any
            // android.util.Log call ("Method d in android.util.Log not mocked") -- needed for
            // ObservabilityInterceptor's tests, which exercise real Log.d/Log.w calls.
            isReturnDefaultValues = true
        }
    }
}

// ContinuousIntegrationGuardTest asserts on .github/workflows/ci.yml -- that CI still compiles
// androidTest, still runs both unit-test flavours, and still builds what it used to. Gradle has
// no way to know a test reads a file outside the module, so without this the task is UP-TO-DATE
// after a workflow-only edit and the guard never executes. That was not theoretical: the Phase 29
// mutation that deleted the androidTest step from the workflow was reported as MISSED for exactly
// this reason, with the guard never running at all.
//
// Declaring the file as an input is the whole fix: change the workflow and the unit tests rerun.
// Marked optional so a checkout without the .github directory still builds rather than failing to
// configure.
//
// ReleaseSigningGuardTest reads the same kind of out-of-module files for the release and signing
// configuration, so each one is declared for the same reason: without this, editing .gitignore to
// stop ignoring keystores -- exactly the mutation the guard exists to catch -- would leave the
// task UP-TO-DATE and the guard would never run.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file(".github/workflows/ci.yml"))
        .withPropertyName("ciWorkflow")
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .optional(true)
    listOf(
        "gitignore" to rootProject.file(".gitignore"),
        "gradleProperties" to rootProject.file("gradle.properties"),
        "appBuildScript" to file("build.gradle.kts"),
        "keystoreTemplate" to rootProject.file("keystore.properties.example"),
        "devManifest" to file("src/dev/AndroidManifest.xml"),
        "prodManifest" to file("src/prod/AndroidManifest.xml"),
    ).forEach { (name, releaseConfigFile) ->
        inputs.file(releaseConfigFile)
            .withPropertyName(name)
            .withPathSensitivity(PathSensitivity.RELATIVE)
            .optional(true)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    // Encrypts TokenStore's access/refresh JWTs at rest (Android Keystore-backed key via
    // Tink's AndroidKeysetManager) -- see TokenStore.kt.
    implementation(libs.tink.android)
    // Local persistence for the Placement Accountability Alarm (scheduled alarm configs +
    // resumable challenge state) -- the only local DB in the app; everything else stays
    // backend-fetched-on-demand per the existing architecture. WorkManager is deliberately
    // not used anywhere in this app -- its granularity is wrong for an exact-time alarm.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Firebase: auxiliary services only (FCM, Crashlytics, Analytics, App Check) -- never a
    // replacement for the FastAPI + PostgreSQL backend, which stays the system of record for
    // all app data. Deliberately NOT included: firebase-firestore, firebase-auth,
    // firebase-storage.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    // Both App Check provider artifacts are needed for every flavor: AiPlacementApp.kt
    // references both factory classes unconditionally (the choice between them is a
    // BuildConfig.DEBUG runtime branch, not a compile-time flavor split -- a prod-flavor
    // debug build like assembleProdDebug still needs the Debug provider available, since
    // Play Integrity doesn't work on emulators/unregistered installs).
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.androidx.fragment)
    // Razorpay TEST MODE checkout only -- see payment/RazorpayCheckoutBridge.kt. Order
    // creation and payment-signature verification both happen server-side; this SDK only
    // renders Razorpay's own hosted checkout UI and hands back a result to verify.
    implementation(libs.razorpay.checkout)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Kotlin 2.x removed the android.kotlinOptions DSL (it became a hard error in the compiler
// plugin, not just a deprecation warning). This is the replacement for the old
// `kotlinOptions { jvmTarget = "17" }` block that used to sit inside android {} -- same
// JVM target, expressed through the typed compilerOptions DSL and kept in step with the
// compileOptions source/target compatibility above.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
