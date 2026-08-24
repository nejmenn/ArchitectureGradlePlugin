package br.com.nejmenn.architecture.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArchitectureGradlePluginFunctionalTest {
    @TempDir lateinit var projectDir: Path

    @Test fun `valid project exposes tasks produces reports and attaches to check`() {
        write("settings.gradle.kts", "rootProject.name = \"consumer\"")
        write("build.gradle.kts", """
            plugins {
                java
                id("br.com.nejmenn.architecture")
            }
            architectureGradlePlugin {
                preset("spring-hexagonal")
                basePackage = "br.com.nejmenn.orion"
                domain { forbiddenImports.addAll("com.acme.legacy.") }
                sources { exclude("**/ignored/**") }
            }
        """.trimIndent())
        write("src/main/kotlin/br/com/nejmenn/orion/access/usecase/CreateAccessUseCase.kt", "package br.com.nejmenn.orion.access.usecase\nclass CreateAccessUseCase")

        val tasks = run("tasks", "--all")
        assertTrue(tasks.output.contains("architectureCheck"))
        assertTrue(tasks.output.contains("architectureReport"))

        val check = run("check")
        assertEquals(TaskOutcome.SUCCESS, check.task(":architectureCheck")?.outcome)

        val report = run("architectureReport")
        assertEquals(TaskOutcome.SUCCESS, report.task(":architectureReport")?.outcome)
        assertTrue(projectDir.resolve("build/reports/architecture/architecture-report.json").exists())
        assertTrue(projectDir.resolve("build/reports/architecture/architecture-report.txt").exists())
        assertTrue(projectDir.resolve("build/reports/architecture/architecture-report.html").exists())
    }

    @Test fun `invalid project fails with stable rule id and recommendation`() {
        validBuild()
        write("src/main/kotlin/br/com/nejmenn/orion/access/service/AccessService.kt", """
            package br.com.nejmenn.orion.access.service
            import com.fasterxml.jackson.databind.ObjectMapper
            class AccessService
        """.trimIndent())
        val result = runAndFail("architectureCheck")
        assertTrue(result.output.contains("ARCH-006"))
        assertTrue(result.output.contains("AccessUseCase"))
        assertTrue(result.output.contains("ARCH-011"))
    }

    @Test fun `attachToCheck can be disabled`() {
        write("settings.gradle.kts", "rootProject.name = \"consumer\"")
        write("build.gradle.kts", """
            plugins { java; id("br.com.nejmenn.architecture") }
            architectureGradlePlugin { attachToCheck = false }
        """.trimIndent())
        val result = run("check", "--dry-run")
        assertTrue(!result.output.contains(":architectureCheck"))
    }

    @Test fun `invalid Gradle module dependency is detected`() {
        write("settings.gradle.kts", "rootProject.name = \"consumer\"\ninclude(\":domain\", \":infrastructure\")")
        write("build.gradle.kts", """
            plugins { id("br.com.nejmenn.architecture") }
            subprojects { apply(plugin = "java-library") }
            architectureGradlePlugin {
                modules {
                    module(":domain") { mayDependOn() }
                    module(":infrastructure") { mayDependOn(":domain") }
                }
            }
        """.trimIndent())
        write("domain/build.gradle.kts", "dependencies { implementation(project(\":infrastructure\")) }")
        write("infrastructure/build.gradle.kts", "")
        val result = runAndFail("architectureCheck")
        assertTrue(result.output.contains("ARCH-012"))
        assertTrue(result.output.contains(":domain' cannot depend on ':infrastructure"))
    }

    @Test fun `Android preset discovers Kotlin in src main java and protects domain`() {
        write(
            "settings.gradle.kts",
            "rootProject.name = \"consumer\"\ninclude(\":core:domain\")",
        )
        write("build.gradle.kts", """
            plugins { id("br.com.nejmenn.architecture") }
            subprojects { apply(plugin = "java-library") }
            architectureGradlePlugin {
                preset("android-clean-feature")
                basePackage = "br.com.nejmenn.sample"
            }
        """.trimIndent())
        write("core/domain/build.gradle.kts", "")
        write(
            "core/domain/src/main/java/br/com/nejmenn/sample/domain/Account.kt",
            "package br.com.nejmenn.sample.domain\nimport android.content.Context\nclass Account",
        )

        val result = runAndFail("architectureCheck")
        assertTrue(result.output.contains("ARCH-013"))
        assertTrue(result.output.contains("Android Framework Boundary"))
    }

    @Test fun `Android feature implementations may only depend on feature APIs`() {
        write(
            "settings.gradle.kts",
            """
                rootProject.name = "consumer"
                include(":features:home:impl", ":features:editor:impl")
            """.trimIndent(),
        )
        write("build.gradle.kts", """
            plugins { id("br.com.nejmenn.architecture") }
            subprojects { apply(plugin = "java-library") }
            architectureGradlePlugin { preset("android-clean-feature") }
        """.trimIndent())
        write(
            "features/home/impl/build.gradle.kts",
            "dependencies { implementation(project(\":features:editor:impl\")) }",
        )
        write("features/editor/impl/build.gradle.kts", "")

        val result = runAndFail("architectureCheck")
        assertTrue(result.output.contains("ARCH-012"))
        assertTrue(result.output.contains(":features:home:impl' cannot depend on ':features:editor:impl"))
    }

    private fun validBuild() {
        write("settings.gradle.kts", "rootProject.name = \"consumer\"")
        write("build.gradle.kts", """
            plugins { java; id("br.com.nejmenn.architecture") }
            architectureGradlePlugin {
                preset("spring-hexagonal")
                basePackage = "br.com.nejmenn.orion"
            }
        """.trimIndent())
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir.toFile()).withPluginClasspath()
        .withArguments(*arguments, "--stacktrace").build()

    private fun runAndFail(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir.toFile()).withPluginClasspath()
        .withArguments(*arguments, "--stacktrace").buildAndFail()

    private fun write(relative: String, content: String) {
        val file = projectDir.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }
}
