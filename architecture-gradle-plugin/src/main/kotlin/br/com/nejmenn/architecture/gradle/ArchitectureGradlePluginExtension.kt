package br.com.nejmenn.architecture.gradle

import br.com.nejmenn.architecture.android.AndroidArchitectureConfiguration
import br.com.nejmenn.architecture.android.AndroidArchitecturePresets
import br.com.nejmenn.architecture.android.DomainPurity
import br.com.nejmenn.architecture.android.ExternalDependencyBoundary
import br.com.nejmenn.architecture.core.*
import br.com.nejmenn.architecture.presets.ArchitecturePresets
import br.com.nejmenn.architecture.presets.PresetConfiguration

open class ArchitectureGradlePluginExtension {
    var basePackage: String? = null
    var attachToCheck: Boolean = true
    val sources = SourcesSpec()
    val domain = DomainSpec()
    val naming = NamingSpec()
    val repositories = RepositoriesSpec()
    val serialization = SerializationSpec()
    val layers = LayersSpec()
    val modules = ModulesSpec()
    val android = AndroidSpec()
    val forbiddenImports = StringSet()

    fun preset(name: String) {
        if (AndroidArchitecturePresets.supports(name)) {
            val preset = AndroidArchitecturePresets.named(name)
            applyPreset(preset.core)
            android.apply(preset.android)
            return
        }
        applyPreset(ArchitecturePresets.named(name).configuration())
    }

    private fun applyPreset(preset: PresetConfiguration) {
        serialization.allowed.addAll(preset.allowedSerialization)
        serialization.forbidden.addAll(preset.forbiddenSerialization)
        preset.forbiddenSuffixes.forEach { (suffix, recommendation) -> naming.forbiddenSuffix(suffix, recommendation) }
        preset.forbiddenPackages.forEach { (path, recommendation) -> naming.forbiddenPackage(path, recommendation) }
        if (domain.modelSuffix == null) domain.modelSuffix = preset.domainModelSuffix
        if (!repositories.requireSuspendFunctions) repositories.requireSuspendFunctions = preset.requireSuspendRepositories
        repositories.allowedNonSuspendReturnTypes.addAll(preset.allowedNonSuspendRepositoryReturnTypes)
        sources.includes.addAll(preset.sourceIncludes)
        sources.excludes.addAll(preset.sourceExcludes)
        preset.modulePatterns.forEach { modulePattern ->
            modules.modulePattern(modulePattern.pathPattern) {
                mayDependOn.addAll(modulePattern.mayDependOnPatterns)
            }
        }
        preset.layers.forEach { presetLayer ->
            layers.layer(presetLayer.name) {
                mayDependOn.addAll(presetLayer.mayDependOn)
                forbiddenImports.addAll(presetLayer.forbiddenImports)
            }
        }
    }

    fun sources(action: SourcesSpec.() -> Unit) = sources.action()
    fun domain(action: DomainSpec.() -> Unit) = domain.action()
    fun naming(action: NamingSpec.() -> Unit) = naming.action()
    fun repositories(action: RepositoriesSpec.() -> Unit) = repositories.action()
    fun serialization(action: SerializationSpec.() -> Unit) = serialization.action()
    fun layers(action: LayersSpec.() -> Unit) = layers.action()
    fun modules(action: ModulesSpec.() -> Unit) = modules.action()
    fun android(action: AndroidSpec.() -> Unit) = android.action().also { android.enabled = true }

    internal fun toCoreConfiguration(): ArchitectureConfiguration {
        val configuredLayers = layers.values.values.map { layer ->
            LayerConfiguration(
                name = layer.name,
                packageFragments = layer.packageFragments.toSet().ifEmpty { setOf(layer.name) },
                mayDependOn = layer.mayDependOn.toSet(),
                forbiddenImports = layer.forbiddenImports.toSet() + if (layer.name == "domain") domain.forbiddenImports else emptySet(),
            )
        }
        return ArchitectureConfiguration(
            basePackage = basePackage,
            forbiddenImports = forbiddenImports.toSet(),
            oneTypePerFile = naming.oneTypePerFile,
            filenameMustMatchType = naming.filenameMustMatchType,
            forbiddenSuffixes = naming.forbiddenSuffixes.map { (suffix, recommendation) ->
                ForbiddenSuffix(
                    suffix = suffix,
                    recommendation = recommendation,
                )
            },
            forbiddenPackages = naming.forbiddenPackages.map { (packageFragment, recommendation) ->
                ForbiddenPackage(
                    packageFragment = packageFragment,
                    recommendation = recommendation,
                )
            },
            domainModelSuffix = domain.modelSuffix,
            requireSuspendRepositoryFunctions = repositories.requireSuspendFunctions,
            repositoryPackageFragments = repositories.packageFragments.toSet(),
            allowedNonSuspendRepositoryReturnTypes = repositories.allowedNonSuspendReturnTypes.toSet(),
            allowedSerialization = serialization.allowed.toSet(),
            forbiddenSerialization = serialization.forbidden.toSet(),
            layers = configuredLayers,
            modules = modules.values.values.map { module ->
                ModuleConfiguration(
                    path = module.path,
                    mayDependOn = module.allowedDependencies.toSet(),
                )
            },
            modulePatterns = modules.patterns.values.map { pattern ->
                ModulePatternConfiguration(
                    pathPattern = pattern.pathPattern,
                    mayDependOnPatterns = pattern.mayDependOn.toSet(),
                )
            },
        )
    }

    internal fun toAndroidConfiguration(): AndroidArchitectureConfiguration? =
        android.takeIf { it.enabled }?.toConfiguration()
}

open class SourcesSpec {
    val includes = StringSet("**/src/main/kotlin/**/*.kt")
    val excludes = StringSet("**/build/**", "**/.gradle/**", "**/generated/**")
    fun include(vararg patterns: String) { includes.addAll(patterns) }
    fun exclude(vararg patterns: String) { excludes.addAll(patterns) }
}

open class DomainSpec {
    var modelSuffix: String? = null
    val forbiddenImports = StringSet()
}

open class NamingSpec {
    var oneTypePerFile: Boolean = true
    var filenameMustMatchType: Boolean = true
    internal val forbiddenSuffixes = linkedMapOf<String, String?>()
    internal val forbiddenPackages = linkedMapOf<String, String?>()
    fun forbiddenSuffix(suffix: String, recommendation: String? = null) { forbiddenSuffixes[suffix] = recommendation }
    fun forbiddenPackage(packageFragment: String, recommendation: String? = null) { forbiddenPackages[packageFragment] = recommendation }
}

open class RepositoriesSpec {
    var requireSuspendFunctions: Boolean = false
    val packageFragments = StringSet("repository")
    val allowedNonSuspendReturnTypes = StringSet()
}

open class SerializationSpec {
    val allowed = StringSet()
    val forbidden = StringSet()
    fun allowed(vararg prefixes: String) { allowed.addAll(prefixes) }
    fun forbidden(vararg prefixes: String) { forbidden.addAll(prefixes) }
}

open class LayersSpec {
    internal val values = linkedMapOf<String, LayerSpec>()
    fun layer(name: String, action: LayerSpec.() -> Unit = {}) { values.getOrPut(name) { LayerSpec(name) }.action() }
}

open class LayerSpec internal constructor(val name: String) {
    val packageFragments = StringSet(name)
    val mayDependOn = StringSet()
    val forbiddenImports = StringSet()
    fun mayDependOn(vararg layerNames: String) { mayDependOn.addAll(layerNames) }
}

open class ModulesSpec {
    internal val values = linkedMapOf<String, ModuleSpec>()
    internal val patterns = linkedMapOf<String, ModulePatternSpec>()
    fun module(path: String, action: ModuleSpec.() -> Unit) { values.getOrPut(path) { ModuleSpec(path) }.action() }
    fun modulePattern(pathPattern: String, action: ModulePatternSpec.() -> Unit) {
        patterns.getOrPut(pathPattern) { ModulePatternSpec(pathPattern) }.action()
    }
}

open class ModuleSpec internal constructor(val path: String) {
    val allowedDependencies = StringSet()
    fun mayDependOn(vararg paths: String) { allowedDependencies.addAll(paths) }
}

open class ModulePatternSpec internal constructor(val pathPattern: String) {
    val mayDependOn = StringSet()
    fun mayDependOn(vararg pathPatterns: String) { mayDependOn.addAll(pathPatterns) }
}

open class AndroidSpec {
    internal var enabled: Boolean = false
    var domainPurity: DomainPurity = DomainPurity.STRICT
    val domainModulePatterns = StringSet(":core:domain")
    val domainAllowedImports = StringSet("javax.inject.", "kotlinx.coroutines.", "kotlinx.serialization.")
    var featureApiModulePattern: String = ":features:*:api"
    var featureImplementationModulePattern: String = ":features:*:impl"
    val composeAllowedModulePatterns = StringSet()
    val roomAllowedModulePatterns = StringSet()
    val androidComponentAllowedModulePatterns = StringSet()
    internal val externalDependencyBoundaries = mutableListOf<ExternalDependencyBoundary>()

    fun externalDependencyBoundary(
        technology: String,
        coordinatePrefixes: Set<String>,
        allowedModulePatterns: Set<String>,
    ) {
        externalDependencyBoundaries.removeAll { it.technology == technology }
        externalDependencyBoundaries += ExternalDependencyBoundary(
            technology = technology,
            coordinatePrefixes = coordinatePrefixes,
            allowedModulePatterns = allowedModulePatterns,
        )
    }

    internal fun apply(configuration: AndroidArchitectureConfiguration) {
        enabled = true
        domainPurity = configuration.domainPurity
        domainModulePatterns.replaceWith(configuration.domainModulePatterns)
        domainAllowedImports.replaceWith(configuration.domainAllowedImports)
        featureApiModulePattern = configuration.featureApiModulePattern
        featureImplementationModulePattern = configuration.featureImplementationModulePattern
        composeAllowedModulePatterns.replaceWith(configuration.composeAllowedModulePatterns)
        roomAllowedModulePatterns.replaceWith(configuration.roomAllowedModulePatterns)
        androidComponentAllowedModulePatterns.replaceWith(configuration.androidComponentAllowedModulePatterns)
        externalDependencyBoundaries.clear()
        externalDependencyBoundaries.addAll(configuration.externalDependencyBoundaries)
    }

    internal fun toConfiguration() = AndroidArchitectureConfiguration(
        domainPurity = domainPurity,
        domainModulePatterns = domainModulePatterns.toSet(),
        domainAllowedImports = domainAllowedImports.toSet(),
        featureApiModulePattern = featureApiModulePattern,
        featureImplementationModulePattern = featureImplementationModulePattern,
        composeAllowedModulePatterns = composeAllowedModulePatterns.toSet(),
        roomAllowedModulePatterns = roomAllowedModulePatterns.toSet(),
        androidComponentAllowedModulePatterns = androidComponentAllowedModulePatterns.toSet(),
        externalDependencyBoundaries = externalDependencyBoundaries.toList(),
    )
}

class StringSet(vararg initial: String) : LinkedHashSet<String>() {
    init { addAll(initial.asList()) }
    fun addAll(vararg values: String): Boolean = addAll(values.asList())
    internal fun replaceWith(values: Iterable<String>) { clear(); addAll(values) }
}
