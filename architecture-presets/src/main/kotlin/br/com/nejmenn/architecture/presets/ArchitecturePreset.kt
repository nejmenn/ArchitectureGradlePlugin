package br.com.nejmenn.architecture.presets

import br.com.nejmenn.architecture.core.ModulePatternConfiguration

data class PresetConfiguration(
    val forbiddenSerialization: Set<String> = emptySet(),
    val allowedSerialization: Set<String> = emptySet(),
    val forbiddenSuffixes: Map<String, String?> = emptyMap(),
    val forbiddenPackages: Map<String, String?> = emptyMap(),
    val domainModelSuffix: String? = null,
    val requireSuspendRepositories: Boolean = false,
    val allowedNonSuspendRepositoryReturnTypes: Set<String> = emptySet(),
    val layers: List<PresetLayer> = emptyList(),
    val modulePatterns: List<ModulePatternConfiguration> = emptyList(),
    val sourceIncludes: Set<String> = emptySet(),
    val sourceExcludes: Set<String> = emptySet(),
)

data class PresetLayer(
    val name: String,
    val mayDependOn: Set<String>,
    val forbiddenImports: Set<String> = emptySet(),
)

fun interface ArchitecturePreset {
    fun configuration(): PresetConfiguration
}

object ArchitecturePresets {
    private val presets = mapOf(
        "spring-hexagonal" to SpringHexagonalPreset,
    )

    fun named(name: String): ArchitecturePreset = presets[name]
        ?: error("Unknown architecture preset '$name'. Available: ${presets.keys.sorted().joinToString()}")
}

object SpringHexagonalPreset : ArchitecturePreset {
    override fun configuration() = PresetConfiguration(
        forbiddenSerialization = setOf("com.fasterxml.jackson", "org.codehaus.jackson"),
        allowedSerialization = setOf("kotlinx.serialization"),
        forbiddenSuffixes = mapOf("Service" to "UseCase"),
        forbiddenPackages = mapOf("service" to "usecase"),
        domainModelSuffix = "Domain",
        requireSuspendRepositories = true,
        layers = listOf(
            PresetLayer(
                name = "domain",
                mayDependOn = emptySet(),
                forbiddenImports = setOf("org.springframework."),
            ),
            PresetLayer(
                name = "application",
                mayDependOn = setOf("domain"),
            ),
            PresetLayer(
                name = "adapter",
                mayDependOn = setOf("application", "domain"),
            ),
            PresetLayer(
                name = "infrastructure",
                mayDependOn = setOf("application", "domain"),
            ),
            PresetLayer(
                name = "web",
                mayDependOn = setOf("application", "domain"),
            ),
        ),
    )
}
