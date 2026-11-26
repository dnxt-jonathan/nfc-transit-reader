package com.jml.transitreader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandBlue = Color(0xFF0B5CAD)
private val BrandBlueDark = Color(0xFF4A90D9)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    secondary = Color(0xFF00695C)
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    secondary = Color(0xFF4DB6AC)
)

@Composable
fun TransitReaderTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
