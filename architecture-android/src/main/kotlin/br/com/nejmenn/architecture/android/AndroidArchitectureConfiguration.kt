package br.com.nejmenn.architecture.android

enum class DomainPurity { STRICT, PRAGMATIC }

data class ExternalDependencyBoundary(
    val technology: String,
    val coordinatePrefixes: Set<String>,
    val allowedModulePatterns: Set<String>,
)

data class AndroidArchitectureConfiguration(
    val domainPurity: DomainPurity = DomainPurity.STRICT,
    val domainModulePatterns: Set<String> = setOf(":core:domain"),
    val domainAllowedImports: Set<String> = setOf(
        "javax.inject.",
        "kotlinx.coroutines.",
        "kotlinx.serialization.",
    ),
    val featureApiModulePattern: String = ":features:*:api",
    val featureImplementationModulePattern: String = ":features:*:impl",
    val composeAllowedModulePatterns: Set<String> = setOf(
        ":app",
        ":widgets",
        ":core:ui",
        ":core:presentation",
        ":features:*:impl",
    ),
    val roomAllowedModulePatterns: Set<String> = setOf(":app", ":core:data"),
    val androidComponentAllowedModulePatterns: Set<String> = setOf(":app", ":widgets"),
    val externalDependencyBoundaries: List<ExternalDependencyBoundary> = defaultExternalBoundaries(),
) {
    companion object {
        fun defaultExternalBoundaries() = listOf(
            ExternalDependencyBoundary(
                technology = "Jetpack Compose",
                coordinatePrefixes = setOf("androidx.compose.", "com.google.android.material:"),
                allowedModulePatterns = setOf(
                    ":app",
                    ":widgets",
                    ":core:ui",
                    ":core:presentation",
                    ":features:*:impl",
                ),
            ),
            ExternalDependencyBoundary(
                technology = "Room",
                coordinatePrefixes = setOf("androidx.room:"),
                allowedModulePatterns = setOf(":app", ":core:data"),
            ),
        )
    }
}

