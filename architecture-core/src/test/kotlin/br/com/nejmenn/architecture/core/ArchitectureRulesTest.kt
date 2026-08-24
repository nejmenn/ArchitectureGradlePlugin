package br.com.nejmenn.architecture.core

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchitectureRulesTest {
    private val root = Path.of("/project")
    private val parser = KotlinSourceParser()

    @Test fun `forbidden import reports its source line`() {
        val result = run(ArchitectureConfiguration(forbiddenImports = setOf("org.springframework.")), "src/main/kotlin/io/acme/Foo.kt", """
            package io.acme
            import org.springframework.stereotype.Component
            class Foo
        """.trimIndent())
        assertViolation(result, RuleId.FORBIDDEN_IMPORT, 2)
    }

    @Test fun `canonical package rejects sources outside base`() {
        val result = run(ArchitectureConfiguration(basePackage = "br.com.nejmenn"), "src/main/kotlin/com/acme/Foo.kt", "package com.acme\nclass Foo")
        assertViolation(result, RuleId.CANONICAL_PACKAGE)
    }

    @Test fun `physical path must mirror package`() {
        val result = run(ArchitectureConfiguration(), "src/main/kotlin/wrong/Foo.kt", "package io.acme\nclass Foo")
        assertViolation(result, RuleId.PACKAGE_PATH)
    }

    @Test fun `one type rule ignores nested types and detects a second top-level type`() {
        val source = """
            package io.acme
            class Outer { class Nested }
            interface Second
        """.trimIndent()
        val result = run(ArchitectureConfiguration(filenameMustMatchType = false), "src/main/kotlin/io/acme/Outer.kt", source)
        assertViolation(result, RuleId.ONE_TYPE_PER_FILE)
        assertEquals(listOf("Outer", "Second"), parser.parse(root.resolve("Test.kt"), root, source).topLevelTypes.map { it.name })
    }

    @Test fun `filename must match the single type`() {
        val result = run(ArchitectureConfiguration(), "src/main/kotlin/io/acme/Wrong.kt", "package io.acme\ndata class Right(val id: String)")
        assertViolation(result, RuleId.FILENAME_MATCHES_TYPE)
    }

    @Test fun `forbidden type suffix supplies replacement`() {
        val result = run(
            ArchitectureConfiguration(
                forbiddenSuffixes = listOf(
                    ForbiddenSuffix(
                        suffix = "Service",
                        recommendation = "UseCase",
                    ),
                ),
            ),
            "src/main/kotlin/io/acme/AccessService.kt", "package io.acme\nclass AccessService",
        )
        assertEquals("AccessUseCase", result.violations.first { it.ruleId == RuleId.FORBIDDEN_TYPE_SUFFIX.code }.recommendation)
    }

    @Test fun `forbidden package can recommend usecase`() {
        val result = run(
            ArchitectureConfiguration(
                forbiddenPackages = listOf(
                    ForbiddenPackage(
                        packageFragment = "service",
                        recommendation = "usecase",
                    ),
                ),
            ),
            "src/main/kotlin/io/acme/service/Foo.kt", "package io.acme.service\nclass Foo",
        )
        assertViolation(result, RuleId.FORBIDDEN_PACKAGE)
    }

    @Test fun `domain model requires configured suffix`() {
        val result = run(
            ArchitectureConfiguration(domainModelSuffix = "Domain"),
            "domain/device/src/main/kotlin/io/acme/domain/device/model/Device.kt",
            "package io.acme.domain.device.model\nclass Device",
        )
        assertViolation(result, RuleId.DOMAIN_MODEL_SUFFIX)
    }

    @Test fun `repository operations must be suspend`() {
        val result = run(
            ArchitectureConfiguration(requireSuspendRepositoryFunctions = true),
            "src/main/kotlin/io/acme/repository/DeviceRepository.kt",
            "package io.acme.repository\ninterface DeviceRepository {\n fun find(): String\n suspend fun save()\n}",
        )
        val violations = result.violations.filter { it.ruleId == RuleId.REPOSITORY_SUSPEND.code }
        assertEquals(1, violations.size)
        assertTrue(violations.single().message.contains("find"))
    }

    @Test fun `repository operation may expose an allowed Flow without suspend`() {
        val result = run(
            ArchitectureConfiguration(
                requireSuspendRepositoryFunctions = true,
                allowedNonSuspendRepositoryReturnTypes = setOf("Flow"),
            ),
            "src/main/kotlin/io/acme/repository/DeviceRepository.kt",
            "package io.acme.repository\ninterface DeviceRepository {\n fun observe(): Flow<String>\n}",
        )
        assertTrue(result.violations.none { it.ruleId == RuleId.REPOSITORY_SUSPEND.code })
    }

    @Test fun `forbidden serializer is detected`() {
        val result = run(
            ArchitectureConfiguration(forbiddenSerialization = setOf("com.fasterxml.jackson"), allowedSerialization = setOf("kotlinx.serialization")),
            "src/main/kotlin/io/acme/Foo.kt", "package io.acme\nimport com.fasterxml.jackson.databind.ObjectMapper\nclass Foo",
        )
        assertViolation(result, RuleId.SERIALIZATION)
    }

    @Test fun `layer imports respect allowed dependency graph`() {
        val configuration = ArchitectureConfiguration(layers = listOf(
            LayerConfiguration(
                name = "domain",
                mayDependOn = emptySet(),
            ),
            LayerConfiguration(
                name = "infrastructure",
                mayDependOn = setOf("domain"),
            ),
        ))
        val result = run(
            configuration, "domain/src/main/kotlin/io/acme/domain/Foo.kt",
            "package io.acme.domain\nimport io.acme.infrastructure.Database\nclass Foo",
        )
        assertViolation(result, RuleId.LAYER_DEPENDENCY)
    }

    @Test fun `comments and strings do not create fake declarations`() {
        val source = """
            package io.acme
            // class Fake
            val text = "interface AlsoFake"
            class Real { object Nested }
        """.trimIndent()
        assertEquals(listOf("Real"), parser.parse(root.resolve("Real.kt"), root, source).topLevelTypes.map { it.name })
    }

    @Test fun `Gradle modules respect explicit allowed dependencies`() {
        val configuration = ArchitectureConfiguration(
            modules = listOf(
                ModuleConfiguration(
                    path = ":domain",
                    mayDependOn = setOf(":shared"),
                ),
            ),
        )
        val context = ArchitectureContext(
            rootDirectory = root,
            sources = emptyList(),
            configuration = configuration,
            moduleDependencies = listOf(
                ModuleDependency(
                    from = ":domain",
                    to = ":infrastructure",
                    buildFile = Path.of("domain/build.gradle.kts"),
                ),
            ),
        )
        assertViolation(ArchitectureEngine().check(context), RuleId.GRADLE_MODULE_DEPENDENCY)
    }

    @Test fun `Gradle module patterns support feature families`() {
        val configuration = ArchitectureConfiguration(
            modulePatterns = listOf(
                ModulePatternConfiguration(
                    pathPattern = ":features:*:impl",
                    mayDependOnPatterns = setOf(":features:*:api", ":core:**"),
                ),
            ),
        )
        val allowed = ArchitectureContext(
            rootDirectory = root,
            sources = emptyList(),
            configuration = configuration,
            moduleDependencies = listOf(
                ModuleDependency(
                    from = ":features:home:impl",
                    to = ":features:editor:api",
                    buildFile = Path.of("features/home/impl/build.gradle.kts"),
                ),
            ),
        )
        assertTrue(ArchitectureEngine().check(allowed).isSuccessful)
    }

    private fun run(configuration: ArchitectureConfiguration, relative: String, source: String): ArchitectureResult {
        val file = root.resolve(relative)
        return ArchitectureEngine().check(
            ArchitectureContext(
                rootDirectory = root,
                sources = listOf(parser.parse(file, root, source)),
                configuration = configuration,
            ),
        )
    }

    private fun assertViolation(result: ArchitectureResult, id: RuleId, line: Int? = null) {
        val violation = result.violations.firstOrNull { it.ruleId == id.code }
        assertTrue(violation != null, "Expected ${id.code}, got ${result.violations}")
        if (line != null) assertEquals(line, violation.line)
    }
}
