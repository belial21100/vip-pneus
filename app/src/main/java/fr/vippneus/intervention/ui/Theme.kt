package fr.vippneus.intervention.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF1D2B44)
private val Amber = Color(0xFFFFB300)

private val LightColors = lightColorScheme(
    primary = Color(0xFF24406B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3F8),
    onPrimaryContainer = Navy,
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3A8),
    onSecondaryContainer = Color(0xFF2B1B00),
    tertiary = Color(0xFF2E6B3A),
    tertiaryContainer = Color(0xFFCFEBD3),
    onTertiaryContainer = Color(0xFF0B2912),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFF7F8FA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F7),
    surfaceContainer = Color(0xFFEBEEF3),
    surfaceContainerHigh = Color(0xFFE5E8EE),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF0B2448),
    primaryContainer = Color(0xFF24406B),
    onPrimaryContainer = Color(0xFFD7E3F8),
    secondary = Amber,
    onSecondary = Color(0xFF2B1B00),
    secondaryContainer = Color(0xFF5E4100),
    tertiary = Color(0xFF9ED5A6),
    tertiaryContainer = Color(0xFF14512A),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp),
        labelLarge = t.labelLarge.copy(fontSize = 15.sp),
    )
}

val SectionTitle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun VipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
