package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
fun StreamingMarkdownContent(
    content: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    // Pendant le streaming rapide, utiliser du texte simple
    // Apres le streaming, utiliser le rendu Markdown complet

    var debouncedContent by remember { mutableStateOf(content) }
    var useSimpleRender by remember { mutableStateOf(isStreaming) }

    // Debounce le contenu pendant le streaming
    LaunchedEffect(content, isStreaming) {
        if (isStreaming) {
            // Pendant le streaming, mettre a jour toutes les 100ms max
            delay(100)
            debouncedContent = content
            useSimpleRender = true
        } else {
            // Streaming termine, passer au rendu Markdown
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
        // Rendu Markdown complet apres le streaming
        MarkdownContent(
            content = debouncedContent,
            modifier = modifier
        )
    }
}
