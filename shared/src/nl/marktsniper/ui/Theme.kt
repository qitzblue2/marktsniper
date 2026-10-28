package nl.marktsniper.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Bg = Color(0xFF0B0D12)
val Panel = Color(0xFF12151D)
val Panel2 = Color(0xFF181C26)
val Line = Color(0xFF232838)
val Mute = Color(0xFF8A92A6)
val Accent = Color(0xFFFF6A3D)
val Accent2 = Color(0xFFFFB03D)
val Green = Color(0xFF2FD58B)
val AccentGradient = Brush.linearGradient(listOf(Accent, Accent2))

fun platformColor(p: String) = when (p) {
    "marktplaats" -> Color(0xFFF0A500)
    "2dehands" -> Color(0xFF2A8CFF)
    else -> Color(0xFF09B1BA)
}

fun platformLabel(p: String) = when (p) {
    "marktplaats" -> "Marktplaats"
    "2dehands" -> "2dehands"
    else -> "Vinted"
}

@Composable
fun SniperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color(0xFF1A0D05), secondary = Accent2,
            background = Bg, surface = Panel, surfaceVariant = Panel2, surfaceContainer = Panel,
            surfaceContainerHigh = Panel2, surfaceContainerLow = Panel, onSurface = Color(0xFFE8EBF2),
            onSurfaceVariant = Mute, outline = Line, outlineVariant = Line,
        ),
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
