package br.com.nejmenn.architecture.gradle

import br.com.nejmenn.architecture.android.AndroidArchitectureConfiguration
import br.com.nejmenn.architecture.android.AndroidArchitectureRules
import br.com.nejmenn.architecture.core.*
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import java.nio.file.Files
import java.nio.file.Path

@CacheableTask
abstract class ArchitectureCheckTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input abstract val configurationFingerprint: org.gradle.api.provider.Property<String>
    @get:Input abstract val moduleDependencies: ListProperty<String>
    @get:Input abstract val externalDependencies: ListProperty<String>
    @get:Input abstract val moduleDirectories: ListProperty<String>
    @get:Internal
    abstract val scanRoot: DirectoryProperty
    @get:OutputFile abstract val markerFile: RegularFileProperty
    @get:Internal lateinit var architectureConfiguration: ArchitectureConfiguration
    @get:Internal var androidConfiguration: AndroidArchitectureConfiguration? = null

    @TaskAction open fun checkArchitecture() {
        val result = analyze()
        logger.lifecycle(TextReport.render(result))
        val marker = markerFile.get().asFile.toPath()
        Files.createDirectories(marker.parent)
        Files.writeString(marker, if (result.isSuccessful) "OK" else "FAILED\n${result.violations.size}")
        if (!result.isSuccessful) throw GradleException("Architecture check failed. ${result.errors.size} error violation(s) found.")
    }

    internal fun analyze(): ArchitectureResult {
        val root = scanRoot.get().asFile.toPath()
        val parser = KotlinSourceParser()
        val moduleLocations = moduleDirectories.get().map { encoded ->
            val (modulePath, relativeDirectory) = encoded.split("|", limit = 2)
            modulePath to root.resolve(relativeDirectory).normalize()
        }.sortedByDescending { (_, directory) -> directory.nameCount }
        val sources = sourceFiles.files.sortedBy { it.path }.map { file ->
            val sourcePath = file.toPath().normalize()
            val modulePath = moduleLocations.firstOrNull { (_, directory) -> sourcePath.startsWith(directory) }?.first
            parser.parse(
                file = sourcePath,
                root = root,
                content = file.readText(),
                modulePath = modulePath,
            )
        }
        val dependencies = moduleDependencies.get().map { encoded ->
            val (from, configurationName, to) = encoded.split("|", limit = 3)
            ModuleDependency(
                from = from,
                to = to,
                buildFile = Path.of(from.trim(':').replace(':', '/').ifBlank { "." }, "build.gradle.kts"),
                configurationName = configurationName,
            )
        }
        val declaredExternalDependencies = externalDependencies.get().map { encoded ->
            val (from, configurationName, group, name) = encoded.split("|", limit = 4)
            DeclaredExternalDependency(
                from = from,
                configurationName = configurationName,
                group = group,
                name = name,
                buildFile = Path.of(from.trim(':').replace(':', '/').ifBlank { "." }, "build.gradle.kts"),
            )
        }
        val rules = DefaultRules.all + androidConfiguration?.let(AndroidArchitectureRules::all).orEmpty()
        return ArchitectureEngine(rules).check(
            ArchitectureContext(
                rootDirectory = root,
                sources = sources,
                configuration = architectureConfiguration,
                moduleDependencies = dependencies,
                externalDependencies = declaredExternalDependencies,
            ),
        )
    }
}

@CacheableTask
abstract class ArchitectureReportTask : ArchitectureCheckTask() {
    @get:OutputDirectory abstract val reportDirectory: DirectoryProperty

    @TaskAction override fun checkArchitecture() {
        val result = analyze()
        val directory = reportDirectory.get().asFile.toPath()
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("architecture-report.txt"), TextReport.render(result))
        Files.writeString(directory.resolve("architecture-report.json"), JsonReport.render(result))
        Files.writeString(directory.resolve("architecture-report.html"), HtmlReport.render(result))
        val marker = markerFile.get().asFile.toPath()
        Files.createDirectories(marker.parent)
        Files.writeString(marker, "${result.violations.size} violation(s)")
        logger.lifecycle("Architecture reports written to ${directory.toAbsolutePath()}")
    }
}

internal object TextReport {
    fun render(result: ArchitectureResult) = buildString {
        appendLine("ArchitectureGradlePlugin Check")
        appendLine("===========================")
        if (result.violations.isEmpty()) appendLine("\nNo violations found.")
        result.violations.forEach { violation ->
            val rule = RuleId.entries.find { it.code == violation.ruleId }
            appendLine("\n${violation.ruleId} ${rule?.title.orEmpty()}")
            appendLine("\n${violation.file}${violation.line?.let { ":$it" }.orEmpty()}\n")
            appendLine(violation.message)
            violation.evidence?.let { appendLine("\nFound:\n$it") }
            violation.recommendation?.let { appendLine("\nRecommended:\n$it") }
        }
        appendLine("\n${result.violations.size} violation(s) found.")
    }
}

internal object JsonReport {
    fun render(result: ArchitectureResult) = buildString {
        append("{\n  \"schemaVersion\": 1,\n  \"successful\": ${result.isSuccessful},\n  \"summary\": {\"violations\": ${result.violations.size}, \"errors\": ${result.errors.size}},\n  \"violations\": [")
        result.violations.forEachIndexed { index, v ->
            if (index > 0) append(',')
            append("\n    {\"ruleId\":\"${escape(v.ruleId)}\",\"severity\":\"${v.severity}\",\"file\":\"${escape(v.file.toString())}\",\"line\":${v.line ?: "null"},\"message\":\"${escape(v.message)}\",\"evidence\":${nullable(v.evidence)},\"recommendation\":${nullable(v.recommendation)}}")
        }
        append("\n  ]\n}\n")
    }
    private fun nullable(value: String?) = value?.let { "\"${escape(it)}\"" } ?: "null"
    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}

internal object HtmlReport {
    fun render(result: ArchitectureResult) = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>ArchitectureGradlePlugin Report</title>
<style>body{font:15px system-ui;margin:2rem;max-width:1000px}article{border-left:4px solid #b42318;padding:0 1rem;margin:1.5rem 0}code{white-space:pre-wrap}.ok{color:#067647}</style></head>
<body><h1>ArchitectureGradlePlugin Report</h1><p class="${if (result.isSuccessful) "ok" else ""}">${result.violations.size} violation(s), ${result.errors.size} error(s).</p>
${result.violations.joinToString("\n") { v -> "<article><h2>${html(v.ruleId)}</h2><p><code>${html(v.file.toString())}${v.line?.let { ":$it" }.orEmpty()}</code></p><p>${html(v.message)}</p>${v.recommendation?.let { "<p><strong>Recommended:</strong> ${html(it)}</p>" }.orEmpty()}</article>" }}
</body></html>"""
    private fun html(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
