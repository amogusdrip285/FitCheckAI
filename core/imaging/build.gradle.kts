// OWNER: Track A
plugins {
    alias(libs.plugins.fitcheck.android.library)
    alias(libs.plugins.fitcheck.hilt)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.kotlinx.coroutines.android)
}
