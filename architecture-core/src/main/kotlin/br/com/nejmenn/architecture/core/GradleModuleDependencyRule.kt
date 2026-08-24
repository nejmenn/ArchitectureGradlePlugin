package br.com.nejmenn.architecture.core

class GradleModuleDependencyRule : ArchitectureRule {
    override fun check(context: ArchitectureContext): List<ArchitectureViolation> {
        val exactRules = context.configuration.modules.associateBy { it.path }
        return context.moduleDependencies.mapNotNull { dependency ->
            if (dependency.from == dependency.to) return@mapNotNull null
            val allowedPatterns = exactRules[dependency.from]?.mayDependOn
                ?: context.configuration.modulePatterns
                    .firstOrNull { modulePathMatches(it.pathPattern, dependency.from) }
                    ?.mayDependOnPatterns
                ?: return@mapNotNull null
            if (allowedPatterns.any { modulePathMatches(it, dependency.to) }) null else ArchitectureViolation(
                ruleId = RuleId.GRADLE_MODULE_DEPENDENCY.code,
                file = dependency.buildFile,
                message = "Gradle module '${dependency.from}' cannot depend on '${dependency.to}'.",
                evidence = "${dependency.configurationName}(project(\"${dependency.to}\"))",
                severity = Severity.ERROR,
            )
        }
    }
}
