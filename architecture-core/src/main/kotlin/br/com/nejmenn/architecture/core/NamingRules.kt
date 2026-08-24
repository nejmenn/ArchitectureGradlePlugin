package br.com.nejmenn.architecture.core

class OneTypePerFileRule : ArchitectureRule {
    override fun check(context: ArchitectureContext): List<ArchitectureViolation> {
        if (!context.configuration.oneTypePerFile) return emptyList()
        return context.sources.mapNotNull { source ->
            if (source.topLevelTypes.size <= 1) null else violation(
                RuleId.ONE_TYPE_PER_FILE, source, source.topLevelTypes[1].line,
                "File declares ${source.topLevelTypes.size} top-level types; only one is allowed.",
                evidence = source.topLevelTypes.joinToString { it.name },
            )
        }
    }
}

class FilenameMatchesTypeRule : ArchitectureRule {
    override fun check(context: ArchitectureContext): List<ArchitectureViolation> {
        if (!context.configuration.filenameMustMatchType) return emptyList()
        return context.sources.mapNotNull { source ->
            val type = source.topLevelTypes.singleOrNull() ?: return@mapNotNull null
            val expected = "${type.name}.kt"
            if (source.file.fileName.toString() == expected) null else violation(
                RuleId.FILENAME_MATCHES_TYPE, source, type.line,
                "Filename must match the single top-level type '${type.name}'.", recommendation = expected,
            )
        }
    }
}

class ForbiddenTypeSuffixRule : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources.flatMap { source ->
        source.topLevelTypes.mapNotNull { type ->
            context.configuration.forbiddenSuffixes.firstOrNull { type.name.endsWith(it.suffix) }?.let { forbidden ->
                val replacement = forbidden.recommendation?.let { type.name.removeSuffix(forbidden.suffix) + it }
                violation(
                    RuleId.FORBIDDEN_TYPE_SUFFIX, source, type.line,
                    "Types ending with '${forbidden.suffix}' are forbidden.",
                    evidence = type.name,
                    recommendation = replacement,
                )
            }
        }
    }
}

class RepositorySuspendRule : ArchitectureRule {
    override fun check(context: ArchitectureContext): List<ArchitectureViolation> {
        if (!context.configuration.requireSuspendRepositoryFunctions) return emptyList()
        return context.sources.flatMap { source ->
            val packageName = source.packageName.orEmpty()
            if (context.configuration.repositoryPackageFragments.none { fragment ->
                    val normalized = fragment.trim('.')
                    packageName == normalized || packageName.contains(".$normalized.") || packageName.endsWith(".$normalized")
                }) return@flatMap emptyList()
            source.functions.filterNot { function ->
                function.isSuspend || context.configuration.allowedNonSuspendRepositoryReturnTypes.any { allowedType ->
                    function.returnType == allowedType || function.returnType?.endsWith(".$allowedType") == true
                }
            }.map { function ->
                violation(
                    RuleId.REPOSITORY_SUSPEND, source, function.line,
                    "Repository operation '${function.name}' must be declared suspend.",
                    recommendation = "suspend fun ${function.name}",
                )
            }
        }
    }
}
