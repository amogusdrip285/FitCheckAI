// OWNER: Track A
plugins {
    alias(libs.plugins.fitcheck.android.feature)
}

// Mock wardrobe = real CC0/Wikimedia clothing photos in data/fixtures, seeded on first launch.
// Toggle with FITCHECK_MOCK_WARDROBE=true|false in the root .env (see .env.example); -Pfitcheck.mockWardrobe overrides.
// Off by default: assets are not even packaged when disabled.
val dotEnv: Map<String, String> = providers.fileContents(rootProject.layout.projectDirectory.file(".env")).asText
    .orNull.orEmpty().lineSequence().map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    .associate { line -> line.substringBefore("=").trim() to line.substringAfter("=").trim().trim('"', '\'') }
val mockWardrobe: Boolean = (providers.gradleProperty("fitcheck.mockWardrobe").orNull
    ?: dotEnv["FITCHECK_MOCK_WARDROBE"] ?: "false").lowercase().let { value ->
    require(value == "true" || value == "false") { "FITCHECK_MOCK_WARDROBE must be true or false, got '$value'" }
    value == "true"
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
    buildFeatures.buildConfig = true
    defaultConfig.buildConfigField("boolean", "MOCK_WARDROBE", mockWardrobe.toString())
    if (mockWardrobe) sourceSets.getByName("main").assets.srcDir(rootProject.file("data/fixtures"))
}

dependencies {
    implementation(project(":core:ai"))
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
