import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            }
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
            }
            val bom = dependencies.platform(lib("androidx-compose-bom"))
            dependencies.add("implementation", bom)
            dependencies.add("androidTestImplementation", bom)
            dependencies.add("implementation", lib("androidx-compose-ui"))
            dependencies.add("implementation", lib("androidx-compose-material3"))
            dependencies.add("implementation", lib("androidx-compose-ui-tooling-preview"))
            dependencies.add("debugImplementation", lib("androidx-compose-ui-tooling"))
        }
    }
}
