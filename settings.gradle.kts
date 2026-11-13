// FROZEN sau Sprint 0 — thêm module mới phải báo cả nhóm.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FitCheckAI"

include(":app")

include(":core:model")
include(":core:ui")
include(":core:database")
include(":core:imaging")
include(":core:ai")
include(":core:weather")
include(":core:stylist")
include(":core:datastore")
include(":core:analytics")

include(":feature:wardrobe")
include(":feature:today")
include(":feature:onboarding")
include(":feature:insights")
include(":feature:tryon")
