import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

    // Separates "development" and "production" as a real build axis (backend URL today;
    // this is also where each environment's Firebase project plugs in later -- a
    // `google-services.json` dropped into app/src/dev/ vs app/src/prod/ is picked up
    // automatically by the Google Services Gradle plugin once it's added, no other wiring
    // needed). This is what lets publishing switch dev -> prod Firebase and Ollama -> a
    // paid AI API (backend-side, via AIProvider/AI_PROVIDER) without restructuring the app.
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
