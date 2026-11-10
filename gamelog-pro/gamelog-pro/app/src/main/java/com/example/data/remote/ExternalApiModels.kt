package com.example.data.remote

import com.example.config.AppConfig
import com.squareup.moshi.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * Datenmodelle für Twitch OAuth2, IGDB API v4 und Steam Web/Store API (inkl. Errungenschaften).
 */

/**
 * Antwort des Twitch OAuth2 Token-Endpunkts (`POST oauth2/token`).
 */
data class TwitchTokenResponse(
    @param:Json(name = "access_token") val accessToken: String = "",
    @param:Json(name = "expires_in") val expiresIn: Long = 0L,
    @param:Json(name = "token_type") val tokenType: String = ""
)

/**
 * IGDB Spiel-DTO (`POST https://api.igdb.com/v4/games`).
 * Enthält `id`, `name` und das verschachtelte `cover`-Objekt mit `image_id`.
 */
data class IgdbGameDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "name") val name: String = "",
    @param:Json(name = "cover") val cover: IgdbCoverDto? = null
) {
    /**
     * Mapper-Funktion, die die verschachtelte `cover.image_id` in die fertige Coil-URL umwandelt:
     * `https://images.igdb.com/igdb/image/upload/t_cover_big/{image_id}.jpg`
     */
    fun toCoverBigUrl(): String {
        val imageId = cover?.imageId?.trim().orEmpty()
        return if (imageId.isNotEmpty()) {
            AppConfig.getIgdbCoverImageUrl(imageId)
        } else {
            ""
        }
    }
}

/**
 * Verschachteltes `cover`-Objekt aus der IGDB-Antwort (`fields name, cover.image_id;`).
 */
data class IgdbCoverDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "image_id") val imageId: String? = null
)

/**
 * Steam Store Search API Antwort (`store.steampowered.com/api/storesearch/`).
 */
data class SteamStoreSearchResponse(
    @param:Json(name = "total") val total: Int = 0,
    @param:Json(name = "items") val items: List<SteamStoreSearchItemDto>? = null
)

data class SteamStoreSearchItemDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "name") val name: String = "",
    @param:Json(name = "tiny_image") val tinyImage: String? = null
)

/**
 * Steam Web API: `IPlayerService/GetOwnedGames/v0001/`
 */
data class SteamOwnedGamesEnvelope(
    @param:Json(name = "response") val response: SteamOwnedGamesResponse? = null
)

data class SteamOwnedGamesResponse(
    @param:Json(name = "game_count") val gameCount: Int? = null,
    @param:Json(name = "games") val games: List<SteamOwnedGameDto>? = null
)

data class SteamOwnedGameDto(
    @param:Json(name = "appid") val appId: Long = 0L,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "playtime_forever") val playtimeForeverMinutes: Int? = null,
    @param:Json(name = "img_icon_url") val imgIconUrl: String? = null
)

/**
 * Steam Web API: `IWishlistService/GetWishlist/v1/`
 */
data class SteamWishlistEnvelope(
    @param:Json(name = "response") val response: SteamWishlistResponse? = null
)

data class SteamWishlistResponse(
    @param:Json(name = "items") val items: List<SteamWishlistItemDto>? = null
)

data class SteamWishlistItemDto(
    @param:Json(name = "appid") val appId: Long = 0L,
    @param:Json(name = "priority") val priority: Int? = null,
    @param:Json(name = "date_added") val dateAdded: Long? = null
)

/**
 * Steam Web API: `ISteamUser/ResolveVanityURL/v0001/`
 */
data class SteamVanityEnvelope(
    @param:Json(name = "response") val response: SteamVanityResponse? = null
)

data class SteamVanityResponse(
    @param:Json(name = "steamid") val steamId: String? = null,
    @param:Json(name = "success") val success: Int? = null,
    @param:Json(name = "message") val message: String? = null
)

/**
 * Steam Web API: `ISteamUserStats/GetPlayerAchievements/v0001/`
 */
data class SteamPlayerAchievementsEnvelope(
    @param:Json(name = "playerstats") val playerStats: SteamPlayerStatsDto? = null
)

data class SteamPlayerStatsDto(
    @param:Json(name = "steamID") val steamId: String? = null,
    @param:Json(name = "gameName") val gameName: String? = null,
    @param:Json(name = "success") val success: Boolean? = null,
    @param:Json(name = "achievements") val achievements: List<SteamPlayerAchievementDto>? = null
)

data class SteamPlayerAchievementDto(
    @param:Json(name = "apiname") val apiName: String = "",
    @param:Json(name = "achieved") val achieved: Int = 0,
    @param:Json(name = "unlocktime") val unlockTime: Long = 0L,
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "description") val description: String? = null
)

/**
 * Steam Web API: `ISteamUserStats/GetSchemaForGame/v2/`
 * Liefert Icons, Titel, Beschreibungen und das `hidden`-Flag (`1` = Spoiler/Versteckt).
 */
data class SteamGameSchemaEnvelope(
    @param:Json(name = "game") val game: SteamGameSchemaDto? = null
)

data class SteamGameSchemaDto(
    @param:Json(name = "gameName") val gameName: String? = null,
    @param:Json(name = "availableGameStats") val availableGameStats: SteamAvailableGameStatsDto? = null
)

data class SteamAvailableGameStatsDto(
    @param:Json(name = "achievements") val achievements: List<SteamSchemaAchievementDto>? = null
)

data class SteamSchemaAchievementDto(
    @param:Json(name = "name") val apiName: String = "",
    @param:Json(name = "displayName") val displayName: String? = null,
    @param:Json(name = "hidden") val hidden: Int = 0, // 1 = Versteckte Errungenschaft (Spoiler!)
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "icon") val icon: String? = null,
    @param:Json(name = "icongray") val iconGray: String? = null
)

/**
 * Domain-Modell für eine Spiel-Errungenschaft (Achievement) inkl. Spoiler-Schutz.
 */
data class SteamAchievement(
    val apiName: String,
    val title: String,
    val description: String,
    val unlockedIconUrl: String = "",
    val lockedIconUrl: String = "",
    val isUnlocked: Boolean = false,
    val unlockTimestamp: Long = 0L,
    val isHiddenSpoiler: Boolean = false
)

object SteamAchievementSerializer {
    fun toJsonString(list: List<SteamAchievement>): String {
        val arr = JSONArray()
        list.forEach { ach ->
            val obj = JSONObject().apply {
                put("apiName", ach.apiName)
                put("title", ach.title)
                put("description", ach.description)
                put("unlockedIconUrl", ach.unlockedIconUrl)
                put("lockedIconUrl", ach.lockedIconUrl)
                put("isUnlocked", ach.isUnlocked)
                put("unlockTimestamp", ach.unlockTimestamp)
                put("isHiddenSpoiler", ach.isHiddenSpoiler)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun fromJsonString(json: String): List<SteamAchievement> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val result = mutableListOf<SteamAchievement>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(
                    SteamAchievement(
                        apiName = obj.optString("apiName", "ach_$i"),
                        title = obj.optString("title", "Errungenschaft"),
                        description = obj.optString("description", ""),
                        unlockedIconUrl = obj.optString("unlockedIconUrl", ""),
                        lockedIconUrl = obj.optString("lockedIconUrl", ""),
                        isUnlocked = obj.optBoolean("isUnlocked", false),
                        unlockTimestamp = obj.optLong("unlockTimestamp", 0L),
                        isHiddenSpoiler = obj.optBoolean("isHiddenSpoiler", false)
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * Vereinheitlichtes Suchergebnis-Modell für die UI (IGDB & Steam).
 */
data class ExternalGameSearchResult(
    val apiId: String,
    val title: String,
    val coverImageUrl: String,
    val releaseYear: String = "",
    val genres: String = "",
    val sourceLabel: String // z.B. "IGDB" oder "STEAM"
)
