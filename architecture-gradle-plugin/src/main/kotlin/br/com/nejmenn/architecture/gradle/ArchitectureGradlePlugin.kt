package br.com.nejmenn.architecture.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

class ArchitectureGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("architectureGradlePlugin", ArchitectureGradlePluginExtension::class.java)
        val check = project.tasks.register("architectureCheck", ArchitectureCheckTask::class.java) { task ->
            task.group = "verification"
            task.description = "Checks Kotlin sources against the configured ArchitectureGradlePlugin architecture rules."
            task.scanRoot.set(project.layout.projectDirectory)
            task.markerFile.set(project.layout.buildDirectory.file("architecture/architecture-check.marker"))
            task.moduleDependencies.convention(emptyList())
            task.externalDependencies.convention(emptyList())
            task.moduleDirectories.convention(emptyList())
        }
        project.tasks.register("architectureReport", ArchitectureReportTask::class.java) { task ->
            task.group = "verification"
            task.description = "Generates JSON, text and HTML architecture reports."
            task.scanRoot.set(project.layout.projectDirectory)
            task.markerFile.set(project.layout.buildDirectory.file("architecture/architecture-report.marker"))
            task.reportDirectory.set(project.layout.buildDirectory.dir("reports/architecture"))
            task.moduleDependencies.convention(emptyList())
            task.externalDependencies.convention(emptyList())
            task.moduleDirectories.convention(emptyList())
        }

        project.gradle.projectsEvaluated {
            val tree = project.fileTree(project.projectDir)
            tree.include(extension.sources.includes)
            tree.exclude(extension.sources.excludes)
            val dependencies = project.allprojects.flatMap { candidate ->
                candidate.configurations.flatMap { configuration ->
                    configuration.dependencies.withType(ProjectDependency::class.java).mapNotNull { dependency ->
                        if (dependency.path == candidate.path) null
                        else "${candidate.path}|${configuration.name}|${dependency.path}"
                    }
                }
            }.distinct().sorted()
            val externalDependencies = project.allprojects.flatMap { candidate ->
                candidate.configurations.flatMap { configuration ->
                    configuration.dependencies.withType(ExternalModuleDependency::class.java).mapNotNull { dependency ->
                        val group = dependency.group ?: return@mapNotNull null
                        "${candidate.path}|${configuration.name}|$group|${dependency.name}"
                    }
                }
            }.distinct().sorted()
            val moduleDirectories = project.allprojects.map { candidate ->
                "${candidate.path}|${project.projectDir.toPath().relativize(candidate.projectDir.toPath())}"
            }
            val configuration = extension.toCoreConfiguration()
            val androidConfiguration = extension.toAndroidConfiguration()
            project.tasks.withType(ArchitectureCheckTask::class.java).configureEach { task ->
                task.sourceFiles.from(tree)
                task.architectureConfiguration = configuration
                task.androidConfiguration = androidConfiguration
                task.configurationFingerprint.set("$configuration|$androidConfiguration")
                task.moduleDependencies.set(dependencies)
                task.externalDependencies.set(externalDependencies)
                task.moduleDirectories.set(moduleDirectories)
            }
            if (extension.attachToCheck) {
                project.allprojects.forEach { candidate ->
                    candidate.tasks.matching { it.name == "check" }.configureEach { task -> task.dependsOn(check) }
                }
            }
        }
    }
}
