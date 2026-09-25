package fr.vippneus.intervention.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vippneus.intervention.R

/** Palette VIP Pneus : graphite (atelier, pneu) et jaune ambré (repères, actions). */
object Palette {
    val Graphite950 = Color(0xFF0E1114)
    val Graphite900 = Color(0xFF15181D)
    val Graphite850 = Color(0xFF1B1F24)
    val Graphite800 = Color(0xFF232830)
    val Graphite700 = Color(0xFF2F353E)
    val Graphite600 = Color(0xFF434A54)
    val Graphite500 = Color(0xFF5B636E)
    val Graphite400 = Color(0xFF7F8792)
    val Graphite300 = Color(0xFFABB2BB)
    val Graphite200 = Color(0xFFD3D8DE)
    val Graphite100 = Color(0xFFE7EAEE)
    val Graphite50 = Color(0xFFF3F4F6)

    val Amber = Color(0xFFFFB400)
    val AmberDeep = Color(0xFFE09A00)
    val Amber100 = Color(0xFFFFEFC7)
    val Amber50 = Color(0xFFFFF8E5)
    val AmberInk = Color(0xFF5C3F00)

    val Green = Color(0xFF178A4A)
    val Green100 = Color(0xFFDCF2E4)
    val Blue = Color(0xFF2360D8)
    val Blue100 = Color(0xFFDDE7FB)
    val Red = Color(0xFFD4372C)
    val Red100 = Color(0xFFFBE2DF)
    val Orange = Color(0xFFD9680A)
    val Orange100 = Color(0xFFFCE9D6)
}

/** Couleurs propres à l'application, en plus du schéma Material. */
@Immutable
data class VipColors(
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val chrome: Color,
    val chromeHigh: Color,
    val onChrome: Color,
    val onChromeMuted: Color,
    val canvas: Color,
    val card: Color,
    val cardBorder: Color,
    val muted: Color,
    val autoFill: Color,
    val success: Color,
    val successSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
)

private val LightVip = VipColors(
    accent = Palette.Amber,
    onAccent = Palette.Graphite900,
    accentSoft = Palette.Amber100,
    onAccentSoft = Palette.AmberInk,
    chrome = Palette.Graphite900,
    chromeHigh = Palette.Graphite800,
    onChrome = Color.White,
    onChromeMuted = Color.White.copy(alpha = 0.66f),
    canvas = Palette.Graphite700,
    card = Color.White,
    cardBorder = Palette.Graphite100,
    muted = Palette.Graphite500,
    autoFill = Palette.Amber50,
    success = Palette.Green,
    successSoft = Palette.Green100,
    info = Palette.Blue,
    infoSoft = Palette.Blue100,
    warning = Palette.Orange,
    warningSoft = Palette.Orange100,
    danger = Palette.Red,
    dangerSoft = Palette.Red100,
)

private val DarkVip = LightVip.copy(
    onAccentSoft = Palette.Amber100,
    accentSoft = Color(0xFF3D3010),
    chrome = Palette.Graphite950,
    chromeHigh = Palette.Graphite900,
    canvas = Palette.Graphite950,
    card = Palette.Graphite850,
    cardBorder = Palette.Graphite800,
    muted = Palette.Graphite300,
    autoFill = Color(0xFF2A2412),
    success = Color(0xFF53C285),
    successSoft = Color(0xFF15311F),
    info = Color(0xFF7FA6F5),
    infoSoft = Color(0xFF17243F),
    warning = Color(0xFFF2A15B),
    warningSoft = Color(0xFF3A2410),
    danger = Color(0xFFF08A82),
    dangerSoft = Color(0xFF3B1714),
)

val LocalVipColors = staticCompositionLocalOf { LightVip }

object Vip {
    val colors: VipColors
        @Composable get() = LocalVipColors.current
}

private val LightScheme = lightColorScheme(
    primary = Palette.Graphite800,
    onPrimary = Color.White,
    primaryContainer = Palette.Graphite100,
    onPrimaryContainer = Palette.Graphite900,
    secondary = Palette.Amber,
    onSecondary = Palette.Graphite900,
    secondaryContainer = Palette.Amber100,
    onSecondaryContainer = Palette.AmberInk,
    tertiary = Palette.Green,
    onTertiary = Color.White,
    tertiaryContainer = Palette.Green100,
    onTertiaryContainer = Color(0xFF0B3A1F),
    background = Palette.Graphite50,
    onBackground = Palette.Graphite900,
    surface = Palette.Graphite50,
    onSurface = Palette.Graphite900,
    surfaceVariant = Palette.Graphite100,
    onSurfaceVariant = Palette.Graphite500,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFEEF0F3),
    surfaceContainerHigh = Palette.Graphite100,
    surfaceContainerHighest = Palette.Graphite200,
    outline = Palette.Graphite300,
    outlineVariant = Palette.Graphite200,
    error = Palette.Red,
    onError = Color.White,
    errorContainer = Palette.Red100,
    onErrorContainer = Color(0xFF5C0F0A),
    inverseSurface = Palette.Graphite800,
    inverseOnSurface = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Palette.Graphite100,
    onPrimary = Palette.Graphite900,
    primaryContainer = Palette.Graphite700,
    onPrimaryContainer = Color.White,
    secondary = Palette.Amber,
    onSecondary = Palette.Graphite900,
    secondaryContainer = Color(0xFF3D3010),
    onSecondaryContainer = Palette.Amber100,
    tertiary = Color(0xFF53C285),
    background = Palette.Graphite900,
    onBackground = Palette.Graphite50,
    surface = Palette.Graphite900,
    onSurface = Palette.Graphite50,
    surfaceVariant = Palette.Graphite800,
    onSurfaceVariant = Palette.Graphite300,
    surfaceContainerLowest = Palette.Graphite950,
    surfaceContainerLow = Palette.Graphite850,
    surfaceContainer = Palette.Graphite800,
    surfaceContainerHigh = Palette.Graphite700,
    surfaceContainerHighest = Palette.Graphite600,
    outline = Palette.Graphite500,
    outlineVariant = Palette.Graphite700,
    error = Color(0xFFF08A82),
    inverseSurface = Palette.Graphite100,
    inverseOnSurface = Palette.Graphite900,
)

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0, family: FontFamily = Barlow) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val AppTypography = Typography(
    displayLarge = style(56, FontWeight.Bold, 60, family = BarlowCondensed),
    displayMedium = style(44, FontWeight.Bold, 48, family = BarlowCondensed),
    displaySmall = style(36, FontWeight.SemiBold, 40, family = BarlowCondensed),
    headlineLarge = style(32, FontWeight.SemiBold, 38),
    headlineMedium = style(28, FontWeight.SemiBold, 34),
    headlineSmall = style(24, FontWeight.SemiBold, 30),
    titleLarge = style(21, FontWeight.SemiBold, 27),
    titleMedium = style(18, FontWeight.SemiBold, 24),
    titleSmall = style(15, FontWeight.SemiBold, 20, 0.1),
    bodyLarge = style(17, FontWeight.Normal, 24),
    bodyMedium = style(15, FontWeight.Normal, 21),
    bodySmall = style(13, FontWeight.Normal, 18),
    labelLarge = style(16, FontWeight.SemiBold, 20, 0.2),
    labelMedium = style(13, FontWeight.Medium, 16, 0.2),
    labelSmall = style(12, FontWeight.Medium, 16, 0.3),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun VipTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalVipColors provides if (dark) DarkVip else LightVip) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
            // Couleur de texte par défaut hors des Surface (listes, formulaires)
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
