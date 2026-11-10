// OWNER: Track D
plugins {
    alias(libs.plugins.fitcheck.android.library)
    alias(libs.plugins.fitcheck.android.compose)
}

dependencies {
    implementation(project(":core:model"))
    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
}
