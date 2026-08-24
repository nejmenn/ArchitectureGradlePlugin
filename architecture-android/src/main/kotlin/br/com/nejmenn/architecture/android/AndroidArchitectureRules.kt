package br.com.nejmenn.architecture.android

import br.com.nejmenn.architecture.core.*

object AndroidArchitectureRules {
    fun all(configuration: AndroidArchitectureConfiguration): List<ArchitectureRule> = listOf(
        AndroidFrameworkBoundaryRule(configuration),
        FeatureImplementationLeakRule(configuration),
        FeatureApiBoundaryRule(configuration),
        AndroidTechnologyPlacementRule(configuration),
        GradleExternalDependencyBoundaryRule(configuration),
        AndroidComponentPlacementRule(configuration),
    )
}

class AndroidFrameworkBoundaryRule(
    private val configuration: AndroidArchitectureConfiguration,
) : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources
        .filter { source -> configuration.domainModulePatterns.any { modulePathMatches(it, source.modulePath.orEmpty()) } }
        .flatMap { source ->
            source.imports.mapNotNull { sourceImport ->
                val isAndroid = sourceImport.qualifiedName.startsWith("android.") ||
                    sourceImport.qualifiedName.startsWith("androidx.")
                val pragmaticException = configuration.domainPurity == DomainPurity.PRAGMATIC &&
                    sourceImport.qualifiedName.startsWith("android.os.Parcelable")
                val explicitlyAllowed = configuration.domainAllowedImports.any(sourceImport.qualifiedName::startsWith)
                if (!isAndroid || pragmaticException || explicitlyAllowed) null else androidViolation(
                    ruleId = RuleId.ANDROID_FRAMEWORK_BOUNDARY,
                    source = source,
                    line = sourceImport.line,
                    message = "Domain module '${source.modulePath}' cannot depend on the Android framework.",
                    evidence = "import ${sourceImport.qualifiedName}",
                    recommendation = "Move the Android-specific type to data, UI, presentation, or an adapter module.",
                )
            }
        }
}

class FeatureImplementationLeakRule(
    private val configuration: AndroidArchitectureConfiguration,
) : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources.flatMap { source ->
        val sourceFeature = source.modulePath.featureName()
        source.imports.mapNotNull { sourceImport ->
            val targetFeature = sourceImport.qualifiedName.featureImplementationName() ?: return@mapNotNull null
            val isOwnImplementation = source.modulePath?.let {
                modulePathMatches(configuration.featureImplementationModulePattern, it) && sourceFeature == targetFeature
            } == true
            if (isOwnImplementation) null else androidViolation(
                ruleId = RuleId.FEATURE_IMPLEMENTATION_LEAK,
                source = source,
                line = sourceImport.line,
                message = "Feature implementation '$targetFeature' is private and cannot be imported by '${source.modulePath}'.",
                evidence = "import ${sourceImport.qualifiedName}",
                recommendation = "Depend on the feature's api module instead.",
            )
        }
    }
}

class FeatureApiBoundaryRule(
    private val configuration: AndroidArchitectureConfiguration,
) : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources
        .filter { source -> modulePathMatches(configuration.featureApiModulePattern, source.modulePath.orEmpty()) }
        .flatMap { source ->
            source.imports.mapNotNull { sourceImport ->
                val forbidden = sourceImport.qualifiedName.contains(".impl.") ||
                    sourceImport.qualifiedName.startsWith("androidx.compose.") ||
                    sourceImport.qualifiedName.startsWith("androidx.room.")
                if (!forbidden) null else androidViolation(
                    ruleId = RuleId.FEATURE_API_BOUNDARY,
                    source = source,
                    line = sourceImport.line,
                    message = "Feature API modules must expose framework-light public contracts.",
                    evidence = "import ${sourceImport.qualifiedName}",
                    recommendation = "Move this implementation detail to the matching feature impl module.",
                )
            }
        }
}

class AndroidTechnologyPlacementRule(
    private val configuration: AndroidArchitectureConfiguration,
) : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources.flatMap { source ->
        source.imports.mapNotNull { sourceImport ->
            val (technology, allowedPatterns) = when {
                sourceImport.qualifiedName.startsWith("androidx.compose.") ->
                    "Jetpack Compose" to configuration.composeAllowedModulePatterns
                sourceImport.qualifiedName.startsWith("androidx.room.") ->
                    "Room" to configuration.roomAllowedModulePatterns
                else -> return@mapNotNull null
            }
            if (allowedPatterns.any { modulePathMatches(it, source.modulePath.orEmpty()) }) null else androidViolation(
                ruleId = RuleId.ANDROID_TECHNOLOGY_PLACEMENT,
                source = source,
                line = sourceImport.line,
                message = "$technology is not allowed in module '${source.modulePath}'.",
                evidence = "import ${sourceImport.qualifiedName}",
                recommendation = "Move this code to one of: ${allowedPatterns.sorted().joinToString()}.",
            )
        }
    }
}

class GradleExternalDependencyBoundaryRule(
    private val configuration: AndroidArchitectureConfiguration,
) : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.externalDependencies.mapNotNull { dependency ->
        val boundary = configuration.externalDependencyBoundaries.firstOrNull { rule ->
            rule.coordinatePrefixes.any(dependency.coordinate::startsWith)
        } ?: return@mapNotNull null
        if (boundary.allowedModulePatterns.any { modulePathMatches(it, dependency.from) }) null else ArchitectureViolation(
            ruleId = RuleId.GRADLE_EXTERNAL_DEPENDENCY.code,
            file = dependency.buildFile,
            message = "${boundary.technology} dependency '${dependency.coordinate}' is not allowed in '${dependency.from}'.",
            severity = Severity.ERROR,
            evidence = "${dependency.configurationName}(\"${dependency.coordinate}\")",
            recommendation = "Allowed modules: ${boundary.allowedModulePatterns.sorted().joinToString()}.",
        )
    }
}

class AndroidComponentPlacementRule(
    private val configuration: AndroidArchitectureConfiguration,
) : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources.flatMap { source ->
        val componentImport = source.imports.firstOrNull { sourceImport ->
            COMPONENT_IMPORT_PREFIXES.any(sourceImport.qualifiedName::startsWith)
        } ?: return@flatMap emptyList()
        if (configuration.androidComponentAllowedModulePatterns.any {
                modulePathMatches(it, source.modulePath.orEmpty())
            }) return@flatMap emptyList()
        source.topLevelTypes.filter { type -> COMPONENT_SUFFIXES.any(type.name::endsWith) }.map { type ->
            androidViolation(
                ruleId = RuleId.ANDROID_COMPONENT_PLACEMENT,
                source = source,
                line = type.line,
                message = "Android component '${type.name}' is not allowed in module '${source.modulePath}'.",
                evidence = "import ${componentImport.qualifiedName}",
                recommendation = "Place Android entry points in ${configuration.androidComponentAllowedModulePatterns.sorted().joinToString()}.",
            )
        }
    }

    private companion object {
        val COMPONENT_IMPORT_PREFIXES = setOf(
            "android.app.Activity",
            "android.app.Service",
            "android.content.BroadcastReceiver",
            "androidx.activity.",
            "androidx.work.",
        )
        val COMPONENT_SUFFIXES = setOf("Activity", "Service", "Receiver", "Worker")
    }
}

private fun androidViolation(
    ruleId: RuleId,
    source: AnalyzedSource,
    line: Int?,
    message: String,
    evidence: String? = null,
    recommendation: String? = null,
) = ArchitectureViolation(
    ruleId = ruleId.code,
    file = source.relativePath,
    line = line,
    message = message,
    severity = Severity.ERROR,
    evidence = evidence,
    recommendation = recommendation,
)

private fun String?.featureName(): String? {
    val segments = this?.trim(':')?.split(':') ?: return null
    val featuresIndex = segments.indexOf("features")
    return segments.getOrNull(featuresIndex + 1)
}

private fun String.featureImplementationName(): String? {
    val segments = split('.')
    val featuresIndex = segments.indexOf("features")
    val feature = segments.getOrNull(featuresIndex + 1) ?: return null
    return feature.takeIf { segments.getOrNull(featuresIndex + 2) == "impl" }
}

