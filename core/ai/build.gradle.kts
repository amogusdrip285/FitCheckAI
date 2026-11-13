// OWNER: Track B
plugins {
    alias(libs.plugins.fitcheck.android.library)
    alias(libs.plugins.fitcheck.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        val proxyUrl = providers.gradleProperty("fitcheck.proxyUrl").get()
        buildConfigField("String", "PROXY_URL", "\"$proxyUrl\"")
        // Mã truy cập demo kín (POST /v2/session). Đặt trong ~/.gradle/gradle.properties, KHÔNG commit.
        // Trống ⇒ client báo UNSUPPORTED_CAPABILITY, không gọi mạng. Không phải bảo mật public beta (BACK-011).
        val accessCode = providers.gradleProperty("fitcheck.accessCode").orElse("").get()
        require(Regex("[A-Za-z0-9_.-]{0,128}").matches(accessCode)) { "fitcheck.accessCode chỉ gồm chữ, số, . _ -" }
        buildConfigField("String", "PROXY_ACCESS_CODE", "\"$accessCode\"")
        // Thời gian chờ mỗi request AI phía app (ms). Mặc định CT-07 20000. Chỉ nới khi test qua gateway chậm (FOSTA ~15–40s),
        // đồng thời nới PROVIDER_TIMEOUT_MS/PROXY_DEADLINE_MS ở proxy. Kẹp 5000–130000.
        val aiTimeoutMs = providers.gradleProperty("fitcheck.aiTimeoutMs").orElse("20000").get().toLongOrNull()
            ?.coerceIn(5_000L, 130_000L) ?: error("fitcheck.aiTimeoutMs phải là số mili-giây")
        buildConfigField("long", "AI_TIMEOUT_MS", "${aiTimeoutMs}L")
    }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.ktor.client.mock)
    testImplementation(testFixtures(project(":core:model")))
}
