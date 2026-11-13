// HOTSPOT — contract chung. Chỉ THÊM (field có default, method interface có default impl).
plugins {
    alias(libs.plugins.fitcheck.jvm.library)
    `java-test-fixtures`
}

tasks.processResources {
    from(rootProject.file("data/sources/garment-types.json")) {
        into("com/fitcheckai/core/model/wardrobe")
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    testFixturesApi(libs.kotlinx.coroutines.core)
}
