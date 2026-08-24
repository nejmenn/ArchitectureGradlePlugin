package br.com.nejmenn.architecture.core

class ArchitectureEngine(private val rules: List<ArchitectureRule> = DefaultRules.all) {
    fun check(context: ArchitectureContext): ArchitectureResult =
        ArchitectureResult(rules.flatMap { it.check(context) }.sortedWith(compareBy({ it.file.toString() }, { it.line ?: 0 }, { it.ruleId })))
}

object DefaultRules {
    val all: List<ArchitectureRule> = listOf(
        LayerDependencyRule(), ForbiddenImportRule(), CanonicalPackageRule(), PackagePathRule(),
        OneTypePerFileRule(), ForbiddenTypeSuffixRule(), FilenameMatchesTypeRule(),
        ForbiddenPackageRule(), DomainModelSuffixRule(), RepositorySuspendRule(), SerializationRule(),
        GradleModuleDependencyRule(),
    )
}

