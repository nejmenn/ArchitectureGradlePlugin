package br.com.nejmenn.architecture.core

import java.nio.file.Path

enum class Severity { INFO, WARNING, ERROR }

enum class RuleId(val code: String, val title: String) {
    LAYER_DEPENDENCY("ARCH-001", "Forbidden Layer Dependency"),
    FORBIDDEN_IMPORT("ARCH-002", "Forbidden Import"),
    CANONICAL_PACKAGE("ARCH-003", "Canonical Package"),
    PACKAGE_PATH("ARCH-004", "Package Path"),
    ONE_TYPE_PER_FILE("ARCH-005", "One Type Per File"),
    FORBIDDEN_TYPE_SUFFIX("ARCH-006", "Forbidden Type Suffix"),
    FILENAME_MATCHES_TYPE("ARCH-007", "Filename Matches Type"),
    FORBIDDEN_PACKAGE("ARCH-008", "Forbidden Package"),
    DOMAIN_MODEL_SUFFIX("ARCH-009", "Domain Model Suffix"),
    REPOSITORY_SUSPEND("ARCH-010", "Repository Suspend Functions"),
    SERIALIZATION("ARCH-011", "Serialization Technology"),
    GRADLE_MODULE_DEPENDENCY("ARCH-012", "Gradle Module Dependency"),
    ANDROID_FRAMEWORK_BOUNDARY("ARCH-013", "Android Framework Boundary"),
    FEATURE_IMPLEMENTATION_LEAK("ARCH-014", "Feature Implementation Leakage"),
    FEATURE_API_BOUNDARY("ARCH-015", "Feature API Boundary"),
    ANDROID_TECHNOLOGY_PLACEMENT("ARCH-016", "Android Technology Placement"),
    GRADLE_EXTERNAL_DEPENDENCY("ARCH-017", "Gradle External Dependency Boundary"),
    ANDROID_COMPONENT_PLACEMENT("ARCH-018", "Android Component Placement"),
}

data class ArchitectureViolation(
    val ruleId: String,
    val file: Path,
    val line: Int? = null,
    val message: String,
    val severity: Severity = Severity.ERROR,
    val evidence: String? = null,
    val recommendation: String? = null,
)

data class ArchitectureResult(val violations: List<ArchitectureViolation>) {
    val errors: List<ArchitectureViolation> get() = violations.filter { it.severity == Severity.ERROR }
    val isSuccessful: Boolean get() = errors.isEmpty()
}

data class SourceImport(val qualifiedName: String, val line: Int)
data class TopLevelType(val name: String, val kind: String, val line: Int)
data class SourceFunction(
    val name: String,
    val line: Int,
    val isSuspend: Boolean,
    val braceDepth: Int,
    val returnType: String? = null,
)

data class AnalyzedSource(
    val file: Path,
    val relativePath: Path,
    val content: String,
    val packageName: String?,
    val packageLine: Int?,
    val imports: List<SourceImport>,
    val topLevelTypes: List<TopLevelType>,
    val functions: List<SourceFunction>,
    val modulePath: String? = null,
)

data class ForbiddenSuffix(val suffix: String, val recommendation: String? = null)
data class ForbiddenPackage(val packageFragment: String, val recommendation: String? = null)

data class LayerConfiguration(
    val name: String,
    val packageFragments: Set<String> = setOf(name),
    val mayDependOn: Set<String> = emptySet(),
    val forbiddenImports: Set<String> = emptySet(),
)

data class ModuleConfiguration(val path: String, val mayDependOn: Set<String> = emptySet())
data class ModulePatternConfiguration(
    val pathPattern: String,
    val mayDependOnPatterns: Set<String> = emptySet(),
)

data class ModuleDependency(
    val from: String,
    val to: String,
    val buildFile: Path,
    val configurationName: String = "implementation",
)

data class DeclaredExternalDependency(
    val from: String,
    val configurationName: String,
    val group: String,
    val name: String,
    val buildFile: Path,
) {
    val coordinate: String get() = "$group:$name"
}

data class ArchitectureConfiguration(
    val basePackage: String? = null,
    val forbiddenImports: Set<String> = emptySet(),
    val oneTypePerFile: Boolean = true,
    val filenameMustMatchType: Boolean = true,
    val forbiddenSuffixes: List<ForbiddenSuffix> = emptyList(),
    val forbiddenPackages: List<ForbiddenPackage> = emptyList(),
    val domainModelPathFragment: String = "/domain/",
    val domainModelPackageFragment: String = ".model",
    val domainModelSuffix: String? = null,
    val requireSuspendRepositoryFunctions: Boolean = false,
    val repositoryPackageFragments: Set<String> = setOf(".repository"),
    val allowedNonSuspendRepositoryReturnTypes: Set<String> = emptySet(),
    val allowedSerialization: Set<String> = emptySet(),
    val forbiddenSerialization: Set<String> = emptySet(),
    val layers: List<LayerConfiguration> = emptyList(),
    val modules: List<ModuleConfiguration> = emptyList(),
    val modulePatterns: List<ModulePatternConfiguration> = emptyList(),
)

data class ArchitectureContext(
    val rootDirectory: Path,
    val sources: List<AnalyzedSource>,
    val configuration: ArchitectureConfiguration,
    val moduleDependencies: List<ModuleDependency> = emptyList(),
    val externalDependencies: List<DeclaredExternalDependency> = emptyList(),
)

fun modulePathMatches(pattern: String, path: String): Boolean {
    val patternSegments = pattern.trim(':').split(':').filter(String::isNotBlank)
    val pathSegments = path.trim(':').split(':').filter(String::isNotBlank)
    if (patternSegments.lastOrNull() == "**") {
        val prefix = patternSegments.dropLast(1)
        return pathSegments.size >= prefix.size && prefix.indices.all { index ->
            prefix[index] == "*" || prefix[index] == pathSegments[index]
        }
    }
    return patternSegments.size == pathSegments.size && patternSegments.indices.all { index ->
        patternSegments[index] == "*" || patternSegments[index] == pathSegments[index]
    }
}

fun interface ArchitectureRule {
    fun check(context: ArchitectureContext): List<ArchitectureViolation>
}
