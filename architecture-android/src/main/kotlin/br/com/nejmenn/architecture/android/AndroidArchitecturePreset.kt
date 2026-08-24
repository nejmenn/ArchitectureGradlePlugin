package br.com.nejmenn.architecture.android

import br.com.nejmenn.architecture.core.ModulePatternConfiguration
import br.com.nejmenn.architecture.presets.PresetConfiguration

data class AndroidPresetConfiguration(
    val core: PresetConfiguration,
    val android: AndroidArchitectureConfiguration,
)

object AndroidArchitecturePresets {
    private val supportedNames = setOf("android-clean-feature")

    fun supports(name: String): Boolean = name in supportedNames

    fun named(name: String): AndroidPresetConfiguration {
        require(supports(name)) {
            "Unknown Android architecture preset '$name'. Available: ${supportedNames.sorted().joinToString()}"
        }
        return AndroidPresetConfiguration(
            core = PresetConfiguration(
                requireSuspendRepositories = true,
                allowedNonSuspendRepositoryReturnTypes = setOf("Flow", "StateFlow", "SharedFlow"),
                modulePatterns = listOf(
                    ModulePatternConfiguration(
                        pathPattern = ":core:utils",
                        mayDependOnPatterns = emptySet(),
                    ),
                    ModulePatternConfiguration(
                        pathPattern = ":core:domain",
                        mayDependOnPatterns = setOf(":core:utils"),
                    ),
                    ModulePatternConfiguration(
                        pathPattern = ":core:data",
                        mayDependOnPatterns = setOf(":core:utils", ":core:domain"),
                    ),
                    ModulePatternConfiguration(
                        pathPattern = ":core:ui",
                        mayDependOnPatterns = setOf(":core:utils", ":core:domain"),
                    ),
                    ModulePatternConfiguration(
                        pathPattern = ":core:presentation",
                        mayDependOnPatterns = setOf(":core:utils", ":core:domain", ":core:ui"),
                    ),
                    ModulePatternConfiguration(
                        pathPattern = ":features:*:api",
                        mayDependOnPatterns = setOf(":core:utils", ":core:domain"),
                    ),
                    ModulePatternConfiguration(
                        pathPattern = ":features:*:impl",
                        mayDependOnPatterns = setOf(":core:**", ":features:*:api"),
                    ),
                    ModulePatternConfiguration(
                        pathPattern = ":widgets",
                        mayDependOnPatterns = setOf(":core:**", ":features:*:api"),
                    ),
                    ModulePatternConfiguration(
                        pathPattern = ":app",
                        mayDependOnPatterns = setOf(":**"),
                    ),
                ),
                sourceIncludes = setOf(
                    "**/src/main/java/**/*.kt",
                    "**/src/main/kotlin/**/*.kt",
                ),
                sourceExcludes = setOf(
                    "**/build/**",
                    "**/generated/**",
                    "**/ksp/**",
                ),
            ),
            android = AndroidArchitectureConfiguration(),
        )
    }
}
