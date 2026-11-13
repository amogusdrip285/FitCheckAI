// OWNER: Track D
plugins {
    alias(libs.plugins.fitcheck.android.feature)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    testImplementation(testFixtures(project(":core:model")))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
