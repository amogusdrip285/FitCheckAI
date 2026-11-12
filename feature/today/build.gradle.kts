// OWNER: Track C
plugins {
    alias(libs.plugins.fitcheck.android.feature)
}

dependencies {
    testImplementation(testFixtures(project(":core:model")))
}
