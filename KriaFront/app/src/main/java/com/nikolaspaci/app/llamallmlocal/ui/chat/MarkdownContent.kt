package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.nikolaspaci.app.llamallmlocal.ui.theme.ChatCodeBlockBg
import com.nikolaspaci.app.llamallmlocal.ui.theme.ChatWhite

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    Markdown(
        content = content,
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeText = ChatWhite,
            codeBackground = ChatCodeBlockBg,
            linkText = MaterialTheme.colorScheme.primary,
            dividerColor = MaterialTheme.colorScheme.outline
        ),
        typography = markdownTypography(
            text = MaterialTheme.typography.bodyMedium,
            code = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
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
