// OWNER: Track C
plugins {
    alias(libs.plugins.fitcheck.android.feature)
}

dependencies {
    implementation(project(":core:ai"))
    implementation(project(":core:imaging"))
    testImplementation(testFixtures(project(":core:model")))
}
