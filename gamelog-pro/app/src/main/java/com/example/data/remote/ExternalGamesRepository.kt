package com.example.data.remote

import android.text.Html
import com.example.config.AppConfig
import com.example.data.local.LocalGame
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Modular aufgebautes Repository für die IGDB API (via Twitch OAuth2) und Steam Web/Store/Community APIs.
 * Unterstützt:
 * 1. IGDB API v4 Spielesuche (mit Twitch OAuth2 Token-Cache & Steam Store Fallback)
 * 2. Vollständigen Steam Owned Games (Bibliothek) & Wishlist Import (ohne Limitierung, kombiniert API + Community)
 * 3. Steam Errungenschaften (Achievements) inkl. Unterscheidung in "Erreicht" / "Fehlt noch" und Spoiler-Schutz.
 */
class ExternalGamesRepository {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()
            chain.proceed(req)
        }
        .build()

    private val twitchAuthService: TwitchAuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.TWITCH_AUTH_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TwitchAuthApiService::class.java)
    }

    private val igdbService: IgdbApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.IGDB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(IgdbApiService::class.java)
    }

    private val steamService: SteamApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.STEAM_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SteamApiService::class.java)
    }

    private val steamStoreService: SteamStoreApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.STEAM_STORE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SteamStoreApiService::class.java)
    }

    private val tokenMutex = Mutex()
    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var tokenExpiresAtEpochMs: Long = 0L

    private suspend fun getOrFetchTwitchBearerToken(): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        val existingToken = cachedAccessToken
        if (!existingToken.isNullOrBlank() && now < tokenExpiresAtEpochMs) {
            return existingToken
        }

        val clientId = AppConfig.TWITCH_CLIENT_ID
        val clientSecret = AppConfig.TWITCH_CLIENT_SECRET
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw IllegalStateException(
                "TWITCH_CLIENT_ID oder TWITCH_CLIENT_SECRET fehlt in AppConfig."
            )
        }

        val tokenResponse = twitchAuthService.getAccessToken(
            clientId = clientId,
            clientSecret = clientSecret,
            grantType = "client_credentials"
        )
        val newToken = tokenResponse.accessToken.trim()
        if (newToken.isBlank()) {
            throw IllegalStateException("Twitch OAuth2 lieferte einen leeren Access Token zurück.")
        }

        cachedAccessToken = newToken
        val lifetimeSeconds = (tokenResponse.expiresIn - 60L).coerceAtLeast(60L)
        tokenExpiresAtEpochMs = now + lifetimeSeconds * 1000L
        return newToken
    }

    fun mapIgdbImageIdToCoverUrl(imageId: String?): String {
        val cleanId = imageId?.trim().orEmpty()
        return if (cleanId.isNotEmpty()) {
            AppConfig.getIgdbCoverImageUrl(cleanId)
        } else {
            ""
        }
    }

    private fun httpGetString(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sucht nach Spielen über die IGDB API v4 mit Apicalypse Plain-Text-Syntax:
     * `search "SUCHBEGRIFF"; fields name, cover.image_id; limit 20;`
     */
    suspend fun searchGames(query: String): Result<List<ExternalGameSearchResult>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        val results = mutableListOf<ExternalGameSearchResult>()
        val clientId = AppConfig.TWITCH_CLIENT_ID
        val clientSecret = AppConfig.TWITCH_CLIENT_SECRET

        if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
            try {
                val accessToken = getOrFetchTwitchBearerToken()
                val sanitizedQuery = trimmed.replace("\"", "\\\"")
                val apicalypseQuery = "search \"$sanitizedQuery\"; fields name, cover.image_id; limit 20;"
                val requestBody = apicalypseQuery.toRequestBody("text/plain; charset=utf-8".toMediaType())

                val igdbGames = igdbService.searchGames(
                    clientId = clientId,
                    bearerToken = "Bearer $accessToken",
                    apicalypseBody = requestBody
                )

                igdbGames.forEach { dto ->
                    if (dto.name.isNotBlank()) {
                        val coverUrl = mapIgdbImageIdToCoverUrl(dto.cover?.imageId)
                        results.add(
                            ExternalGameSearchResult(
                                apiId = "igdb_${dto.id}",
                                title = dto.name,
                                coverImageUrl = coverUrl,
                                releaseYear = "",
                                genres = "IGDB Story Game",
                                sourceLabel = "IGDB"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                cachedAccessToken = null
                tokenExpiresAtEpochMs = 0L
                if (results.isEmpty()) {
                    val fallback = fetchSteamStoreFallback(trimmed)
                    if (fallback.isNotEmpty()) {
                        return@withContext Result.success(fallback)
                    }
                    return@withContext Result.failure(e)
                }
            }
        }

        if (results.isEmpty()) {
            val fallback = fetchSteamStoreFallback(trimmed)
            results.addAll(fallback)
        }

        Result.success(results)
    }

    private suspend fun fetchSteamStoreFallback(query: String): List<ExternalGameSearchResult> {
        return try {
            val storeResponse = steamStoreService.searchStoreGames(query = query)
            storeResponse.items.orEmpty().mapNotNull { item ->
                if (item.id > 0 && item.name.isNotBlank()) {
                    ExternalGameSearchResult(
                        apiId = "steam_${item.id}",
                        title = item.name,
                        coverImageUrl = AppConfig.getSteamHeaderImageUrl(item.id),
                        releaseYear = "",
                        genres = "Steam Store",
                        sourceLabel = "STEAM"
                    )
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Bereinigt eine eingegebene Steam-ID oder Profil-URL und löst bei Bedarf Vanity-Namen auf.
     */
    suspend fun normalizeSteamId64(rawInput: String): String = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim().removeSuffix("/")
        val regex17 = Regex("(\\d{17})")
        val directMatch = regex17.find(trimmed)?.groupValues?.getOrNull(1)
        if (!directMatch.isNullOrBlank()) {
            return@withContext directMatch
        }

        val cleaned = trimmed.substringAfterLast("/").trim()
        if (cleaned.isBlank()) return@withContext ""

        val steamKey = AppConfig.STEAM_API_KEY
        if (steamKey.isNotBlank()) {
            try {
                val resolved = steamService.resolveVanityUrl(apiKey = steamKey, vanityUrl = cleaned)
                val steamId = resolved.response?.steamId
                if (!steamId.isNullOrBlank()) {
                    return@withContext steamId
                }
            } catch (_: Exception) {
            }
        }

        val xml = httpGetString("https://steamcommunity.com/id/$cleaned/?xml=1")
        if (!xml.isNullOrBlank()) {
            val match = Regex("<steamID64>(\\d{17})</steamID64>").find(xml)
            val extractedId = match?.groupValues?.getOrNull(1)
            if (!extractedId.isNullOrBlank()) {
                return@withContext extractedId
            }
        }

        cleaned
    }

    /**
     * Importiert ALLE Spiele aus der eigenen Steam-Spielebibliothek (Owned Games):
     * - Kombiniert `IPlayerService/GetOwnedGames` (inkl. Free-to-Play, Free-Sub & Unvetted Apps)
     *   UND den vollständigen Steam Community `data-profile-gameslist` + XML Feed,
     *   damit garantiert KEIN Spiel aus der Bibliothek fehlt!
     */
    suspend fun importSteamOwnedGames(
        rawSteamIdInput: String,
        knownTitles: Map<Long, String> = emptyMap()
    ): Result<List<LocalGame>> = withContext(Dispatchers.IO) {
        val steamId64 = normalizeSteamId64(rawSteamIdInput)
        if (!steamId64.matches(Regex("^\\d{17}$"))) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    "Ungültige SteamID64 ('$steamId64'). Bitte gib deine 17-stellige SteamID64 (z.B. 76561197960435530) oder deinen öffentlichen Steam-Profilnamen ein."
                )
            )
        }

        val combinedByAppId = LinkedHashMap<Long, LocalGame>()

        // Quelle 1: Offizielle Steam Web API (`IPlayerService/GetOwnedGames` mit allen Flags)
        val steamKey = AppConfig.STEAM_API_KEY
        if (steamKey.isNotBlank()) {
            try {
                val envelope = steamService.getOwnedGames(
                    apiKey = steamKey,
                    steamId = steamId64,
                    includeAppInfo = 1,
                    includePlayedFreeGames = 1,
                    includeFreeSub = 1,
                    skipUnvettedApps = false
                )
                val gamesDto = envelope.response?.games.orEmpty()
                if (gamesDto.isNotEmpty()) {
                    coroutineScope {
                        val mapped = gamesDto.chunked(12).flatMap { chunk ->
                            chunk.map { dto ->
                                async {
                                    if (dto.appId <= 0L) return@async null
                                    val resolvedTitle = dto.name?.takeIf { it.isNotBlank() }
                                        ?: knownTitles[dto.appId]
                                        ?: resolveSteamAppTitle(dto.appId)
                                        ?: "Steam App #${dto.appId}"
                                    val playtimeHours = (dto.playtimeForeverMinutes ?: 0) / 60
                                    LocalGame(
                                        id = "steam_${dto.appId}",
                                        title = resolvedTitle.trim(),
                                        coverImageUrl = AppConfig.getSteamHeaderImageUrl(dto.appId),
                                        isPlayed = (dto.playtimeForeverMinutes ?: 0) > 30,
                                        isOwned = true,
                                        is100Percent = false,
                                        personalRating = 0,
                                        personalNote = if (playtimeHours > 0) "Steam Import ($playtimeHours Std. Spielzeit)" else "Steam Bibliothek Import",
                                        isRecommended = false,
                                        genres = "Steam Library",
                                        playtimeHours = playtimeHours
                                    )
                                }
                            }.awaitAll().filterNotNull()
                        }
                        mapped.forEach { game ->
                            val appId = game.id.removePrefix("steam_").toLongOrNull() ?: return@forEach
                            combinedByAppId[appId] = game
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Quelle 2: Vollständige Steam Community HTML/JSON `data-profile-gameslist` (ergänzt fehlende oder F2P-Spiele!)
        val htmlGames = parseSteamCommunityHtmlGamesList(steamId64)
        htmlGames.forEach { game ->
            val appId = game.id.removePrefix("steam_").toLongOrNull() ?: return@forEach
            val existing = combinedByAppId[appId]
            if (existing == null) {
                combinedByAppId[appId] = game
            } else if (game.playtimeHours > existing.playtimeHours) {
                combinedByAppId[appId] = existing.copy(
                    playtimeHours = game.playtimeHours,
                    isPlayed = existing.isPlayed || game.isPlayed
                )
            }
        }

        // Quelle 3: Öffentlicher Steam Community XML Games-Feed (`?tab=all&xml=1`)
        val xmlGames = parseSteamCommunityXmlGamesList(steamId64)
        xmlGames.forEach { game ->
            val appId = game.id.removePrefix("steam_").toLongOrNull() ?: return@forEach
            if (!combinedByAppId.containsKey(appId)) {
                combinedByAppId[appId] = game
            }
        }

        if (combinedByAppId.isNotEmpty()) {
            val sortedList = combinedByAppId.values.sortedWith(
                compareByDescending<LocalGame> { it.playtimeHours }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            )
            return@withContext Result.success(sortedList)
        }

        Result.failure(
            IllegalStateException(
                "Spielebibliothek konnte nicht gelesen werden. Stelle in deinen Steam-Datenschutzeinstellungen sicher, dass 'Mein Profil' und 'Spieldetails' auf 'Öffentlich' stehen (und das Häkchen bei 'Gesamte Spielzeit immer privat halten' deaktiviert ist)."
            )
        )
    }

    private fun parseSteamCommunityHtmlGamesList(steamId64: String): List<LocalGame> {
        val urls = listOf(
            "https://steamcommunity.com/profiles/$steamId64/games/?tab=all&l=german",
            "https://steamcommunity.com/profiles/$steamId64/games?tab=all"
        )
        val result = LinkedHashMap<Long, LocalGame>()

        for (url in urls) {
            val html = httpGetString(url) ?: continue
            try {
                val jsonCandidate = extractGamesListJsonFromHtml(html) ?: continue
                val root = JSONObject(jsonCandidate)
                val rgGames = root.optJSONArray("rgGames") ?: JSONArray()
                for (i in 0 until rgGames.length()) {
                    val obj = rgGames.optJSONObject(i) ?: continue
                    val appId = obj.optLong("appid", 0L)
                    val name = decodeHtmlEntities(obj.optString("name", "").trim())
                    val playtimeForever = obj.optInt("playtime_forever", 0)
                    val playtimeHours = playtimeForever / 60
                    if (appId > 0L && name.isNotBlank()) {
                        result[appId] = LocalGame(
                            id = "steam_$appId",
                            title = name,
                            coverImageUrl = AppConfig.getSteamHeaderImageUrl(appId),
                            isPlayed = playtimeForever > 30,
                            isOwned = true,
                            is100Percent = false,
                            personalRating = 0,
                            personalNote = if (playtimeHours > 0) "Steam Import ($playtimeHours Std. Spielzeit)" else "Steam Bibliothek Import",
                            isRecommended = false,
                            genres = "Steam Library",
                            playtimeHours = playtimeHours
                        )
                    }
                }
                if (result.isNotEmpty()) break
            } catch (_: Exception) {
            }
        }
        return result.values.toList()
    }

    private fun extractGamesListJsonFromHtml(html: String): String? {
        val markerDouble = "data-profile-gameslist=\""
        val idxDouble = html.indexOf(markerDouble)
        if (idxDouble >= 0) {
            val start = idxDouble + markerDouble.length
            val end = html.indexOf('"', start)
            if (end > start) {
                return decodeHtmlEntities(html.substring(start, end))
            }
        }

        val markerSingle = "data-profile-gameslist='"
        val idxSingle = html.indexOf(markerSingle)
        if (idxSingle >= 0) {
            val start = idxSingle + markerSingle.length
            val end = html.indexOf('\'', start)
            if (end > start) {
                return decodeHtmlEntities(html.substring(start, end))
            }
        }

        if (html.contains("var rgGames =")) {
            val arrStr = html.substringAfter("var rgGames =").substringBefore("];").trim() + "]"
            return """{"rgGames":$arrStr}"""
        }
        return null
    }

    private fun parseSteamCommunityXmlGamesList(steamId64: String): List<LocalGame> {
        val urlsToTry = listOf(
            "https://steamcommunity.com/profiles/$steamId64/games?tab=all&xml=1",
            "https://steamcommunity.com/profiles/$steamId64/?xml=1"
        )
        val xmlGames = mutableListOf<LocalGame>()
        val seenIds = mutableSetOf<Long>()

        for (url in urlsToTry) {
            val xml = httpGetString(url) ?: continue
            val gameBlocks = Regex("<(game|mostPlayedGame)>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(xml)
            for (block in gameBlocks) {
                val content = block.groupValues[2]
                val appId = Regex("<appID>(\\d+)</appID>").find(content)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    ?: Regex("steamcommunity\\.com/app/(\\d+)").find(content)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    ?: continue
                if (!seenIds.add(appId)) continue

                val rawName = Regex("<(name|gameName)><!\\[CDATA\\[(.*?)]]></\\1>", setOf(RegexOption.DOT_MATCHES_ALL))
                    .find(content)?.groupValues?.getOrNull(2)
                    ?: Regex("<(name|gameName)>(.*?)</\\1>").find(content)?.groupValues?.getOrNull(2)
                    ?: continue
                val hoursStr = Regex("<(hoursOnRecord|hoursPlayed)>([0-9.,]+)</\\1>")
                    .find(content)?.groupValues?.getOrNull(2)
                    ?.replace(",", "")
                val playtimeHours = hoursStr?.toDoubleOrNull()?.toInt() ?: 0
                val cleanTitle = decodeHtmlEntities(rawName.trim())
                if (cleanTitle.isNotBlank()) {
                    xmlGames.add(
                        LocalGame(
                            id = "steam_$appId",
                            title = cleanTitle,
                            coverImageUrl = AppConfig.getSteamHeaderImageUrl(appId),
                            isPlayed = playtimeHours > 0,
                            isOwned = true,
                            is100Percent = false,
                            personalRating = 0,
                            personalNote = if (playtimeHours > 0) "Steam Import ($playtimeHours Std. Spielzeit)" else "Steam Bibliothek Import",
                            isRecommended = false,
                            genres = "Steam Library",
                            playtimeHours = playtimeHours
                        )
                    )
                }
            }
            if (xmlGames.isNotEmpty()) break
        }
        return xmlGames
    }

    /**
     * Importiert die KOMPLETTE öffentliche Steam-Wunschliste (`isOwned = false`) ohne künstliches Limit.
     * Nutzt Known-Titles-Cache + Steam Bulk WishlistData + parallele AppDetails-Auflösung für Echtzeit-Geschwindigkeit.
     */
    suspend fun importSteamWishlist(
        rawSteamIdInput: String,
        knownTitles: Map<Long, String> = emptyMap()
    ): Result<List<LocalGame>> = withContext(Dispatchers.IO) {
        val steamId64 = normalizeSteamId64(rawSteamIdInput)
        if (!steamId64.matches(Regex("^\\d{17}$"))) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    "Bitte gib eine gültige 17-stellige SteamID64 oder deinen Steam-Profilnamen ein."
                )
            )
        }

        try {
            val steamKey = AppConfig.STEAM_API_KEY.takeIf { it.isNotBlank() }
            val envelope = steamService.getWishlist(
                steamId = steamId64,
                apiKey = steamKey
            )
            // Alle Wishlist-Items sortiert nach Priorität (kein .take(40) Limit mehr!)
            val wishlistItems = envelope.response?.items.orEmpty()
                .filter { it.appId > 0L }
                .sortedBy { it.priority ?: Int.MAX_VALUE }

            // Zusätzlich Bulk-Titel aus dem Steam Store Wishlistdata-Endpunkt vorladen
            val bulkTitleMap = fetchBulkWishlistTitles(steamId64)

            if (wishlistItems.isEmpty() && bulkTitleMap.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val appIdsOrdered = if (wishlistItems.isNotEmpty()) {
                wishlistItems.map { it.appId }
            } else {
                bulkTitleMap.keys.toList()
            }

            val resolvedWishlist = coroutineScope {
                appIdsOrdered.chunked(10).flatMap { chunk ->
                    chunk.map { appId ->
                        async {
                            val title = knownTitles[appId]?.takeIf { !it.startsWith("Steam App #") }
                                ?: bulkTitleMap[appId]?.takeIf { it.isNotBlank() }
                                ?: resolveSteamAppTitle(appId)
                                ?: "Steam App #$appId"
                            LocalGame(
                                id = "steam_$appId",
                                title = title,
                                coverImageUrl = AppConfig.getSteamHeaderImageUrl(appId),
                                isPlayed = false,
                                isOwned = false,
                                is100Percent = false,
                                personalRating = 0,
                                personalNote = "Importiert aus deiner Steam-Wunschliste",
                                isRecommended = false,
                                genres = "Steam Wishlist"
                            )
                        }
                    }.awaitAll()
                }
            }
            Result.success(resolvedWishlist)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchBulkWishlistTitles(steamId64: String): Map<Long, String> {
        val map = LinkedHashMap<Long, String>()
        try {
            val body = httpGetString("https://store.steampowered.com/wishlist/profiles/$steamId64/wishlistdata/?p=0&l=german")
                ?: return emptyMap()
            if (body.trim().startsWith("{")) {
                val json = JSONObject(body)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val appId = key.toLongOrNull() ?: continue
                    val itemObj = json.optJSONObject(key) ?: continue
                    val name = decodeHtmlEntities(itemObj.optString("name", "").trim())
                    if (name.isNotBlank()) {
                        map[appId] = name
                    }
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    /**
     * Lädt alle Errungenschaften (Achievements) für ein Spiel inkl. Spoiler-Erkennung.
     */
    suspend fun fetchGameAchievements(
        game: LocalGame,
        rawSteamIdInput: String
    ): Result<List<SteamAchievement>> = withContext(Dispatchers.IO) {
        val appId = resolveSteamAppIdForGame(game)
        if (appId == null || appId <= 0L) {
            val cached = SteamAchievementSerializer.fromJsonString(game.achievementsJson)
            return@withContext if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(IllegalStateException("Für '${game.title}' wurde keine passende Steam-AppID gefunden."))
            }
        }

        val steamId64 = if (rawSteamIdInput.isNotBlank()) {
            normalizeSteamId64(rawSteamIdInput).takeIf { it.matches(Regex("^\\d{17}$")) }
        } else null

        val steamKey = AppConfig.STEAM_API_KEY

        if (steamKey.isNotBlank()) {
            try {
                val schemaEnv = steamService.getSchemaForGame(
                    apiKey = steamKey,
                    appId = appId,
                    language = "german"
                )
                val schemaList = schemaEnv.game?.availableGameStats?.achievements.orEmpty()

                val playerMap: Map<String, SteamPlayerAchievementDto> = if (steamId64 != null) {
                    try {
                        val playerEnv = steamService.getPlayerAchievements(
                            appId = appId,
                            apiKey = steamKey,
                            steamId = steamId64,
                            language = "german"
                        )
                        playerEnv.playerStats?.achievements.orEmpty().associateBy { it.apiName }
                    } catch (_: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }

                if (schemaList.isNotEmpty()) {
                    val combined = schemaList.map { schemaAch ->
                        val playerAch = playerMap[schemaAch.apiName]
                        val unlocked = (playerAch?.achieved ?: 0) == 1
                        val title = (playerAch?.name?.takeIf { it.isNotBlank() }
                            ?: schemaAch.displayName?.takeIf { it.isNotBlank() }
                            ?: schemaAch.apiName).trim()
                        val desc = (playerAch?.description?.takeIf { it.isNotBlank() }
                            ?: schemaAch.description?.takeIf { it.isNotBlank() }
                            ?: if (schemaAch.hidden == 1) "Versteckte Story-Errungenschaft (Spoiler)" else "").trim()
                        val isSpoiler = schemaAch.hidden == 1 || (!unlocked && isPotentialStorySpoiler(title, desc))

                        SteamAchievement(
                            apiName = schemaAch.apiName,
                            title = title,
                            description = desc,
                            unlockedIconUrl = schemaAch.icon.orEmpty(),
                            lockedIconUrl = schemaAch.iconGray.orEmpty().ifBlank { schemaAch.icon.orEmpty() },
                            isUnlocked = unlocked,
                            unlockTimestamp = playerAch?.unlockTime ?: 0L,
                            isHiddenSpoiler = isSpoiler
                        )
                    }
                    return@withContext Result.success(combined)
                }
            } catch (_: Exception) {
            }
        }

        if (steamId64 != null) {
            val playerXmlAchievements = parsePlayerSteamAchievementsXml(steamId64, appId)
            if (playerXmlAchievements.isNotEmpty()) {
                return@withContext Result.success(playerXmlAchievements)
            }
        }

        val globalHtmlAchievements = parseGlobalSteamAchievementsHtml(appId)
        if (globalHtmlAchievements.isNotEmpty()) {
            return@withContext Result.success(globalHtmlAchievements)
        }

        val cached = SteamAchievementSerializer.fromJsonString(game.achievementsJson)
        if (cached.isNotEmpty()) {
            return@withContext Result.success(cached)
        }

        Result.failure(
            IllegalStateException(
                "Für '${game.title}' konnten aktuell keine Steam-Errungenschaften geladen werden."
            )
        )
    }

    private suspend fun resolveSteamAppIdForGame(game: LocalGame): Long? {
        if (game.id.startsWith("steam_")) {
            val parsed = game.id.removePrefix("steam_").trim().toLongOrNull()
            if (parsed != null && parsed > 0L) return parsed
        }
        return try {
            val search = steamStoreService.searchStoreGames(query = game.title)
            search.items?.firstOrNull()?.id?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePlayerSteamAchievementsXml(steamId64: String, appId: Long): List<SteamAchievement> {
        val xml = httpGetString("https://steamcommunity.com/profiles/$steamId64/stats/$appId/?xml=1&l=german")
            ?: return emptyList()

        val blocks = Regex("<achievement\\s+closed=\"([01])\"[^>]*>(.*?)</achievement>", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(xml)

        val result = mutableListOf<SteamAchievement>()
        for ((index, match) in blocks.withIndex()) {
            val isClosed = match.groupValues[1] == "1"
            val body = match.groupValues[2]

            val apiName = Regex("<apiname><!\\[CDATA\\[(.*?)]]></apiname>").find(body)?.groupValues?.getOrNull(1)
                ?: Regex("<apiname>(.*?)</apiname>").find(body)?.groupValues?.getOrNull(1)
                ?: "ach_$index"
            val rawTitle = Regex("<name><!\\[CDATA\\[(.*?)]]></name>", setOf(RegexOption.DOT_MATCHES_ALL)).find(body)?.groupValues?.getOrNull(1)
                ?: Regex("<name>(.*?)</name>").find(body)?.groupValues?.getOrNull(1)
                ?: apiName
            val rawDesc = Regex("<description><!\\[CDATA\\[(.*?)]]></description>", setOf(RegexOption.DOT_MATCHES_ALL)).find(body)?.groupValues?.getOrNull(1)
                ?: Regex("<description>(.*?)</description>").find(body)?.groupValues?.getOrNull(1)
                ?: ""
            val iconClosed = Regex("<iconClosed><!\\[CDATA\\[(.*?)]]></iconClosed>").find(body)?.groupValues?.getOrNull(1)
                ?: Regex("<iconClosed>(.*?)</iconClosed>").find(body)?.groupValues?.getOrNull(1)
                ?: ""
            val iconOpen = Regex("<iconOpen><!\\[CDATA\\[(.*?)]]></iconOpen>").find(body)?.groupValues?.getOrNull(1)
                ?: Regex("<iconOpen>(.*?)</iconOpen>").find(body)?.groupValues?.getOrNull(1)
                ?: iconClosed
            val unlockTime = Regex("<unlockTimestamp>(\\d+)</unlockTimestamp>").find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: 0L

            val title = decodeHtmlEntities(rawTitle.trim())
            val desc = decodeHtmlEntities(rawDesc.trim())
            val isSpoiler = !isClosed && (desc.isBlank() || isPotentialStorySpoiler(title, desc))

            result.add(
                SteamAchievement(
                    apiName = apiName.trim(),
                    title = title,
                    description = desc.ifBlank { "Versteckte Story-Errungenschaft (Spoiler)" },
                    unlockedIconUrl = iconClosed.trim(),
                    lockedIconUrl = iconOpen.trim(),
                    isUnlocked = isClosed,
                    unlockTimestamp = unlockTime,
                    isHiddenSpoiler = isSpoiler
                )
            )
        }
        return result
    }

    private fun parseGlobalSteamAchievementsHtml(appId: Long): List<SteamAchievement> {
        val html = httpGetString("https://steamcommunity.com/stats/$appId/achievements/?l=german")
            ?: return emptyList()

        val rowRegex = Regex(
            """<div class="achieveRow[^"]*">.*?<img src="([^"]+)".*?<h3[^>]*>(.*?)</h3>.*?<h5[^>]*>(.*?)</h5>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val result = mutableListOf<SteamAchievement>()
        for ((idx, match) in rowRegex.findAll(html).withIndex()) {
            val iconUrl = match.groupValues[1].trim()
            val rawTitle = decodeHtmlEntities(match.groupValues[2].replace(Regex("<[^>]+>"), "").trim())
            val rawDesc = decodeHtmlEntities(match.groupValues[3].replace(Regex("<[^>]+>"), "").trim())
            if (rawTitle.isNotBlank()) {
                val isSpoiler = rawDesc.isBlank() ||
                    rawDesc.contains("Versteckte Errungenschaft", ignoreCase = true) ||
                    rawDesc.contains("Hidden achievement", ignoreCase = true) ||
                    isPotentialStorySpoiler(rawTitle, rawDesc)

                result.add(
                    SteamAchievement(
                        apiName = "global_ach_$idx",
                        title = rawTitle,
                        description = rawDesc.ifBlank { "Versteckte Story-Errungenschaft (Spoiler)" },
                        unlockedIconUrl = iconUrl,
                        lockedIconUrl = iconUrl,
                        isUnlocked = false,
                        unlockTimestamp = 0L,
                        isHiddenSpoiler = isSpoiler
                    )
                )
            }
        }
        return result
    }

    private fun isPotentialStorySpoiler(title: String, description: String): Boolean {
        if (description.isBlank()) return true
        val combined = "$title $description".lowercase(Locale.ROOT)
        val spoilerKeywords = listOf(
            "versteckt", "hidden", "geheim", "secret", "spoiler",
            "kapitel", "chapter", "akt ", "act ", "epilog", "epilogue", "prolog",
            "finale", "ende", "ending", "story", "hauptquest", "main quest",
            "besiege", "defeat", "töte", "kill", "rette", "save ", "entdecke die wahrheit",
            "boss", "endgegner", "schließe die geschichte", "complete the story"
        )
        return spoilerKeywords.any { combined.contains(it) }
    }

    private fun decodeHtmlEntities(input: String): String {
        if (input.isBlank()) return input
        return try {
            Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (_: Exception) {
            input
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }
    }

    private fun resolveSteamAppTitle(appId: Long): String? {
        return try {
            val body = httpGetString("${AppConfig.STEAM_STORE_BASE_URL}appdetails?appids=$appId&filters=basic&l=german")
                ?: return null
            val root = JSONObject(body)
            val appObj = root.optJSONObject(appId.toString())
            if (appObj != null && appObj.optBoolean("success", false)) {
                appObj.optJSONObject("data")?.optString("name")?.takeIf { it.isNotBlank() }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
