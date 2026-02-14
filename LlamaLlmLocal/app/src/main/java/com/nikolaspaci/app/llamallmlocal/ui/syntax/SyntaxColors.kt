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
    number = Color(0xFF098658),       // Vert fonce
    comment = Color(0xFF808080),      // Gris
    function = Color(0xFF795E26),     // Marron
    type = Color(0xFF267F99),         // Cyan fonce
    operator = Color(0xFF000000),     // Noir
    variable = Color(0xFF001080),     // Bleu fonce
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
