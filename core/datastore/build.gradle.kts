// OWNER: Track D
plugins {
    alias(libs.plugins.fitcheck.android.library)
    alias(libs.plugins.fitcheck.hilt)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(testFixtures(project(":core:model")))
    androidTestImplementation(project(":core:imaging"))
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
