package br.com.nejmenn.architecture.core

class ForbiddenImportRule : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources.flatMap { source ->
        source.imports.mapNotNull { import ->
            context.configuration.forbiddenImports.firstOrNull(import.qualifiedName::startsWith)?.let { forbidden ->
                violation(RuleId.FORBIDDEN_IMPORT, source, import.line, "Import '$forbidden' is forbidden.", "import ${import.qualifiedName}")
            }
        }
    }
}

class SerializationRule : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources.flatMap { source ->
        source.imports.mapNotNull { import ->
            context.configuration.forbiddenSerialization.firstOrNull(import.qualifiedName::startsWith)?.let { forbidden ->
                violation(
                    RuleId.SERIALIZATION, source, import.line,
                    "Serialization technology '$forbidden' is forbidden.",
                    "import ${import.qualifiedName}",
                    context.configuration.allowedSerialization.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Use "),
                )
            }
        }
    }
}

class LayerDependencyRule : ArchitectureRule {
    override fun check(context: ArchitectureContext): List<ArchitectureViolation> {
        val configuration = context.configuration
        return context.sources.flatMap { source ->
            val sourceLayer = configuration.layers.firstOrNull { layer ->
                layer.packageFragments.any { matchesPackageFragment(source.packageName, it) }
            } ?: return@flatMap emptyList()

            source.imports.mapNotNull { import ->
                val targetLayer = configuration.layers.firstOrNull { layer ->
                    layer.packageFragments.any { matchesPackageFragment(import.qualifiedName, it) }
                }
                val forbiddenExternal = sourceLayer.forbiddenImports.firstOrNull(import.qualifiedName::startsWith)
                when {
                    forbiddenExternal != null -> violation(
                        RuleId.LAYER_DEPENDENCY, source, import.line,
                        "Layer '${sourceLayer.name}' cannot depend on '$forbiddenExternal'.",
                        "import ${import.qualifiedName}",
                    )
                    targetLayer != null && targetLayer.name != sourceLayer.name && targetLayer.name !in sourceLayer.mayDependOn -> violation(
                        RuleId.LAYER_DEPENDENCY, source, import.line,
                        "Layer '${sourceLayer.name}' cannot depend on layer '${targetLayer.name}'.",
                        "import ${import.qualifiedName}",
                    )
                    else -> null
                }
            }
        }
    }

    private fun matchesPackageFragment(packageName: String?, fragment: String): Boolean {
        if (packageName == null) return false
        val normalized = fragment.trim('.', '/')
        return packageName == normalized || packageName.contains(".$normalized.") || packageName.endsWith(".$normalized")
    }
}

internal fun violation(
    id: RuleId,
    source: AnalyzedSource,
    line: Int?,
    message: String,
    evidence: String? = null,
    recommendation: String? = null,
) = ArchitectureViolation(
    ruleId = id.code,
    file = source.relativePath,
    line = line,
    message = message,
    severity = Severity.ERROR,
    evidence = evidence,
    recommendation = recommendation,
)
