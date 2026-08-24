pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "ArchitectureGradlePlugin"
include("architecture-core", "architecture-presets", "architecture-android", "architecture-gradle-plugin")
