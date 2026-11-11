// OWNER: Track A
plugins {
    alias(libs.plugins.fitcheck.android.library)
    alias(libs.plugins.fitcheck.hilt)
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("data/fixtures"))
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.play.services.base)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
