// OWNER: Track C — Virtual Try-On (C-206, C-208, C-209, C-210)
plugins {
    alias(libs.plugins.fitcheck.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    @Suppress("DEPRECATION")
    testOptions {
        targetSdk = 35
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
