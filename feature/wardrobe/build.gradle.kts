// OWNER: Track A
plugins {
    alias(libs.plugins.fitcheck.android.feature)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
    buildFeatures.buildConfig = true
    sourceSets.getByName("debug").assets.srcDir(rootProject.file("data/fixtures"))
}

dependencies {
    implementation(project(":core:imaging"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core:model")))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
