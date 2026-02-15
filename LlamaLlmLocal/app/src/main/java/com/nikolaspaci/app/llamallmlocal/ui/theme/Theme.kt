package com.nikolaspaci.app.llamallmlocal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ChatAccentYellow,
    onPrimary = ChatOnAccent,
    primaryContainer = ChatAccentYellow,
    onPrimaryContainer = ChatOnAccent,
    secondary = ChatGrey,
    onSecondary = ChatWhite,
    secondaryContainer = ChatDarkSurface,
    onSecondaryContainer = ChatWhite,
    background = ChatDarkBackground,
    onBackground = ChatWhite,
    surface = ChatDarkBackground,
    onSurface = ChatWhite,
    surfaceVariant = ChatDarkInputSurface,
    onSurfaceVariant = ChatGrey,
    outline = ChatDarkBorder,
    error = ChatError,
    onError = ChatWhite,
    inverseSurface = ChatDarkSidebar,
    inverseOnSurface = ChatWhite
)

private val LightColorScheme = lightColorScheme(
    primary = ChatAccentYellow,
    onPrimary = ChatOnAccent,
    primaryContainer = ChatAccentYellow,
    onPrimaryContainer = ChatOnAccent,
    secondary = ChatGrey,
    onSecondary = ChatLightBackground,
    secondaryContainer = ChatLightSurface,
    onSecondaryContainer = ChatLightText,
    background = ChatLightBackground,
    onBackground = ChatLightText,
    surface = ChatLightBackground,
    onSurface = ChatLightText,
    surfaceVariant = ChatLightSurface,
    onSurfaceVariant = ChatGrey,
    outline = ChatLightBorder,
    error = ChatError,
    onError = ChatLightBackground,
    inverseSurface = ChatDarkSidebar,
    inverseOnSurface = ChatWhite
)

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LlamaLLmLocalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
