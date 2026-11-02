package com.hugobou.hugotrain.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D32), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9), onPrimaryContainer = Color(0xFF0B3D0E),
    secondary = Color(0xFFEF6C00), secondaryContainer = Color(0xFFFFE0B2),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF81C784), onPrimary = Color(0xFF0B3D0E),
    primaryContainer = Color(0xFF1B5E20), onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFFFB74D), secondaryContainer = Color(0xFF5D3200),
)

@Composable
fun HugoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Color por deporte, para identificar las actividades de un vistazo. */
fun sportColor(sport: String): Color = when (sport) {
    "Carrera" -> Color(0xFF1E88E5)
    "Trail" -> Color(0xFF6D4C41)
    "Natación" -> Color(0xFF00ACC1)
    "Fuerza" -> Color(0xFFE53935)
    "Caminata", "Senderismo" -> Color(0xFF43A047)
    "Bici" -> Color(0xFF8E24AA)
    "Descanso" -> Color(0xFF9E9E9E)
    else -> Color(0xFF757575)
}
