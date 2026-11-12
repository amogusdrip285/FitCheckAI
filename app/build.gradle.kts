// FROZEN sau Sprint 0 — sửa phải báo cả nhóm, 1 PR nhỏ riêng.
plugins {
    alias(libs.plugins.fitcheck.android.application)
    alias(libs.plugins.fitcheck.android.compose)
    alias(libs.plugins.fitcheck.hilt)
}

android {
    namespace = "com.fitcheckai.app"

    defaultConfig {
        applicationId = "com.fitcheckai"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    // Các module chứa Hilt binding — phải có mặt để graph DI đầy đủ.
    implementation(project(":core:database"))
    implementation(project(":core:imaging"))
    implementation(project(":core:ai"))
    implementation(project(":core:weather"))
    implementation(project(":core:stylist"))
    implementation(project(":core:datastore"))
    implementation(project(":core:analytics"))

    implementation(project(":feature:wardrobe"))
    implementation(project(":feature:today"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:tryon"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
