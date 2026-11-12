// OWNER: Track B
plugins {
    alias(libs.plugins.fitcheck.android.library)
    alias(libs.plugins.fitcheck.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        val proxyUrl = providers.gradleProperty("fitcheck.proxyUrl").get()
        buildConfigField("String", "PROXY_URL", "\"$proxyUrl\"")
    }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.ktor.client.mock)
    testImplementation(testFixtures(project(":core:model")))
}
