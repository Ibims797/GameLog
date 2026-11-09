package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.preferences.AppThemeStyle

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

@Composable
fun StoryGamesTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.CYBERPUNK_NEON,
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
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
