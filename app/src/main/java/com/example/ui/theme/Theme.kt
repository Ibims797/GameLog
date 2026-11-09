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

@Composable
fun StoryGamesTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.CYBERPUNK_NEON,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeStyle) {
        AppThemeStyle.CYBERPUNK_NEON -> CyberpunkNeonColorScheme
        AppThemeStyle.DARK_FANTASY -> DarkFantasyColorScheme
        AppThemeStyle.SCI_FI_SPACE -> SciFiSpaceColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
