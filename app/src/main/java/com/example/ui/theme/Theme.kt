package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.data.preferences.AppThemeStyle
import com.example.data.preferences.CustomThemeColors

private val CyberpunkNeonColorScheme = darkColorScheme(
    primary = CyberYellow,
    onPrimary = Color(0xFF0A0B0E),
    primaryContainer = Color(0xFF3A3500),
    onPrimaryContainer = CyberYellow,
    secondary = CyberCyan,
    onSecondary = Color(0xFF001F24),
    secondaryContainer = Color(0xFF00363D),
    onSecondaryContainer = CyberCyan,
    tertiary = CyberMagenta,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4A001B),
    onTertiaryContainer = Color(0xFFFFB2C1),
    background = CyberBackground,
    onBackground = CyberOnSurface,
    surface = CyberSurface,
    onSurface = CyberOnSurface,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberOnSurfaceMuted,
    outline = CyberCyan.copy(alpha = 0.45f)
)

private val DarkFantasyColorScheme = darkColorScheme(
    primary = FantasyBloodRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF470811),
    onPrimaryContainer = Color(0xFFFFB3B8),
    secondary = FantasyRuneGold,
    onSecondary = Color(0xFF261A00),
    secondaryContainer = Color(0xFF3E2E08),
    onSecondaryContainer = FantasyRuneGold,
    tertiary = FantasyCrimsonAccent,
    onTertiary = Color.White,
    background = FantasyBackground,
    onBackground = FantasyOnSurface,
    surface = FantasySurface,
    onSurface = FantasyOnSurface,
    surfaceVariant = FantasySurfaceVariant,
    onSurfaceVariant = FantasyOnSurfaceMuted,
    outline = FantasyBloodRed.copy(alpha = 0.45f)
)

private val SciFiSpaceColorScheme = darkColorScheme(
    primary = SpaceIceCyan,
    onPrimary = Color(0xFF02182B),
    primaryContainer = Color(0xFF082F49),
    onPrimaryContainer = SpaceStarWhite,
    secondary = SpaceStarWhite,
    onSecondary = Color(0xFF091326),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = SpaceStarWhite,
    tertiary = SpaceNebulaViolet,
    onTertiary = Color(0xFF0F172A),
    background = SpaceBackground,
    onBackground = SpaceOnSurface,
    surface = SpaceSurface,
    onSurface = SpaceOnSurface,
    surfaceVariant = SpaceSurfaceVariant,
    onSurfaceVariant = SpaceOnSurfaceMuted,
    outline = SpaceIceCyan.copy(alpha = 0.45f)
)

private val RetroSynthwaveColorScheme = darkColorScheme(
    primary = SynthPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C0827),
    onPrimaryContainer = Color(0xFFFFB8D2),
    secondary = SynthTurquoise,
    onSecondary = Color(0xFF00241F),
    secondaryContainer = Color(0xFF003D35),
    onSecondaryContainer = SynthTurquoise,
    tertiary = SynthSunsetOrange,
    onTertiary = Color(0xFF241400),
    background = SynthBackground,
    onBackground = SynthOnSurface,
    surface = SynthSurface,
    onSurface = SynthOnSurface,
    surfaceVariant = SynthSurfaceVariant,
    onSurfaceVariant = SynthOnSurfaceMuted,
    outline = SynthPink.copy(alpha = 0.45f)
)

private val NordicValhallaColorScheme = darkColorScheme(
    primary = NordicGlacierCyan,
    onPrimary = Color(0xFF042228),
    primaryContainer = Color(0xFF0D3B46),
    onPrimaryContainer = NordicGlacierCyan,
    secondary = NordicBronzeGold,
    onSecondary = Color(0xFF281700),
    secondaryContainer = Color(0xFF422800),
    onSecondaryContainer = NordicBronzeGold,
    tertiary = NordicFrostSilver,
    onTertiary = Color(0xFF0F172A),
    background = NordicBackground,
    onBackground = NordicOnSurface,
    surface = NordicSurface,
    onSurface = NordicOnSurface,
    surfaceVariant = NordicSurfaceVariant,
    onSurfaceVariant = NordicOnSurfaceMuted,
    outline = NordicGlacierCyan.copy(alpha = 0.42f)
)

private val WastelandPipTechColorScheme = darkColorScheme(
    primary = WastelandPipGreen,
    onPrimary = Color(0xFF04240F),
    primaryContainer = Color(0xFF0B3B1C),
    onPrimaryContainer = WastelandPipGreen,
    secondary = WastelandAmber,
    onSecondary = Color(0xFF291200),
    secondaryContainer = Color(0xFF431E02),
    onSecondaryContainer = WastelandAmber,
    tertiary = WastelandRust,
    onTertiary = Color.White,
    background = WastelandBackground,
    onBackground = WastelandOnSurface,
    surface = WastelandSurface,
    onSurface = WastelandOnSurface,
    surfaceVariant = WastelandSurfaceVariant,
    onSurfaceVariant = WastelandOnSurfaceMuted,
    outline = WastelandPipGreen.copy(alpha = 0.45f)
)

private val SamuraiSakuraColorScheme = darkColorScheme(
    primary = SamuraiSakuraRose,
    onPrimary = Color(0xFF29040B),
    primaryContainer = Color(0xFF4C0917),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = SamuraiImperialGold,
    onSecondary = Color(0xFF261C00),
    secondaryContainer = Color(0xFF3F3000),
    onSecondaryContainer = SamuraiImperialGold,
    tertiary = SamuraiJade,
    onTertiary = Color(0xFF00241E),
    background = SamuraiBackground,
    onBackground = SamuraiOnSurface,
    surface = SamuraiSurface,
    onSurface = SamuraiOnSurface,
    surfaceVariant = SamuraiSurfaceVariant,
    onSurfaceVariant = SamuraiOnSurfaceMuted,
    outline = SamuraiSakuraRose.copy(alpha = 0.45f)
)

fun parseHexColorSafe(hex: String, fallback: Color): Color {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return fallback
    return try {
        val value = cleaned.toLong(16)
        if (cleaned.length == 6) {
            Color(0xFF000000L or value)
        } else {
            Color(value)
        }
    } catch (_: Exception) {
        fallback
    }
}

fun colorToHexString(color: Color): String {
    val r = (color.red * 255f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

private fun buildCustomColorScheme(custom: CustomThemeColors): ColorScheme {
    val primary = parseHexColorSafe(custom.primaryHex, CyberCyan)
    val secondary = parseHexColorSafe(custom.secondaryHex, CyberYellow)
    val background = parseHexColorSafe(custom.backgroundHex, CyberBackground)
    val surface = parseHexColorSafe(custom.surfaceHex, CyberSurface)

    val onPrimary = if (primary.luminance() > 0.45f) Color(0xFF0B0D13) else Color.White
    val onSecondary = if (secondary.luminance() > 0.45f) Color(0xFF0B0D13) else Color.White
    val onBg = if (background.luminance() > 0.55f) Color(0xFF10141D) else Color(0xFFF3F6FC)
    val onSurf = if (surface.luminance() > 0.55f) Color(0xFF10141D) else Color(0xFFF3F6FC)
    val onSurfMuted = if (surface.luminance() > 0.55f) Color(0xFF475569) else Color(0xFFA3B1C6)

    val surfaceVariant = Color(
        red = (surface.red * 0.82f + primary.red * 0.18f).coerceIn(0f, 1f),
        green = (surface.green * 0.82f + primary.green * 0.18f).coerceIn(0f, 1f),
        blue = (surface.blue * 0.82f + primary.blue * 0.18f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val primaryContainer = Color(
        red = (surface.red * 0.65f + primary.red * 0.35f).coerceIn(0f, 1f),
        green = (surface.green * 0.65f + primary.green * 0.35f).coerceIn(0f, 1f),
        blue = (surface.blue * 0.65f + primary.blue * 0.35f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val secondaryContainer = Color(
        red = (surface.red * 0.65f + secondary.red * 0.35f).coerceIn(0f, 1f),
        green = (surface.green * 0.65f + secondary.green * 0.35f).coerceIn(0f, 1f),
        blue = (surface.blue * 0.65f + secondary.blue * 0.35f).coerceIn(0f, 1f),
        alpha = 1f
    )

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = secondary,
        tertiary = secondary,
        onTertiary = onSecondary,
        background = background,
        onBackground = onBg,
        surface = surface,
        onSurface = onSurf,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfMuted,
        outline = primary.copy(alpha = 0.45f)
    )
}

@Composable
fun StoryGamesTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.CYBERPUNK_NEON,
    customColors: CustomThemeColors = CustomThemeColors(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeStyle) {
        AppThemeStyle.CYBERPUNK_NEON -> CyberpunkNeonColorScheme
        AppThemeStyle.DARK_FANTASY -> DarkFantasyColorScheme
        AppThemeStyle.SCI_FI_SPACE -> SciFiSpaceColorScheme
        AppThemeStyle.RETRO_SYNTHWAVE -> RetroSynthwaveColorScheme
        AppThemeStyle.NORDIC_VALHALLA -> NordicValhallaColorScheme
        AppThemeStyle.WASTELAND_PIPTECH -> WastelandPipTechColorScheme
        AppThemeStyle.SAMURAI_SAKURA -> SamuraiSakuraColorScheme
        AppThemeStyle.CUSTOM_DESIGN -> buildCustomColorScheme(customColors)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
