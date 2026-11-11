package com.example.data.remote

import com.example.config.AppConfig
import com.example.data.local.LocalGame
import com.squareup.moshi.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * Datenmodelle für Twitch OAuth2, IGDB API v4, Steam Web/Store API,
 * SteamDB / Live-Preis-Tracker sowie das Steam-Inventar.
 */

data class TwitchTokenResponse(
    @param:Json(name = "access_token") val accessToken: String = "",
    @param:Json(name = "expires_in") val expiresIn: Long = 0L,
    @param:Json(name = "token_type") val tokenType: String = ""
)

data class IgdbGameDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "name") val name: String = "",
    @param:Json(name = "cover") val cover: IgdbCoverDto? = null
) {
    fun toCoverBigUrl(): String {
        val imageId = cover?.imageId?.trim().orEmpty()
        return if (imageId.isNotEmpty()) {
            AppConfig.getIgdbCoverImageUrl(imageId)
        } else {
            ""
        }
    }
}

data class IgdbCoverDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "image_id") val imageId: String? = null
)

data class SteamStoreSearchResponse(
    @param:Json(name = "total") val total: Int = 0,
    @param:Json(name = "items") val items: List<SteamStoreSearchItemDto>? = null
)

data class SteamStoreSearchItemDto(
    @param:Json(name = "id") val id: Long = 0L,
    @param:Json(name = "name") val name: String = "",
    @param:Json(name = "tiny_image") val tinyImage: String? = null
)

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

data class SteamVanityEnvelope(
    @param:Json(name = "response") val response: SteamVanityResponse? = null
)

data class SteamVanityResponse(
    @param:Json(name = "steamid") val steamId: String? = null,
    @param:Json(name = "success") val success: Int? = null,
    @param:Json(name = "message") val message: String? = null
)

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
    @param:Json(name = "hidden") val hidden: Int = 0,
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "icon") val icon: String? = null,
    @param:Json(name = "icongray") val iconGray: String? = null
)

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

data class ExternalGameSearchResult(
    val apiId: String,
    val title: String,
    val coverImageUrl: String,
    val releaseYear: String = "",
    val genres: String = "",
    val sourceLabel: String
)

/**
 * Ergebnisbericht des Steam-Bibliotheks-Imports inkl. Diagnose-Status,
 * falls Steam ohne Web-Session / wegen privater Spieldetails nur die ~6 Profil-Vorschauspiele zurückgibt.
 */
data class SteamLibraryImportReport(
    val games: List<LocalGame>,
    val usedWebApi: Boolean = false,
    val usedAuthenticatedSession: Boolean = false,
    val onlyPublicShowcaseFound: Boolean = false,
    val diagnosticInfo: String = ""
)

/**
 * Live-Preis- & SteamDB-Informationen für ein Spiel.
 */
data class SteamPriceOverviewInfo(
    val steamAppId: Long,
    val currentPriceCents: Int, // 0 = Kostenlos, > 0 = Cent
    val originalPriceCents: Int,
    val discountPercent: Int,
    val formattedCurrentPrice: String,
    val formattedOriginalPrice: String = "",
    val lowestHistoricalPrice: String = "",
    val steamDbUrl: String = "https://steamdb.info/app/$steamAppId/",
    val steamStoreUrl: String = "https://store.steampowered.com/app/$steamAppId/"
) {
    val appId: Long
        get() = steamAppId
    val formattedPrice: String
        get() = formattedCurrentPrice
}

/**
 * Unterstützte Steam-Inventar-Kategorien (Context-Auswahl).
 */
enum class SteamInventoryGameContext(
    val label: String,
    val subtitle: String,
    val appId: Long,
    val contextId: Long
) {
    STEAM_COMMUNITY(
        label = "Steam Community",
        subtitle = "Sammelkarten, Hintergründe, Emoticons & Booster",
        appId = 753L,
        contextId = 6L
    ),
    CS2(
        label = "Counter-Strike 2",
        subtitle = "CS2 / CS:GO Skins, Cases, Sticker & Agents",
        appId = 730L,
        contextId = 2L
    ),
    DOTA2(
        label = "Dota 2",
        subtitle = "Cosmetics, Immortals & Sets",
        appId = 570L,
        contextId = 2L
    ),
    TF2(
        label = "Team Fortress 2",
        subtitle = "Items, Hüte, Mann Co. Kisten & Waffen",
        appId = 440L,
        contextId = 2L
    ),
    RUST(
        label = "Rust",
        subtitle = "Skins, Kleidung & Ausrüstung",
        appId = 252490L,
        contextId = 2L
    ),
    PUBG(
        label = "PUBG: Battlegrounds",
        subtitle = "Outfits, Waffen-Skins & Kisten",
        appId = 578080L,
        contextId = 2L
    )
}

/**
 * Einzelnes Item aus dem Steam-Inventar eines Spielers.
 */
data class SteamInventoryItem(
    val assetId: String,
    val classId: String,
    val appId: Long,
    val contextId: Long,
    val name: String,
    val marketHashName: String,
    val typeDescription: String,
    val iconUrl: String,
    val amount: Int = 1,
    val rarityColorHex: String = "",
    val isTradable: Boolean = false,
    val isMarketable: Boolean = false,
    val marketPriceFormatted: String = ""
) {
    val typeLabel: String
        get() = typeDescription

    val steamMarketUrl: String
        get() = "https://steamcommunity.com/market/listings/$appId/${ java.net.URLEncoder.encode(marketHashName.ifBlank { name }, "UTF-8").replace("+", "%20") }"

    val steamDbAppUrl: String
        get() = "https://steamdb.info/app/$appId/items/"
}

/**
 * Ergebnis beim Versuch, ein Spiel direkt über die eingeloggte Steam Store Sitzung zur Steam-Wunschliste hinzuzufügen.
 */
data class SteamWishlistAddResult(
    val successOnSteam: Boolean,
    val appId: Long,
    val message: String
)
