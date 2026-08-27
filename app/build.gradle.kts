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

android {
    namespace = "com.aiplacementos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiplacementos.app"
        minSdk = 26
        targetSdk = 35
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

    kotlinOptions {
        jvmTarget = "17"
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
