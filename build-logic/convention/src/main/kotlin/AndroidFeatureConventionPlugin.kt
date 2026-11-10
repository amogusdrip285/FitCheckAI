import org.gradle.api.Plugin
import org.gradle.api.Project

/** library + compose + hilt + navigation + dependency chuẩn cho mọi feature module. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("fitcheck.android.library")
            pluginManager.apply("fitcheck.android.compose")
            pluginManager.apply("fitcheck.hilt")
            dependencies.add("implementation", project(":core:model"))
            dependencies.add("implementation", project(":core:ui"))
            dependencies.add("implementation", lib("androidx-hilt-navigation-compose"))
            dependencies.add("implementation", lib("androidx-lifecycle-runtime-compose"))
            dependencies.add("implementation", lib("androidx-lifecycle-viewmodel-compose"))
            dependencies.add("implementation", lib("androidx-navigation-compose"))
        }
    }
}
