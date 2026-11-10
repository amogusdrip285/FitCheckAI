// HOTSPOT — contract chung. Chỉ THÊM (field có default, method interface có default impl).
plugins {
    alias(libs.plugins.fitcheck.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
