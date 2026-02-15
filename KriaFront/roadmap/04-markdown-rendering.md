# Phase 4: Rendu Riche (Markdown)

## Objectif
Implémenter le rendu Markdown et la coloration syntaxique pour les réponses du bot.

---

## 1. Dépendances

### libs.versions.toml

```toml
[versions]
compose-markdown = "0.5.4"
coil = "2.6.0"

[libraries]
compose-markdown = { group = "com.mikepenz", name = "multiplatform-markdown-renderer-m3", version.ref = "compose-markdown" }
compose-markdown-code = { group = "com.mikepenz", name = "multiplatform-markdown-renderer-code", version.ref = "compose-markdown" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
```

### build.gradle.kts

```kotlin
dependencies {
    implementation(libs.compose.markdown)
    implementation(libs.compose.markdown.code)
    implementation(libs.coil.compose)
}
```

---

## 2. Thème de Coloration Syntaxique

### Fichier à créer: `ui/syntax/SyntaxColors.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.syntax

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class SyntaxColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val type: Color,
    val operator: Color,
    val variable: Color,
    val background: Color,
    val text: Color
)

val DarkSyntaxColors = SyntaxColors(
    keyword = Color(0xFFCC7832),      // Orange
    string = Color(0xFF6A8759),       // Vert
    number = Color(0xFF6897BB),       // Bleu
    comment = Color(0xFF808080),      // Gris
    function = Color(0xFFFFC66D),     // Jaune
    type = Color(0xFF4EC9B0),         // Cyan
    operator = Color(0xFFA9B7C6),     // Gris clair
    variable = Color(0xFF9876AA),     // Violet
    background = Color(0xFF2B2B2B),   // Fond sombre
    text = Color(0xFFA9B7C6)          // Texte clair
)

val LightSyntaxColors = SyntaxColors(
    keyword = Color(0xFF0000FF),      // Bleu
    string = Color(0xFF008000),       // Vert
    number = Color(0xFF098658),       // Vert foncé
    comment = Color(0xFF808080),      // Gris
    function = Color(0xFF795E26),     // Marron
    type = Color(0xFF267F99),         // Cyan foncé
    operator = Color(0xFF000000),     // Noir
    variable = Color(0xFF001080),     // Bleu foncé
    background = Color(0xFFF5F5F5),   // Fond clair
    text = Color(0xFF000000)          // Texte noir
)

@Composable
fun currentSyntaxColors(): SyntaxColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) DarkSyntaxColors else LightSyntaxColors
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.299f * r + 0.587f * g + 0.114f * b
}
```

---

## 3. Coloration Syntaxique

### Fichier à créer: `ui/syntax/CodeHighlighter.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.syntax

import androidx.compose.ui.graphics.Color
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

            // Colorier les mots-clés
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
```

---

## 4. Composant CodeBlock

### Fichier à créer: `ui/chat/CodeBlock.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nikolaspaci.app.llamallmlocal.ui.syntax.CodeHighlighter
import com.nikolaspaci.app.llamallmlocal.ui.syntax.currentSyntaxColors

@Composable
fun CodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val syntaxColors = currentSyntaxColors()
    val highlighter = remember(syntaxColors) { CodeHighlighter(syntaxColors) }
    val highlightedCode = remember(code, language) {
        highlighter.highlight(code, language ?: highlighter.detectLanguage(code))
    }

    Surface(
        color = syntaxColors.background,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header avec langage et bouton copier
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(syntaxColors.background.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language?.uppercase() ?: "CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = syntaxColors.text.copy(alpha = 0.7f)
                )

                IconButton(
                    onClick = { copyToClipboard(context, code) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copier",
                        tint = syntaxColors.text.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(
                color = syntaxColors.text.copy(alpha = 0.1f),
                thickness = 1.dp
            )

            // Code avec scroll horizontal
            Box(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlightedCode,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = syntaxColors.text
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("code", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Code copié", Toast.LENGTH_SHORT).show()
}
```

---

## 5. Composant MarkdownContent

### Fichier à créer: `ui/chat/MarkdownContent.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.markdownColors
import com.mikepenz.markdown.model.markdownTypography

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    Markdown(
        content = content,
        colors = markdownColors(
            text = MaterialTheme.colorScheme.onSurface,
            codeText = MaterialTheme.colorScheme.onSurfaceVariant,
            codeBackground = MaterialTheme.colorScheme.surfaceVariant,
            linkText = MaterialTheme.colorScheme.primary,
            dividerColor = MaterialTheme.colorScheme.outline
        ),
        typography = markdownTypography(
            text = MaterialTheme.typography.bodyMedium,
            code = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            h1 = MaterialTheme.typography.headlineMedium,
            h2 = MaterialTheme.typography.headlineSmall,
            h3 = MaterialTheme.typography.titleLarge,
            h4 = MaterialTheme.typography.titleMedium,
            h5 = MaterialTheme.typography.titleSmall,
            h6 = MaterialTheme.typography.labelLarge,
            paragraph = MaterialTheme.typography.bodyMedium,
            quote = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            bullet = MaterialTheme.typography.bodyMedium,
            list = MaterialTheme.typography.bodyMedium,
            ordered = MaterialTheme.typography.bodyMedium
        ),
        modifier = modifier.fillMaxWidth()
    )
}
```

---

## 6. Markdown Optimisé pour Streaming

### Fichier à créer: `ui/chat/StreamingMarkdownContent.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun StreamingMarkdownContent(
    content: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    // Pendant le streaming rapide, utiliser du texte simple
    // Après le streaming, utiliser le rendu Markdown complet

    var debouncedContent by remember { mutableStateOf(content) }
    var useSimpleRender by remember { mutableStateOf(isStreaming) }

    // Debounce le contenu pendant le streaming
    LaunchedEffect(content, isStreaming) {
        if (isStreaming) {
            // Pendant le streaming, mettre à jour toutes les 100ms max
            delay(100)
            debouncedContent = content
            useSimpleRender = true
        } else {
            // Streaming terminé, passer au rendu Markdown
            debouncedContent = content
            useSimpleRender = false
        }
    }

    if (useSimpleRender) {
        // Rendu simple et rapide pendant le streaming
        Text(
            text = debouncedContent,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        // Rendu Markdown complet après le streaming
        MarkdownContent(
            content = debouncedContent,
            modifier = modifier
        )
    }
}
```

---

## 7. Modifier MessageRow

### Fichier à modifier: `ui/chat/MessageRow.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.viewmodel.Stats

@Composable
fun MessageRow(
    message: ChatMessage,
    stats: Stats? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (message.sender == Sender.USER)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Utiliser Markdown uniquement pour les messages du bot
            if (message.sender == Sender.BOT) {
                MarkdownContent(content = message.message)
            } else {
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Afficher les stats si disponibles
            if (stats != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "%.1f tokens/s • %ds • %d tokens".format(
                        stats.tokensPerSecond,
                        stats.durationInSeconds,
                        stats.totalTokens
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

---

## 8. Bouton Copier Réutilisable

### Fichier à créer: `ui/chat/CopyButton.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun CopyButton(
    textToCopy: String,
    modifier: Modifier = Modifier,
    onCopied: () -> Unit = {}
) {
    val context = LocalContext.current

    IconButton(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("text", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copié", Toast.LENGTH_SHORT).show()
            onCopied()
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copier",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

---

## 9. Fichiers à Créer/Modifier

### Fichiers à Créer
| Fichier | Description |
|---------|-------------|
| `ui/syntax/SyntaxColors.kt` | Thèmes de coloration |
| `ui/syntax/CodeHighlighter.kt` | Logique de coloration |
| `ui/chat/CodeBlock.kt` | Bloc de code avec copie |
| `ui/chat/MarkdownContent.kt` | Rendu Markdown |
| `ui/chat/StreamingMarkdownContent.kt` | Markdown optimisé |
| `ui/chat/CopyButton.kt` | Bouton copier réutilisable |

### Fichiers à Modifier
| Fichier | Modification |
|---------|--------------|
| `libs.versions.toml` | Ajouter compose-markdown, coil |
| `build.gradle.kts` | Ajouter dépendances |
| `ui/chat/MessageRow.kt` | Utiliser MarkdownContent |

---

## 10. Séquence d'Implémentation

1. Ajouter dépendances markdown et coil
2. Créer `ui/syntax/SyntaxColors.kt`
3. Créer `ui/syntax/CodeHighlighter.kt`
4. Créer `ui/chat/CopyButton.kt`
5. Créer `ui/chat/CodeBlock.kt`
6. Créer `ui/chat/MarkdownContent.kt`
7. Créer `ui/chat/StreamingMarkdownContent.kt`
8. Modifier `ui/chat/MessageRow.kt`
9. Tester le rendu Markdown
10. Tester la coloration syntaxique
