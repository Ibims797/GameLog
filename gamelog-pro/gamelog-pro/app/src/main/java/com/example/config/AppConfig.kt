package com.example.config

/**
 * Zentrale API- & Cloud-Konfiguration (AppConfig).
 *
 * SICHERHEITS-ARCHITEKTUR (ZERO-APK-SECRETS):
 * Es werden KEINE API-Keys im APK oder in `BuildConfig` gespeichert!
 * Ein dekompiliertes APK enthält keinerlei Geheimnisse.
 *
 * Die API-Keys (`twitchClientId`, `twitchClientSecret`, `steamApiKey`) liegen
 * ausschließlich in der Cloud im geschützten Firestore-Tresor (`/admin_vault/api_secrets`).
 * Die Firestore Security Rules erlauben den Lese- und Schreibzugriff auf diesen Tresor
 * mathematisch strikt NUR für den verifizierten Admin (`nico.schaefer.hn@gmail.com`).
 */
object AppConfig {
    // Öffentliche, unkritische Base-URLs
    const val TWITCH_AUTH_BASE_URL = "https://id.twitch.tv/"
    const val IGDB_BASE_URL = "https://api.igdb.com/v4/"
    const val STEAM_API_BASE_URL = "https://api.steampowered.com/"
    const val STEAM_STORE_BASE_URL = "https://store.steampowered.com/api/"
    const val STEAM_OPENID_PROVIDER_URL = "https://steamcommunity.com/openid/login"

    // Bootstrapped Admin E-Mail (hat exklusiven Zugriff auf den Cloud-API-Tresor)
    const val ADMIN_EMAIL = "nico.schaefer.hn@gmail.com"

    // Firestore Kollektions-Konstanten
    const val COLLECTION_GLOBAL_STATS = "global_game_stats"
    const val COLLECTION_USERS = "users"
    const val SUBCOLLECTION_CONTRIBUTIONS = "game_contributions"
    const val COLLECTION_ADMIN_VAULT = "admin_vault"
    const val DOCUMENT_API_SECRETS = "api_secrets"

    // Room Datenbank-Name & DataStore Name
    const val LOCAL_DATABASE_NAME = "story_games_tracker.db"
    const val DATASTORE_PREFERENCES_NAME = "story_games_user_prefs"

    // Laufzeit-Speicher im RAM (wird erst nach erfolgreichem Admin-Login aus Firestore geladen
    // und beim Sign-Out sofort wieder geleert). NIEMALS im APK hinterlegt!
    @Volatile
    var TWITCH_CLIENT_ID: String = ""
        private set

    @Volatile
    var TWITCH_CLIENT_SECRET: String = ""
        private set

    @Volatile
    var STEAM_API_KEY: String = ""
        private set

    fun updateRuntimeAdminKeys(
        twitchClientId: String,
        twitchClientSecret: String,
        steamApiKey: String
    ) {
        TWITCH_CLIENT_ID = twitchClientId.trim()
        TWITCH_CLIENT_SECRET = twitchClientSecret.trim()
        STEAM_API_KEY = steamApiKey.trim()
    }

    fun clearRuntimeAdminKeys() {
        TWITCH_CLIENT_ID = ""
        TWITCH_CLIENT_SECRET = ""
        STEAM_API_KEY = ""
    }

    /**
     * Wandelt eine IGDB `image_id` in die vollständige Cover-URL (`t_cover_big`) für Coil um.
     */
    fun getIgdbCoverImageUrl(imageId: String): String {
        return "https://images.igdb.com/igdb/image/upload/t_cover_big/$imageId.jpg"
    }

    // Hilfsfunktion für hochauflösende Steam Header-Cover anhand der Steam AppID
    fun getSteamHeaderImageUrl(appId: Long): String {
        return "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"
    }
}
