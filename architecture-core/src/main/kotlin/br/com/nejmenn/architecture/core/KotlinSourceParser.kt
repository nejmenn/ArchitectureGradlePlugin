package br.com.nejmenn.architecture.core

import java.nio.file.Path

/** A small lexer-based structural parser. Comments and literals are masked before declarations are inspected. */
class KotlinSourceParser {
    fun parse(
        file: Path,
        root: Path,
        content: String,
        modulePath: String? = null,
    ): AnalyzedSource {
        val masked = maskCommentsAndLiterals(content)
        val packageMatch = PACKAGE.find(masked)
        val imports = IMPORT.findAll(masked).map {
            SourceImport(
                qualifiedName = it.groupValues[1],
                line = lineAt(masked, it.range.first),
            )
        }.toList()
        val tokens = tokenize(masked)
        val types = mutableListOf<TopLevelType>()
        val functions = mutableListOf<SourceFunction>()
        var depth = 0

        tokens.forEachIndexed { index, token ->
            when (token.text) {
                "{" -> depth++
                "}" -> depth = (depth - 1).coerceAtLeast(0)
                "class", "interface", "object" -> if (depth == 0) {
                    val previous = tokens.getOrNull(index - 1)?.text
                    if (!(token.text == "class" && previous == "::")) {
                        tokens.drop(index + 1).firstOrNull { it.isIdentifier }?.let { name ->
                            val kind = when {
                                token.text == "class" && previous == "enum" -> "enum class"
                                token.text == "class" && previous == "data" -> "data class"
                                token.text == "class" && previous == "sealed" -> "sealed class"
                                token.text == "class" && previous == "value" -> "value class"
                                token.text == "interface" && previous == "fun" -> "fun interface"
                                token.text == "interface" && previous == "sealed" -> "sealed interface"
                                else -> token.text
                            }
                            types += TopLevelType(
                                name = name.text,
                                kind = kind,
                                line = token.line,
                            )
                        }
                    }
                }
                "fun" -> {
                    val recent = tokens.subList((index - 8).coerceAtLeast(0), index)
                        .takeLastWhile { it.text !in setOf("{", "}", ";", "fun") }
                    val openingParenthesisIndex = tokens.indexOfFirstAfter(index) { it.text == "(" }
                    val name = openingParenthesisIndex?.let { parenthesisIndex ->
                        tokens.subList(index + 1, parenthesisIndex).lastOrNull {
                            it.isIdentifier && it.text !in TYPE_PARAMETER_KEYWORDS
                        }
                    }
                    if (name != null) {
                        functions += SourceFunction(
                            name = name.text,
                            line = token.line,
                            isSuspend = recent.any { it.text == "suspend" },
                            braceDepth = depth,
                            returnType = openingParenthesisIndex?.let { functionReturnType(tokens, it) },
                        )
                    }
                }
            }
        }

        return AnalyzedSource(
            file = file,
            relativePath = runCatching { root.relativize(file) }.getOrDefault(file),
            content = content,
            packageName = packageMatch?.groupValues?.get(1),
            packageLine = packageMatch?.let { lineAt(masked, it.range.first) },
            imports = imports,
            topLevelTypes = types.distinctBy { Triple(it.name, it.kind, it.line) },
            functions = functions,
            modulePath = modulePath,
        )
    }

    private fun functionReturnType(tokens: List<Token>, openingParenthesisIndex: Int): String? {
        var parenthesisDepth = 0
        var index = openingParenthesisIndex
        while (index < tokens.size) {
            when (tokens[index].text) {
                "(" -> parenthesisDepth++
                ")" -> {
                    parenthesisDepth--
                    if (parenthesisDepth == 0) break
                }
            }
            index++
        }
        if (tokens.getOrNull(index + 1)?.text != ":") return null
        index += 2
        val type = buildString {
            while (index < tokens.size) {
                val token = tokens[index]
                if (token.isIdentifier || token.text == ".") append(token.text) else break
                index++
            }
        }
        return type.takeIf(String::isNotBlank)
    }

    private fun List<Token>.indexOfFirstAfter(index: Int, predicate: (Token) -> Boolean): Int? {
        for (candidate in index + 1 until size) {
            if (predicate(this[candidate])) return candidate
            if (this[candidate].text in setOf("{", "}", ";", "=")) return null
        }
        return null
    }

    private fun tokenize(text: String): List<Token> {
        val result = mutableListOf<Token>()
        var index = 0
        var line = 1
        while (index < text.length) {
            val c = text[index]
            when {
                c == '\n' -> { line++; index++ }
                c.isWhitespace() -> index++
                c == ':' && text.getOrNull(index + 1) == ':' -> {
                    result += Token(
                        text = "::",
                        line = line,
                        isIdentifier = false,
                    )
                    index += 2
                }
                c.isLetter() || c == '_' -> {
                    val start = index++
                    while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) index++
                    result += Token(
                        text = text.substring(start, index),
                        line = line,
                        isIdentifier = true,
                    )
                }
                else -> {
                    result += Token(
                        text = c.toString(),
                        line = line,
                        isIdentifier = false,
                    )
                    index++
                }
            }
        }
        return result
    }

    private fun maskCommentsAndLiterals(text: String): String {
        val chars = text.toCharArray()
        var i = 0
        fun blank(at: Int) { if (chars[at] != '\n' && chars[at] != '\r') chars[at] = ' ' }
        while (i < chars.size) {
            when {
                i + 2 < chars.size && text.startsWith("\"\"\"", i) -> {
                    repeat(3) { blank(i++) }
                    while (i + 2 < chars.size && !text.startsWith("\"\"\"", i)) blank(i++)
                    repeat(minOf(3, chars.size - i)) { blank(i++) }
                }
                chars[i] == '"' || chars[i] == '\'' -> {
                    val quote = chars[i]; blank(i++)
                    while (i < chars.size) {
                        if (chars[i] == '\\') { blank(i++); if (i < chars.size) blank(i++) }
                        else if (chars[i] == quote) { blank(i++); break }
                        else blank(i++)
                    }
                }
                i + 1 < chars.size && text.startsWith("//", i) -> {
                    while (i < chars.size && chars[i] != '\n') blank(i++)
                }
                i + 1 < chars.size && text.startsWith("/*", i) -> {
                    blank(i++); blank(i++); var nesting = 1
                    while (i < chars.size && nesting > 0) {
                        if (i + 1 < chars.size && text.startsWith("/*", i)) { nesting++; blank(i++); blank(i++) }
                        else if (i + 1 < chars.size && text.startsWith("*/", i)) { nesting--; blank(i++); blank(i++) }
                        else blank(i++)
                    }
                }
                else -> i++
            }
        }
        return String(chars)
    }

    private fun lineAt(text: String, offset: Int) = text.take(offset).count { it == '\n' } + 1

    private data class Token(val text: String, val line: Int, val isIdentifier: Boolean)

    private companion object {
        val PACKAGE = Regex("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)")
        val IMPORT = Regex("(?m)^\\s*import\\s+([A-Za-z_][\\w.*]*)(?:\\s+as\\s+\\w+)?")
        val TYPE_PARAMETER_KEYWORDS = setOf("reified", "in", "out")
    }
}
