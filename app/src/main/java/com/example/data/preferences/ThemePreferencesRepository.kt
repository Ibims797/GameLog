package com.example.data.preferences

import android.content.Context
import android.content.res.Resources
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppConfig.DATASTORE_PREFERENCES_NAME
)

/**
 * Unterstützte App-Sprachen.
 * Beim allerersten Start liest die App automatisch die Systemsprache des Handys (`getDeviceSystemLanguageCode()`)
 * ein und setzt die passende Sprache. In den Einstellungen kann die Sprache jederzeit geändert werden.
 */
enum class AppLanguage(
    val id: String,
    val nativeLabel: String,
    val subtitle: String,
    val flagEmoji: String
) {
    SYSTEM(
        id = "system",
        nativeLabel = "Systemsprache (Automatisch)",
        subtitle = "Übernimmt automatisch die Sprache deines Handys",
        flagEmoji = "📱"
    ),
    GERMAN(
        id = "de",
        nativeLabel = "Deutsch",
        subtitle = "Deutsche Benutzeroberfläche",
        flagEmoji = "🇩🇪"
    ),
    ENGLISH(
        id = "en",
        nativeLabel = "English",
        subtitle = "English user interface",
        flagEmoji = "🇬🇧"
    ),
    SPANISH(
        id = "es",
        nativeLabel = "Español",
        subtitle = "Interfaz en español",
        flagEmoji = "🇪🇸"
    ),
    FRENCH(
        id = "fr",
        nativeLabel = "Français",
        subtitle = "Interface en français",
        flagEmoji = "🇫🇷"
    );

    companion object {
        fun getDeviceSystemLanguageCode(): String {
            return try {
                val locales = Resources.getSystem().configuration.locales
                if (!locales.isEmpty) {
                    locales.get(0).language.lowercase(Locale.ROOT)
                } else {
                    Locale.getDefault().language.lowercase(Locale.ROOT)
                }
            } catch (_: Throwable) {
                Locale.getDefault().language.lowercase(Locale.ROOT)
            }
        }

        fun fromSystemLocale(): AppLanguage {
            val sys = getDeviceSystemLanguageCode()
            return when {
                sys.startsWith("de") -> GERMAN
                sys.startsWith("en") -> ENGLISH
                sys.startsWith("es") -> SPANISH
                sys.startsWith("fr") -> FRENCH
                else -> GERMAN
            }
        }

        fun fromId(id: String?): AppLanguage {
            if (id.isNullOrBlank()) {
                return fromSystemLocale()
            }
            return entries.firstOrNull { it.id == id } ?: fromSystemLocale()
        }
    }
}

/**
 * Benutzerdefinierte Farben für das frei anpassbare "Eigenes Design (Custom)".
 */
data class CustomThemeColors(
    val primaryHex: String = "#00F0FF",
    val secondaryHex: String = "#FCEE09",
    val backgroundHex: String = "#0A0C12",
    val surfaceHex: String = "#141824"
)

/**
 * Die 7 vorgefertigten Story-Gaming-Designs + 1 frei konfigurierbares "Eigenes Design (Custom)"
 * (gespeichert via DataStore Preferences).
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
    ),
    CUSTOM_DESIGN(
        id = "custom_design",
        displayName = "Eigenes Design (Custom)",
        subtitle = "Dein individuell zusammengestelltes Farbschema",
        palettePreviewText = "Frei anpassbar"
    );

    companion object {
        fun fromId(id: String?): AppThemeStyle {
            return entries.firstOrNull { it.id == id } ?: CYBERPUNK_NEON
        }
    }
}

/**
 * DataStore-Repository für:
 * 1. Dynamisches Theme-System (7 Presets + frei anpassbares Custom-Design)
 * 2. Automatische Erkennung der Handy-Systemsprache beim ersten Start + manuelle Sprachwahl
 * 3. Verknüpfte Steam-ID & lokal gecachte Admin-Keys
 */
class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_THEME = stringPreferencesKey("selected_theme_style")
        val CUSTOM_PRIMARY_HEX = stringPreferencesKey("custom_theme_primary_hex")
        val CUSTOM_SECONDARY_HEX = stringPreferencesKey("custom_theme_secondary_hex")
        val CUSTOM_BACKGROUND_HEX = stringPreferencesKey("custom_theme_background_hex")
        val CUSTOM_SURFACE_HEX = stringPreferencesKey("custom_theme_surface_hex")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_app_language")
        val LINKED_STEAM_ID = stringPreferencesKey("linked_steam_id")
        val CACHED_STEAM_API_KEY = stringPreferencesKey("cached_steam_api_key")
        val CACHED_TWITCH_CLIENT_ID = stringPreferencesKey("cached_twitch_client_id")
        val CACHED_TWITCH_CLIENT_SECRET = stringPreferencesKey("cached_twitch_client_secret")
    }

    val selectedThemeFlow: Flow<AppThemeStyle> = context.dataStore.data.map { prefs ->
        AppThemeStyle.fromId(prefs[Keys.SELECTED_THEME])
    }

    val customThemeColorsFlow: Flow<CustomThemeColors> = context.dataStore.data.map { prefs ->
        CustomThemeColors(
            primaryHex = prefs[Keys.CUSTOM_PRIMARY_HEX] ?: "#00F0FF",
            secondaryHex = prefs[Keys.CUSTOM_SECONDARY_HEX] ?: "#FCEE09",
            backgroundHex = prefs[Keys.CUSTOM_BACKGROUND_HEX] ?: "#0A0C12",
            surfaceHex = prefs[Keys.CUSTOM_SURFACE_HEX] ?: "#141824"
        )
    }

    val selectedLanguageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromId(prefs[Keys.SELECTED_LANGUAGE])
    }

    val linkedSteamIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LINKED_STEAM_ID] ?: ""
    }

    /**
     * Liest beim allerersten Start automatisch die Systemsprache des Handys aus
     * und speichert sie als Startsprache, sofern der Nutzer noch keine eigene Wahl getroffen hat.
     */
    suspend fun initializeSystemLanguageIfUnset() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.SELECTED_LANGUAGE].isNullOrBlank()) {
                val detected = AppLanguage.fromSystemLocale()
                prefs[Keys.SELECTED_LANGUAGE] = detected.id
            }
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_LANGUAGE] = language.id
        }
    }

    suspend fun setThemeStyle(style: AppThemeStyle) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_THEME] = style.id
        }
    }

    suspend fun saveCustomThemeColors(
        colors: CustomThemeColors,
        activateCustomTheme: Boolean = true
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_PRIMARY_HEX] = colors.primaryHex.trim()
            prefs[Keys.CUSTOM_SECONDARY_HEX] = colors.secondaryHex.trim()
            prefs[Keys.CUSTOM_BACKGROUND_HEX] = colors.backgroundHex.trim()
            prefs[Keys.CUSTOM_SURFACE_HEX] = colors.surfaceHex.trim()
            if (activateCustomTheme) {
                prefs[Keys.SELECTED_THEME] = AppThemeStyle.CUSTOM_DESIGN.id
            }
        }
    }

    suspend fun setLinkedSteamId(steamId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LINKED_STEAM_ID] = steamId.trim()
        }
    }

    suspend fun cacheRuntimeApiKeysLocally(
        twitchClientId: String,
        twitchClientSecret: String,
        steamApiKey: String
    ) {
        context.dataStore.edit { prefs ->
            if (twitchClientId.isNotBlank()) prefs[Keys.CACHED_TWITCH_CLIENT_ID] = twitchClientId.trim()
            if (twitchClientSecret.isNotBlank()) prefs[Keys.CACHED_TWITCH_CLIENT_SECRET] = twitchClientSecret.trim()
            if (steamApiKey.isNotBlank()) prefs[Keys.CACHED_STEAM_API_KEY] = steamApiKey.trim()
        }
    }

    suspend fun restoreCachedApiKeysIntoAppConfig() {
        val prefs = context.dataStore.data.first()
        val cachedTwitchId = prefs[Keys.CACHED_TWITCH_CLIENT_ID].orEmpty().trim()
        val cachedTwitchSecret = prefs[Keys.CACHED_TWITCH_CLIENT_SECRET].orEmpty().trim()
        val cachedSteamKey = prefs[Keys.CACHED_STEAM_API_KEY].orEmpty().trim()
        if (cachedTwitchId.isNotBlank() || cachedTwitchSecret.isNotBlank() || cachedSteamKey.isNotBlank()) {
            AppConfig.updateRuntimeAdminKeys(
                twitchClientId = cachedTwitchId.ifBlank { AppConfig.TWITCH_CLIENT_ID },
                twitchClientSecret = cachedTwitchSecret.ifBlank { AppConfig.TWITCH_CLIENT_SECRET },
                steamApiKey = cachedSteamKey.ifBlank { AppConfig.STEAM_API_KEY }
            )
        }
    }
}
