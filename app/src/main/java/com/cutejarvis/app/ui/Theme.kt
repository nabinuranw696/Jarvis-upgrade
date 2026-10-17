package com.cutejarvis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Jarvis AI neon palette: electric blue, neon cyan, deep navy, purple. */
object Cj {
    val Blue = Color(0xFF2F7BFF)
    val Cyan = Color(0xFF22E3FF)
    val Purple = Color(0xFF9B5CFF)
    val Navy = Color(0xFF0B1636)
    val Deep = Color(0xFF050B1F)
    val Mint = Color(0xFF2EE6A6)
    val Amber = Color(0xFFFFB347)
    val Coral = Color(0xFFFF5C7A)
    val brand = Brush.horizontalGradient(listOf(Color(0xFF2F7BFF), Color(0xFF1FB8FF)))
    val neonBorder = Brush.linearGradient(listOf(Cyan.copy(alpha = 0.85f), Blue.copy(alpha = 0.55f), Purple.copy(alpha = 0.65f)))
}

private val Neon = darkColorScheme(
    primary = Cj.Cyan, onPrimary = Cj.Deep, primaryContainer = Color(0xFF12306E), onPrimaryContainer = Color(0xFFCFEFFF),
    secondary = Cj.Purple, secondaryContainer = Color(0xFF2B1B5A), tertiary = Cj.Blue,
    background = Cj.Deep, surface = Cj.Navy, surfaceVariant = Color(0xFF10204A),
    onSurface = Color(0xFFEAF2FF), onSurfaceVariant = Color(0xFF9FB4DD), outline = Color(0xFF2A4A9A), error = Cj.Coral,
)

@Composable fun CuteJarvisTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Neon, content = content)

/** Dark navy gradient with soft neon glows behind every screen. */
@Composable fun CjBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A1740), Cj.Deep, Color(0xFF040817))))) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Cj.Blue.copy(alpha = 0.18f), Color.Transparent), radius = 900f)))
        content()
    }
}
