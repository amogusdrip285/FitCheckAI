// OWNER: Track C — Virtual Try-On (C-206, C-208, C-209, C-210)
plugins {
    alias(libs.plugins.fitcheck.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

// Try-On backend (proxy/scripts/tryon_local_server.ts or any host serving /v2/tryon/*).
// Set FITCHECK_TRYON_URL in the root .env (see .env.example); empty = try-on stays in CORE (disabled) mode.
// -Pfitcheck.tryonUrl / -Pfitcheck.tryonToken override the .env values.
val dotEnv: Map<String, String> = providers.fileContents(rootProject.layout.projectDirectory.file(".env")).asText
    .orNull.orEmpty().lineSequence().map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    .associate { line -> line.substringBefore("=").trim() to line.substringAfter("=").trim().trim('"', '\'') }
val tryOnUrl: String = (providers.gradleProperty("fitcheck.tryonUrl").orNull ?: dotEnv["FITCHECK_TRYON_URL"]).orEmpty()
    .trimEnd('/').also { url ->
        require(url.isEmpty() || Regex("https?://[A-Za-z0-9.\\-]+(:\\d{1,5})?(/[A-Za-z0-9._~/\\-]*)?").matches(url)) {
            "FITCHECK_TRYON_URL must be empty or an http(s) URL, got '$url'"
        }
    }
val tryOnToken: String = (providers.gradleProperty("fitcheck.tryonToken").orNull ?: dotEnv["FITCHECK_TRYON_TOKEN"]).orEmpty()
    .also { token -> require(Regex("[A-Za-z0-9_.\\-]{0,128}").matches(token)) { "FITCHECK_TRYON_TOKEN may only contain letters, digits, . _ -" } }

android {
    @Suppress("DEPRECATION")
    testOptions {
        targetSdk = 35
    }
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "TRYON_BACKEND_URL", "\"$tryOnUrl\"")
        buildConfigField("String", "TRYON_BACKEND_TOKEN", "\"$tryOnToken\"")
    }
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.activity.compose)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
