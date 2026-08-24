import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    kotlin("jvm") version "2.1.21" apply false
}

val githubPackagesActor = providers.gradleProperty("gpr.user")
    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
val githubPackagesToken = providers.gradleProperty("gpr.key")
    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
val githubPackagesUrl = uri("https://maven.pkg.github.com/jaimenejaim/ArchitectureGradlePlugin")

val validateGitHubPackagesCredentials by tasks.registering {
    group = "publishing"
    description = "Validates credentials required to publish to GitHub Packages."
    doLast {
        check(githubPackagesActor.isPresent && githubPackagesToken.isPresent) {
            "GitHub Packages credentials are missing. Set GITHUB_ACTOR and GITHUB_TOKEN " +
                "(PAT classic with write:packages), or gpr.user and gpr.key in ~/.gradle/gradle.properties."
        }
    }
}

allprojects {
    group = "br.com.nejmenn"
    version = providers.gradleProperty("version").orElse("1.1.0").get()
}

subprojects {
    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories.maven {
                name = "GitHubPackages"
                url = githubPackagesUrl
                credentials {
                    username = githubPackagesActor.orNull
                    password = githubPackagesToken.orNull
                }
            }
        }

        tasks.withType<PublishToMavenRepository>().configureEach {
            if (name.endsWith("ToGitHubPackagesRepository")) {
                dependsOn(validateGitHubPackagesCredentials)
            }
        }
    }
}

tasks.register("publishToGitHubPackages") {
    group = "publishing"
    description = "Publishes the plugin, marker, core and presets to the official GitHub Packages repository."
    dependsOn(subprojects.map { subproject ->
        subproject.tasks.withType<PublishToMavenRepository>().matching {
            it.name.endsWith("ToGitHubPackagesRepository")
        }
    })
}
