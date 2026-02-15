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
    keyword = Color(0xFFCC7832),
    string = Color(0xFF6A8759),
    number = Color(0xFF6897BB),
    comment = Color(0xFF808080),
    function = Color(0xFFFFC66D),
    type = Color(0xFF4EC9B0),
    operator = Color(0xFFA9B7C6),
    variable = Color(0xFF9876AA),
    background = Color(0xFF0D0D0D),
    text = Color(0xFFA9B7C6)
)

val LightSyntaxColors = SyntaxColors(
    keyword = Color(0xFF0000FF),
    string = Color(0xFF008000),
    number = Color(0xFF098658),
    comment = Color(0xFF808080),
    function = Color(0xFF795E26),
    type = Color(0xFF267F99),
    operator = Color(0xFF000000),
    variable = Color(0xFF001080),
    background = Color(0xFF1E1E1E),
    text = Color(0xFFA9B7C6)
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
