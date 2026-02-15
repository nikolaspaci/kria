package com.nikolaspaci.app.llamallmlocal.ui.syntax

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

class CodeHighlighter(private val colors: SyntaxColors) {

    private val languageKeywords = mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "class", "object", "interface", "enum",
            "if", "else", "when", "for", "while", "do", "return", "break",
            "continue", "throw", "try", "catch", "finally", "import", "package",
            "private", "public", "protected", "internal", "override", "open",
            "abstract", "sealed", "data", "inline", "suspend", "companion",
            "lateinit", "by", "lazy", "null", "true", "false", "is", "as", "in"
        ),
        "java" to setOf(
            "public", "private", "protected", "class", "interface", "enum",
            "extends", "implements", "static", "final", "void", "int", "long",
            "double", "float", "boolean", "char", "byte", "short", "if", "else",
            "for", "while", "do", "switch", "case", "default", "break", "continue",
            "return", "try", "catch", "finally", "throw", "throws", "new", "this",
            "super", "null", "true", "false", "instanceof"
        ),
        "python" to setOf(
            "def", "class", "if", "elif", "else", "for", "while", "try",
            "except", "finally", "with", "as", "import", "from", "return",
            "yield", "lambda", "pass", "break", "continue", "raise", "True",
            "False", "None", "and", "or", "not", "in", "is", "global", "nonlocal",
            "async", "await", "self"
        ),
        "javascript" to setOf(
            "function", "const", "let", "var", "if", "else", "for", "while",
            "do", "switch", "case", "default", "break", "continue", "return",
            "try", "catch", "finally", "throw", "new", "this", "class", "extends",
            "import", "export", "default", "async", "await", "true", "false",
            "null", "undefined", "typeof", "instanceof"
        ),
        "cpp" to setOf(
            "int", "long", "double", "float", "char", "void", "bool", "auto",
            "class", "struct", "enum", "union", "namespace", "using", "template",
            "typename", "public", "private", "protected", "virtual", "override",
            "const", "static", "extern", "inline", "if", "else", "for", "while",
            "do", "switch", "case", "default", "break", "continue", "return",
            "try", "catch", "throw", "new", "delete", "nullptr", "true", "false"
        )
    )

    private val stringPattern = Regex("\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\.[^'\\\\]*)*'")
    private val numberPattern = Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b")
    private val commentSingleLine = Regex("//.*|#.*")
    private val commentMultiLine = Regex("/\\*[\\s\\S]*?\\*/")
    private val functionPattern = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
    private val typePattern = Regex("\\b[A-Z][a-zA-Z0-9_]*\\b")

    fun highlight(code: String, language: String? = null): AnnotatedString {
        val keywords = languageKeywords[language?.lowercase()] ?: emptySet()

        return buildAnnotatedString {
            append(code)

            // Colorier les commentaires multi-lignes
            commentMultiLine.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(color = colors.comment),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Colorier les commentaires single-line
            commentSingleLine.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(color = colors.comment),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Colorier les strings
            stringPattern.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(color = colors.string),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Colorier les nombres
            numberPattern.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(color = colors.number),
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Colorier les types (PascalCase)
            typePattern.findAll(code).forEach { match ->
                if (match.value !in keywords) {
                    addStyle(
                        SpanStyle(color = colors.type),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // Colorier les fonctions
            functionPattern.findAll(code).forEach { match ->
                val funcName = match.groupValues[1]
                val funcStart = match.range.first
                val funcEnd = funcStart + funcName.length
                addStyle(
                    SpanStyle(color = colors.function),
                    funcStart,
                    funcEnd
                )
            }

            // Colorier les mots-cles
            keywords.forEach { keyword ->
                Regex("\\b$keyword\\b").findAll(code).forEach { match ->
                    addStyle(
                        SpanStyle(color = colors.keyword),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }
        }
    }

    fun detectLanguage(code: String): String? {
        return when {
            code.contains("fun ") && code.contains("val ") -> "kotlin"
            code.contains("public class") || code.contains("System.out") -> "java"
            code.contains("def ") && code.contains(":") -> "python"
            code.contains("function") || code.contains("const ") -> "javascript"
            code.contains("#include") || code.contains("std::") -> "cpp"
            else -> null
        }
    }
}
