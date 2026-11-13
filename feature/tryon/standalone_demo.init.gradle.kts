// OWNER: Track C — Standalone Gradle init script to include :feature:tryon without modifying root settings.gradle.kts
import org.gradle.api.NamedDomainObjectContainer

settingsEvaluated {
    include(":feature:tryon")
}

projectsLoaded {
    rootProject.findProject(":feature:tryon")?.let { tryOnProject ->
        tryOnProject.pluginManager.withPlugin("com.android.library") {
            val androidExt = tryOnProject.extensions.getByName("android")
            val agpClassLoader = androidExt.javaClass.classLoader
            val commonExtensionClass = Class.forName(
                "com.android.build.api.dsl.CommonExtension",
                true,
                agpClassLoader,
            )
            val androidSourceSetClass = Class.forName(
                "com.android.build.api.dsl.AndroidSourceSet",
                true,
                agpClassLoader,
            )
            val androidSourceFileClass = Class.forName(
                "com.android.build.api.dsl.AndroidSourceFile",
                true,
                agpClassLoader,
            )
            val sourceSets = commonExtensionClass
                .getMethod("getSourceSets")
                .invoke(androidExt) as NamedDomainObjectContainer<*>
            val debugSourceSet = sourceSets.getByName("debug")
            val manifest = androidSourceSetClass
                .getMethod("getManifest")
                .invoke(debugSourceSet)
            androidSourceFileClass
                .getMethod("srcFile", Any::class.java)
                .invoke(manifest, tryOnProject.file("src/standaloneDemo/AndroidManifest.xml"))
        }
    }
}
