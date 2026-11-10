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
 * Die 7 wählbaren Story-Gaming-Designs (gespeichert via DataStore Preferences):
 * 1. "Cyberpunk Neon" (Gelb / Cyan / Magenta)
 * 2. "Dark Fantasy" (Dunkelgrau / Blutrot / Runen-Gold)
 * 3. "Sci-Fi Space" (Tiefblau / Sternenweiß / Eis-Cyan)
 * 4. "Retro Synthwave" (Sunset-Pink / Outrun-Türkis / Violett)
 * 5. "Nordic Valhalla" (Gletscher-Blau / Nordisches Bronze-Gold / Frost-Schiefer)
 * 6. "Wasteland Pip-Tech" (Phosphor-Grün / Bernstein-Orange / Bunker-Anthrazit)
 * 7. "Samurai Sakura" (Kirschblüten-Karmesin / Jade-Gold / Mitternachts-Indigo)
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
        palettePreviewText = "#D91E36 • #E5B84B • #18161E"
    ),
    SCI_FI_SPACE(
        id = "sci_fi_space",
        displayName = "Sci-Fi Space",
        subtitle = "Orbital-Kommandozentrale (Tiefblau / Sternenweiß / Eis-Cyan)",
        palettePreviewText = "#38BDF8 • #F0F7FF • #0B1536"
    ),
    RETRO_SYNTHWAVE(
        id = "retro_synthwave",
        displayName = "Retro Synthwave",
        subtitle = "80s Outrun & Neon-Horizont (Sunset-Pink / Türkis / Violett)",
        palettePreviewText = "#FF2A85 • #00F5D4 • #160B28"
    ),
    NORDIC_VALHALLA(
        id = "nordic_valhalla",
        displayName = "Nordic Valhalla",
        subtitle = "Midgard-Frost & Runen-Bronze (Gletscher-Blau / Gold / Schiefer)",
        palettePreviewText = "#67E8F9 • #F59E0B • #0F1722"
    ),
    WASTELAND_PIPTECH(
        id = "wasteland_piptech",
        displayName = "Wasteland Pip-Tech",
        subtitle = "Post-Apokalyptischer Terminal-Stil (Phosphor-Grün / Bernstein)",
        palettePreviewText = "#22C55E • #F97316 • #0D140E"
    ),
    SAMURAI_SAKURA(
        id = "samurai_sakura",
        displayName = "Samurai Sakura",
        subtitle = "Mitternachts-Indigo & Kirschblüte (Karmesin / Elfenbein-Gold)",
        palettePreviewText = "#FB7185 • #FDE047 • #131124"
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
