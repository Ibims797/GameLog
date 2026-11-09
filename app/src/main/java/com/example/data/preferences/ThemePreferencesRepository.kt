package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppConfig.DATASTORE_PREFERENCES_NAME
)

/**
 * Die 3 wählbaren Designs laut Architektur-Vorgabe:
 * 1. "Cyberpunk Neon" (Gelb/Cyan/Magenta)
 * 2. "Dark Fantasy" (Dunkelgrau/Blutrot)
 * 3. "Sci-Fi Space" (Tiefblau/Weiß)
 */
enum class AppThemeStyle(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val palettePreviewText: String
) {
    CYBERPUNK_NEON(
        id = "cyberpunk_neon",
        displayName = "Cyberpunk Neon",
        subtitle = "High-Voltage Night City HUD (Gelb / Cyan / Magenta)",
        palettePreviewText = "#FCEE09 • #00F0FF • #FF0055"
    ),
    DARK_FANTASY(
        id = "dark_fantasy",
        displayName = "Dark Fantasy",
        subtitle = "Gotische Kathedrale & Runen-Glut (Dunkelgrau / Blutrot)",
        palettePreviewText = "#C81D25 • #D4AF37 • #18181C"
    ),
    SCI_FI_SPACE(
        id = "sci_fi_space",
        displayName = "Sci-Fi Space",
        subtitle = "Orbital-Kommandozentrale (Tiefblau / Sternenweiß / Eis-Cyan)",
        palettePreviewText = "#EAF6FF • #38BDF8 • #070F2B"
    );

    companion object {
        fun fromId(id: String?): AppThemeStyle {
            return entries.firstOrNull { it.id == id } ?: CYBERPUNK_NEON
        }
    }
}

/**
 * DataStore-Repository für das dynamische Theme-System sowie die verknüpfte Steam-ID.
 */
class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_THEME = stringPreferencesKey("selected_theme_style")
        val LINKED_STEAM_ID = stringPreferencesKey("linked_steam_id")
    }

    val selectedThemeFlow: Flow<AppThemeStyle> = context.dataStore.data.map { prefs ->
        AppThemeStyle.fromId(prefs[Keys.SELECTED_THEME])
    }

    val linkedSteamIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LINKED_STEAM_ID] ?: ""
    }

    suspend fun setThemeStyle(style: AppThemeStyle) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_THEME] = style.id
        }
    }

    suspend fun setLinkedSteamId(steamId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LINKED_STEAM_ID] = steamId.trim()
        }
    }
}
