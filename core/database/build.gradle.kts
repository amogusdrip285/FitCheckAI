// OWNER: Track A
plugins {
    alias(libs.plugins.fitcheck.android.library)
    alias(libs.plugins.fitcheck.hilt)
    alias(libs.plugins.room)
    jacoco
}

android {
    buildFeatures.buildConfig = true
    testOptions.unitTests.isIncludeAndroidResources = true
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

jacoco { toolVersion = "0.8.13" }

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        // Robolectric loads application classes through a sandbox without a code-source location.
        isIncludeNoLocationClasses = true
        includes = listOf("com.fitcheckai.core.database.*")
        excludes = listOf("jdk.internal.*")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(testFixtures(project(":core:model")))
    androidTestImplementation(project(":core:imaging"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// Measure handwritten database code; Room/Hilt and Kotlin data-class boilerplate are generated.
val databaseClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
    exclude("**/di/**", "**/entity/**", "**/*_Impl*", "**/*_Factory*", "**/BuildConfig*", "**/Hilt*")
}
val coverageReport by tasks.registering(JacocoReport::class) {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(databaseClasses)
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    reports { xml.required.set(true); html.required.set(true) }
}
tasks.register<JacocoCoverageVerification>("verifyDatabaseCoverage") {
    dependsOn(coverageReport)
    classDirectories.setFrom(databaseClasses)
    executionData.setFrom(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    violationRules { rule { limit { counter = "LINE"; minimum = "0.80".toBigDecimal() } } }
}
