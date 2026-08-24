package br.com.nejmenn.architecture.core

class CanonicalPackageRule : ArchitectureRule {
    override fun check(context: ArchitectureContext): List<ArchitectureViolation> {
        val base = context.configuration.basePackage?.takeIf { it.isNotBlank() } ?: return emptyList()
        return context.sources.mapNotNull { source ->
            if (source.packageName == base || source.packageName?.startsWith("$base.") == true) null
            else violation(RuleId.CANONICAL_PACKAGE, source, source.packageLine, "Package '${source.packageName ?: "<missing>"}' must be inside '$base'.")
        }
    }
}

class PackagePathRule : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources.mapNotNull { source ->
        val packageName = source.packageName ?: return@mapNotNull null
        val expected = packageName.replace('.', '/') + "/${source.file.fileName}"
        val actual = source.relativePath.toString().replace('\\', '/')
        if (actual.endsWith(expected)) null
        else violation(RuleId.PACKAGE_PATH, source, source.packageLine, "Source path must mirror package '$packageName'.", recommendation = expected)
    }
}

class ForbiddenPackageRule : ArchitectureRule {
    override fun check(context: ArchitectureContext) = context.sources.mapNotNull { source ->
        val packageName = source.packageName.orEmpty()
        val path = "/${source.relativePath.toString().replace('\\', '/')}"
        context.configuration.forbiddenPackages.firstOrNull { forbidden ->
            val normalized = forbidden.packageFragment.trim('/', '.')
            packageName.split('.').contains(normalized) || path.contains("/$normalized/")
        }?.let { forbidden ->
            violation(
                RuleId.FORBIDDEN_PACKAGE, source, source.packageLine,
                "Package '${forbidden.packageFragment}' is forbidden.",
                recommendation = forbidden.recommendation,
            )
        }
    }
}

class DomainModelSuffixRule : ArchitectureRule {
    override fun check(context: ArchitectureContext): List<ArchitectureViolation> {
        val suffix = context.configuration.domainModelSuffix ?: return emptyList()
        return context.sources.flatMap { source ->
            val path = "/${source.relativePath.toString().replace('\\', '/')}"
            val packageName = source.packageName.orEmpty()
            val isDomainModel = path.contains(context.configuration.domainModelPathFragment) &&
                (packageName.endsWith(context.configuration.domainModelPackageFragment) ||
                    packageName.contains("${context.configuration.domainModelPackageFragment}."))
            if (!isDomainModel) emptyList() else source.topLevelTypes.mapNotNull { type ->
                if (type.name.endsWith(suffix)) null else violation(
                    RuleId.DOMAIN_MODEL_SUFFIX, source, type.line,
                    "Domain model '${type.name}' must end with '$suffix'.",
                    recommendation = type.name + suffix,
                )
            }
        }
    }
}

