package br.com.nejmenn.architecture.android

import br.com.nejmenn.architecture.core.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidArchitectureRulesTest {
    private val root = Path.of("/project")
    private val parser = KotlinSourceParser()

    @Test fun `strict domain cannot import Android framework`() {
        val result = run(
            rule = AndroidFrameworkBoundaryRule(AndroidArchitectureConfiguration()),
            modulePath = ":core:domain",
            source = "package io.sample.domain\nimport android.content.Context\nclass Account",
        )
        assertRule(result, RuleId.ANDROID_FRAMEWORK_BOUNDARY)
    }

    @Test fun `pragmatic domain allows Parcelable`() {
        val configuration = AndroidArchitectureConfiguration(domainPurity = DomainPurity.PRAGMATIC)
        val result = run(
            rule = AndroidFrameworkBoundaryRule(configuration),
            modulePath = ":core:domain",
            source = "package io.sample.domain\nimport android.os.Parcelable\nclass Account",
        )
        assertTrue(result.violations.isEmpty())
    }

    @Test fun `one feature cannot import another feature implementation`() {
        val result = run(
            rule = FeatureImplementationLeakRule(AndroidArchitectureConfiguration()),
            modulePath = ":features:home:impl",
            source = "package io.sample.features.home.impl\nimport io.sample.features.editor.impl.EditorScreen\nclass HomeScreen",
        )
        assertRule(result, RuleId.FEATURE_IMPLEMENTATION_LEAK)
    }

    @Test fun `feature API cannot expose Compose implementation`() {
        val result = run(
            rule = FeatureApiBoundaryRule(AndroidArchitectureConfiguration()),
            modulePath = ":features:home:api",
            source = "package io.sample.features.home.api\nimport androidx.compose.runtime.Composable\ninterface HomeApi",
        )
        assertRule(result, RuleId.FEATURE_API_BOUNDARY)
    }

    @Test fun `Room is restricted to configured modules`() {
        val result = run(
            rule = AndroidTechnologyPlacementRule(AndroidArchitectureConfiguration()),
            modulePath = ":core:domain",
            source = "package io.sample.domain\nimport androidx.room.Entity\nclass Account",
        )
        assertRule(result, RuleId.ANDROID_TECHNOLOGY_PLACEMENT)
    }

    @Test fun `external Compose dependency is checked at Gradle boundary`() {
        val configuration = AndroidArchitectureConfiguration()
        val context = ArchitectureContext(
            rootDirectory = root,
            sources = emptyList(),
            configuration = ArchitectureConfiguration(),
            externalDependencies = listOf(
                DeclaredExternalDependency(
                    from = ":core:domain",
                    configurationName = "implementation",
                    group = "androidx.compose.runtime",
                    name = "runtime",
                    buildFile = Path.of("core/domain/build.gradle.kts"),
                ),
            ),
        )
        val result = ArchitectureEngine(listOf(GradleExternalDependencyBoundaryRule(configuration))).check(context)
        assertRule(result, RuleId.GRADLE_EXTERNAL_DEPENDENCY)
    }

    @Test fun `Android entry points are restricted to app and widgets`() {
        val result = run(
            rule = AndroidComponentPlacementRule(AndroidArchitectureConfiguration()),
            modulePath = ":core:data",
            source = "package io.sample.data\nimport android.content.BroadcastReceiver\nclass SyncReceiver",
        )
        assertRule(result, RuleId.ANDROID_COMPONENT_PLACEMENT)
    }

    @Test fun `preset includes Android source roots and feature module patterns`() {
        val preset = AndroidArchitecturePresets.named("android-clean-feature")
        assertTrue("**/src/main/java/**/*.kt" in preset.core.sourceIncludes)
        assertTrue(preset.core.modulePatterns.any { it.pathPattern == ":features:*:impl" })
        assertEquals(DomainPurity.STRICT, preset.android.domainPurity)
    }

    private fun run(rule: ArchitectureRule, modulePath: String, source: String): ArchitectureResult {
        val file = root.resolve("src/main/java/io/sample/Test.kt")
        val analyzed = parser.parse(
            file = file,
            root = root,
            content = source,
            modulePath = modulePath,
        )
        return ArchitectureEngine(listOf(rule)).check(
            ArchitectureContext(
                rootDirectory = root,
                sources = listOf(analyzed),
                configuration = ArchitectureConfiguration(),
            ),
        )
    }

    private fun assertRule(result: ArchitectureResult, ruleId: RuleId) {
        assertTrue(result.violations.any { it.ruleId == ruleId.code }, "Expected ${ruleId.code}: ${result.violations}")
    }
}

