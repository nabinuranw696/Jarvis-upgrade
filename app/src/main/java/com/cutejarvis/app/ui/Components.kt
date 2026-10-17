package com.cutejarvis.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cutejarvis.app.R
import kotlin.math.abs
import kotlin.math.sin

@Composable fun Mascot(size: Int = 120) = Image(
    painterResource(R.drawable.jarvis_mascot), contentDescription = "Jarvis",
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.26f).dp)).border(1.5.dp, Cj.neonBorder, RoundedCornerShape((size * 0.26f).dp)),
)

@Composable fun Avatar(size: Int = 36) = Image(
    painterResource(R.drawable.jarvis_mascot), contentDescription = "Jarvis", contentScale = ContentScale.Crop,
    modifier = Modifier.size(size.dp).clip(CircleShape).border(1.5.dp, Cj.neonBorder, CircleShape),
)

@Composable fun GradientButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null,
) {
    val grey = Color(0xFF35446B)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.then(if (enabled) Modifier.shadow(10.dp, shape, ambientColor = Cj.Cyan, spotColor = Cj.Blue) else Modifier)
            .clip(shape).background(if (enabled) Cj.brand else Brush.horizontalGradient(listOf(grey, grey)))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        Arrangement.Center, Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val color = Cj.Navy.copy(alpha = 0.82f)
    val border = BorderStroke(1.dp, Cj.neonBorder)
    val m = modifier.shadow(6.dp, shape, ambientColor = Cj.Blue.copy(alpha = 0.6f), spotColor = Cj.Cyan.copy(alpha = 0.4f))
    if (onClick != null) Surface(onClick = onClick, modifier = m, shape = shape, color = color, border = border) { Column(content = content) }
    else Surface(modifier = m, shape = shape, color = color, border = border) { Column(content = content) }
}

@Composable fun IconBadge(icon: ImageVector, tint: Color, size: Int = 42) {
    val shape = RoundedCornerShape(13.dp)
    Box(Modifier.size(size.dp).clip(shape).background(tint.copy(alpha = 0.14f)).border(1.dp, tint.copy(alpha = 0.7f), shape), Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.52f).dp))
    }
}

@Composable fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.8f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable fun Chip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Cj.Navy).border(1.dp, Cj.Cyan.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge) }
}

@Composable fun ScreenHeader(title: String, subtitle: String? = null) = Column(Modifier.padding(bottom = 12.dp)) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun NavRow(icon: ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit) =
    SoftCard(Modifier.fillMaxWidth(), onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Cj.Cyan)
        }
    }

@Composable fun GlowCircle(size: Int, color: Color = Cj.Blue) {
    Box(Modifier.size(size.dp).background(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent)), CircleShape))
}

/** Animated neon equalizer. [active] animates bars; [level] (0..1) scales them with mic loudness. */
@Composable fun Waveform(active: Boolean, level: Float, color: Color, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "wave").animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase",
    )
    Canvas(modifier.fillMaxWidth().height(80.dp)) {
        val n = 34; val gap = size.width / n; val w = gap * 0.45f
        for (i in 0 until n) {
            val env = 1f - abs(i - n / 2f) / (n / 2f) * 0.7f
            val amp = if (active) (0.25f + 0.75f * level) * (0.4f + 0.6f * abs(sin(t + i * 0.55f))) else 0.08f
            val h = (size.height * env * amp).coerceAtLeast(4f)
            drawRoundRect(color, Offset(i * gap + (gap - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}
