import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal object FitCheckConfig {
    const val COMPILE_SDK = 35
    const val MIN_SDK = 26
    const val TARGET_SDK = 35
    val JAVA_VERSION = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    libs.findLibrary(alias).orElseThrow {
        IllegalStateException("Missing library \"$alias\" in libs.versions.toml")
    }

internal fun Project.configureKotlinAndroid(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = FitCheckConfig.COMPILE_SDK
        defaultConfig {
            minSdk = FitCheckConfig.MIN_SDK
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = FitCheckConfig.JAVA_VERSION
            targetCompatibility = FitCheckConfig.JAVA_VERSION
        }
        testOptions {
            unitTests.isReturnDefaultValues = true
        }
    }
    configureKotlinCompile()
}

internal fun Project.configureKotlinCompile() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(FitCheckConfig.JVM_TARGET)
        }
    }
}

internal fun Project.addUnitTestDependencies() {
    dependencies.add("testImplementation", lib("junit"))
    dependencies.add("testImplementation", lib("kotlinx-coroutines-test"))
    dependencies.add("testImplementation", lib("turbine"))
}
