package com.example.data.remote

import android.text.Html
import android.webkit.CookieManager
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
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Modular aufgebautes Repository für:
 * 1. IGDB API v4 Spielesuche (mit Twitch OAuth2 Token-Cache & Steam Store Fallback)
 * 2. Vollständigen Steam Owned Games (Bibliothek 250+ Spiele) & Wishlist Import (inkl. WebView-Cookie-Bridge + `dynamicstore/userdata` + `GetOwnedGames`)
 * 3. SteamDB & Live Steam Store Preis-Tracker + Preisalarm-Auswertung
 * 4. Steam-Inventar Viewer (`steamcommunity.com/inventory/...` + Live Community Market Preisabfrage)
 * 5. Steam Errungenschaften (Achievements) inkl. Spoiler-Schutz.
 */
class ExternalGamesRepository {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * Liest vorhandene Steam-Session-Cookies (`steamLoginSecure`, `sessionid`) aus dem Android WebView `CookieManager`,
     * damit Anfragen an `steamcommunity.com` und `store.steampowered.com` als eingeloggter Besitzer ausgeführt werden
     * und selbst bei "Spieldetails: Nur Freunde / Privat" alle 250+ Spiele geladen werden!
     */
    private fun buildMergedCookieHeader(requestUrl: String): String {
        val baseCookies = "birthtime=0; mature_content=1; lastagecheckage=1-0-1990; wants_mature_content=1"
        return try {
            val cookieManager = CookieManager.getInstance()
            val urlCookies = cookieManager.getCookie(requestUrl).orEmpty().trim()
            val communityCookies = cookieManager.getCookie("https://steamcommunity.com").orEmpty().trim()
            val storeCookies = cookieManager.getCookie("https://store.steampowered.com").orEmpty().trim()
            listOf(baseCookies, urlCookies, communityCookies, storeCookies)
                .filter { it.isNotBlank() }
                .joinToString("; ")
        } catch (_: Throwable) {
            baseCookies
        }
    }

    fun hasActiveSteamWebSession(): Boolean {
        return try {
            val cookies = CookieManager.getInstance().getCookie("https://steamcommunity.com").orEmpty() +
                ";" + CookieManager.getInstance().getCookie("https://store.steampowered.com").orEmpty()
            cookies.contains("steamLoginSecure=")
        } catch (_: Throwable) {
            false
        }
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val mergedCookies = buildMergedCookieHeader(original.url.toString())
            val req = original.newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cookie", mergedCookies)
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
     * Bereinigt eine eingegebene Steam-ID oder Profil-URL und löst bei Bedarf Vanity-Namen oder aktive Cookie-Sessions auf.
     */
    suspend fun normalizeSteamId64(rawInput: String): String = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim().removeSuffix("/")
        val regex17 = Regex("(\\d{17})")
        val directMatch = regex17.find(trimmed)?.groupValues?.getOrNull(1)
        if (!directMatch.isNullOrBlank()) {
            return@withContext directMatch
        }

        val cleaned = trimmed
            .substringBefore("?")
            .removeSuffix("/games")
            .removeSuffix("/wishlist")
            .removeSuffix("/inventory")
            .removeSuffix("/")
            .substringAfterLast("/")
            .trim()

        if (cleaned.isNotBlank()) {
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

            val html = httpGetString("https://steamcommunity.com/id/$cleaned")
            if (!html.isNullOrBlank()) {
                val match = Regex("\"steamid\"\\s*:\\s*\"(\\d{17})\"").find(html)
                    ?: Regex("g_rgProfileData\\s*=\\s*\\{[^}]*\"steamid\"\\s*:\\s*\"(\\d{17})\"").find(html)
                val extractedId = match?.groupValues?.getOrNull(1)
                if (!extractedId.isNullOrBlank()) {
                    return@withContext extractedId
                }
            }
        }

        // Falls der Nutzer im WebView eingeloggt ist, kann die 17-stellige SteamID64 direkt aus `steamLoginSecure` gelesen werden
        try {
            val cookies = CookieManager.getInstance().getCookie("https://steamcommunity.com").orEmpty()
            val cookieId = Regex("steamLoginSecure=(\\d{17})").find(cookies)?.groupValues?.getOrNull(1)
            if (!cookieId.isNullOrBlank()) {
                return@withContext cookieId
            }
        } catch (_: Throwable) {
        }

        cleaned
    }

    /**
     * Importiert ALLE Spiele (250+ Titel) aus der eigenen Steam-Spielebibliothek (Owned Games)
     * und liefert einen detaillierten `SteamLibraryImportReport`:
     * - Quelle 1: `IPlayerService/GetOwnedGames/v1/` (mit `input_json` inkl. `include_free_sub=true` & `skip_unvetted_apps=false`) + `/v0001/`
     * - Quelle 2: Steam Community HTML `data-profile-gameslist` (`/profiles/$id/games/?tab=all` + `/my/games/?tab=all` mit WebView-Session-Cookies!)
     * - Quelle 3: Steam Dynamic Store Userdata (`store.steampowered.com/dynamicstore/userdata/` -> `rgOwnedApps`, liefert mit Session-Cookies ALLE 250+ AppIDs selbst bei privatem Profil!)
     * - Quelle 4: Steam Community XML `/games/?tab=all&xml=1` + Profil-Vorschau-Fallback.
     */
    suspend fun importSteamOwnedGamesWithReport(
        rawSteamIdInput: String,
        knownTitles: Map<Long, String> = emptyMap()
    ): Result<SteamLibraryImportReport> = withContext(Dispatchers.IO) {
        val steamId64 = normalizeSteamId64(rawSteamIdInput)
        if (!steamId64.matches(Regex("^\\d{17}$"))) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    "Ungültige SteamID64 ('$steamId64'). Bitte gib deine 17-stellige SteamID64 (z.B. 76561197960435530) oder deinen öffentlichen Steam-Profilnamen ein."
                )
            )
        }

        val combinedByAppId = LinkedHashMap<Long, LocalGame>()
        var countFromWebApi = 0
        var countFromHtmlOrDynamicStore = 0

        fun mergeGameEntry(game: LocalGame) {
            val appId = game.id.removePrefix("steam_").toLongOrNull() ?: return
            val existing = combinedByAppId[appId]
            if (existing == null) {
                combinedByAppId[appId] = game
            } else {
                val betterTitle = when {
                    !existing.title.startsWith("Steam App #") && existing.title.isNotBlank() -> existing.title
                    !game.title.startsWith("Steam App #") && game.title.isNotBlank() -> game.title
                    else -> existing.title
                }
                val maxHours = maxOf(existing.playtimeHours, game.playtimeHours)
                combinedByAppId[appId] = existing.copy(
                    title = betterTitle,
                    playtimeHours = maxHours,
                    isPlayed = existing.isPlayed || game.isPlayed || maxHours > 0,
                    personalNote = if (maxHours > 0) "Steam Import ($maxHours Std. Spielzeit)" else existing.personalNote
                )
            }
        }

        // Quelle 1: Offizielle Steam Web API (`IPlayerService/GetOwnedGames/v1/` mit `input_json` + `/v0001/`)
        val steamKey = AppConfig.STEAM_API_KEY
        if (steamKey.isNotBlank()) {
            try {
                val inputJson = """{"steamid":"$steamId64","include_appinfo":true,"include_played_free_games":true,"include_free_sub":true,"skip_unvetted_apps":false}"""
                val encodedJson = URLEncoder.encode(inputJson, "UTF-8")
                val v1Url = "${AppConfig.STEAM_API_BASE_URL}IPlayerService/GetOwnedGames/v1/?key=$steamKey&input_json=$encodedJson&format=json"
                val v1Body = httpGetString(v1Url)
                if (!v1Body.isNullOrBlank()) {
                    val parsed = parseOwnedGamesApiJson(v1Body, knownTitles)
                    countFromWebApi += parsed.size
                    parsed.forEach(::mergeGameEntry)
                }
            } catch (_: Exception) {
            }

            try {
                val v0001Url = "${AppConfig.STEAM_API_BASE_URL}IPlayerService/GetOwnedGames/v0001/?key=$steamKey&steamid=$steamId64&include_appinfo=1&include_played_free_games=1&format=json"
                val v0001Body = httpGetString(v0001Url)
                if (!v0001Body.isNullOrBlank()) {
                    val parsed = parseOwnedGamesApiJson(v0001Body, knownTitles)
                    countFromWebApi += parsed.size
                    parsed.forEach(::mergeGameEntry)
                }
            } catch (_: Exception) {
            }
        }

        // Quelle 2: Vollständige Steam Community HTML/JSON `data-profile-gameslist` (nutzt aktive WebView-Cookies falls eingeloggt!)
        val htmlGames = parseSteamCommunityHtmlGamesList(steamId64, rawSteamIdInput, knownTitles)
        countFromHtmlOrDynamicStore += htmlGames.size
        htmlGames.forEach(::mergeGameEntry)

        // Quelle 3: Steam Dynamic Store Userdata (`https://store.steampowered.com/dynamicstore/userdata/`)
        // Wenn der Nutzer über den Steam-Web-Login angemeldet ist, liefert dieser Endpunkt `rgOwnedApps` mit ALLEN 250+ AppIDs!
        if (hasActiveSteamWebSession()) {
            val dynamicOwnedGames = fetchDynamicStoreOwnedGames(knownTitles)
            countFromHtmlOrDynamicStore += dynamicOwnedGames.size
            dynamicOwnedGames.forEach(::mergeGameEntry)
        }

        // Quelle 4: Öffentlicher Steam Community XML Games-Feed (`/games/?tab=all&xml=1` + Profil-Vorschau)
        val xmlGames = parseSteamCommunityXmlGamesList(
            steamId64 = steamId64,
            includeProfileSummaryFallback = true
        )
        xmlGames.forEach(::mergeGameEntry)

        // Falls einzelne Spiele (z.B. aus `rgOwnedApps`) noch keinen aufgelösten Namen haben ("Steam App #..."),
        // laden wir ihre Titel und Preise schnell parallel über die Steam Store API nach!
        val unresolvedAppIds = combinedByAppId.entries
            .filter { it.value.title.startsWith("Steam App #") }
            .map { it.key }

        if (unresolvedAppIds.isNotEmpty()) {
            coroutineScope {
                unresolvedAppIds.chunked(16).forEach { chunk ->
                    val resolvedPairs = chunk.map { appId ->
                        async {
                            val title = knownTitles[appId] ?: resolveSteamAppTitle(appId)
                            if (!title.isNullOrBlank()) appId to title else null
                        }
                    }.awaitAll().filterNotNull()

                    resolvedPairs.forEach { (appId, resolvedTitle) ->
                        val current = combinedByAppId[appId]
                        if (current != null) {
                            combinedByAppId[appId] = current.copy(title = resolvedTitle.trim())
                        }
                    }
                }
            }
        }

        if (combinedByAppId.isNotEmpty()) {
            val sortedList = combinedByAppId.values.sortedWith(
                compareByDescending<LocalGame> { it.playtimeHours }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            )
            val onlyShowcase = countFromWebApi == 0 && countFromHtmlOrDynamicStore == 0 && sortedList.size <= 12
            return@withContext Result.success(
                SteamLibraryImportReport(
                    games = sortedList,
                    usedWebApi = countFromWebApi > 0,
                    usedAuthenticatedSession = hasActiveSteamWebSession() && countFromHtmlOrDynamicStore > 0,
                    onlyPublicShowcaseFound = onlyShowcase
                )
            )
        }

        Result.failure(
            IllegalStateException(
                "Spielebibliothek konnte nicht gelesen werden. Bitte nutze den Button 'Steam Web-Login (Alle 250+ Spiele freischalten)' oder stelle in deinen Steam-Datenschutzeinstellungen 'Spieldetails' auf 'Öffentlich'."
            )
        )
    }

    suspend fun importSteamOwnedGames(
        rawSteamIdInput: String,
        knownTitles: Map<Long, String> = emptyMap()
    ): Result<List<LocalGame>> {
        return importSteamOwnedGamesWithReport(rawSteamIdInput, knownTitles).map { it.games }
    }

    private fun fetchDynamicStoreOwnedGames(knownTitles: Map<Long, String>): List<LocalGame> {
        val list = mutableListOf<LocalGame>()
        try {
            val body = httpGetString("https://store.steampowered.com/dynamicstore/userdata/?t=${System.currentTimeMillis()}")
                ?: return emptyList()
            if (!body.trim().startsWith("{")) return emptyList()
            val json = JSONObject(body)
            val rgOwnedApps = json.optJSONArray("rgOwnedApps") ?: return emptyList()
            for (i in 0 until rgOwnedApps.length()) {
                val appId = rgOwnedApps.optLong(i, 0L)
                if (appId <= 0L) continue
                val title = knownTitles[appId] ?: "Steam App #$appId"
                list.add(
                    LocalGame(
                        id = "steam_$appId",
                        title = title,
                        coverImageUrl = AppConfig.getSteamHeaderImageUrl(appId),
                        isPlayed = false,
                        isOwned = true,
                        is100Percent = false,
                        personalRating = 0,
                        personalNote = "Steam Bibliothek Import",
                        isRecommended = false,
                        genres = "Steam Library"
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun parseOwnedGamesApiJson(
        rawJson: String,
        knownTitles: Map<Long, String>
    ): List<LocalGame> {
        val list = mutableListOf<LocalGame>()
        try {
            val root = JSONObject(rawJson)
            val responseObj = root.optJSONObject("response") ?: root
            val gamesArr = responseObj.optJSONArray("games") ?: return emptyList()
            for (i in 0 until gamesArr.length()) {
                val obj = gamesArr.optJSONObject(i) ?: continue
                val appId = obj.optLong("appid", 0L)
                if (appId <= 0L) continue
                val rawName = obj.optString("name", "").trim()
                val title = rawName.ifBlank {
                    knownTitles[appId] ?: "Steam App #$appId"
                }
                val playtimeMinutes = obj.optInt("playtime_forever", 0)
                val playtimeHours = playtimeMinutes / 60
                list.add(
                    LocalGame(
                        id = "steam_$appId",
                        title = title,
                        coverImageUrl = AppConfig.getSteamHeaderImageUrl(appId),
                        isPlayed = playtimeMinutes > 30,
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
        } catch (_: Exception) {
        }
        return list
    }

    private fun parseSteamCommunityHtmlGamesList(
        steamId64: String,
        rawInput: String,
        knownTitles: Map<Long, String>
    ): List<LocalGame> {
        val cleanedVanity = rawInput.trim()
            .removeSuffix("/")
            .substringAfterLast("/")
            .trim()
            .takeIf { it.isNotBlank() && !it.matches(Regex("^\\d{17}$")) }

        val urls = buildList {
            add("https://steamcommunity.com/profiles/$steamId64/games/?tab=all&l=german")
            add("https://steamcommunity.com/profiles/$steamId64/games/?tab=all")
            if (hasActiveSteamWebSession()) {
                add("https://steamcommunity.com/my/games/?tab=all&l=german")
            }
            if (cleanedVanity != null) {
                add("https://steamcommunity.com/id/$cleanedVanity/games/?tab=all&l=german")
                add("https://steamcommunity.com/id/$cleanedVanity/games/?tab=all")
            }
        }

        val result = LinkedHashMap<Long, LocalGame>()

        for (url in urls) {
            val html = httpGetString(url) ?: continue
            val jsonCandidate = extractGamesListJsonFromHtml(html)
            if (!jsonCandidate.isNullOrBlank()) {
                try {
                    val root = JSONObject(jsonCandidate)
                    val rgGames = root.optJSONArray("rgGames")
                    if (rgGames != null && rgGames.length() > 0) {
                        parseRgGamesJsonArray(rgGames, knownTitles).forEach { game ->
                            val appId = game.id.removePrefix("steam_").toLongOrNull()
                            if (appId != null) result[appId] = game
                        }
                    }
                } catch (_: Exception) {
                    try {
                        val rgStart = jsonCandidate.indexOf("\"rgGames\":")
                        if (rgStart >= 0) {
                            val arrStart = jsonCandidate.indexOf('[', rgStart)
                            if (arrStart >= 0) {
                                val arrJson = extractBalancedJsonArray(jsonCandidate, arrStart)
                                if (!arrJson.isNullOrBlank()) {
                                    val rgGames = JSONArray(arrJson)
                                    parseRgGamesJsonArray(rgGames, knownTitles).forEach { game ->
                                        val appId = game.id.removePrefix("steam_").toLongOrNull()
                                        if (appId != null) result[appId] = game
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                extractRgGamesViaRegex(jsonCandidate, knownTitles).forEach { game ->
                    val appId = game.id.removePrefix("steam_").toLongOrNull()
                    if (appId != null && !result.containsKey(appId)) {
                        result[appId] = game
                    }
                }
            }

            if (result.size > 20) break
        }
        return result.values.toList()
    }

    private fun parseRgGamesJsonArray(
        rgGames: JSONArray,
        knownTitles: Map<Long, String>
    ): List<LocalGame> {
        val list = mutableListOf<LocalGame>()
        for (i in 0 until rgGames.length()) {
            val obj = rgGames.optJSONObject(i) ?: continue
            val appId = obj.optLong("appid", 0L)
            if (appId <= 0L) continue
            val rawName = obj.optString("name", "").trim()
            val name = decodeHtmlEntities(rawName).ifBlank {
                knownTitles[appId] ?: "Steam App #$appId"
            }
            val playtimeForeverMinutes = obj.optInt("playtime_forever", -1)
            val playtimeHours = if (playtimeForeverMinutes >= 0) {
                playtimeForeverMinutes / 60
            } else {
                val hoursStr = obj.optString("hours_forever", "").replace(",", "").trim()
                hoursStr.toDoubleOrNull()?.toInt() ?: 0
            }
            val isPlayed = if (playtimeForeverMinutes >= 0) {
                playtimeForeverMinutes > 30
            } else {
                playtimeHours > 0
            }

            list.add(
                LocalGame(
                    id = "steam_$appId",
                    title = name,
                    coverImageUrl = AppConfig.getSteamHeaderImageUrl(appId),
                    isPlayed = isPlayed,
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
        return list
    }

    private fun unescapeHtmlAttributeJson(rawAttr: String): String {
        return rawAttr
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private fun extractGamesListJsonFromHtml(html: String): String? {
        val markerDouble = "data-profile-gameslist=\""
        val idxDouble = html.indexOf(markerDouble)
        if (idxDouble >= 0) {
            val start = idxDouble + markerDouble.length
            val end = html.indexOf('"', start)
            if (end > start) {
                return unescapeHtmlAttributeJson(html.substring(start, end))
            }
        }

        val markerSingle = "data-profile-gameslist='"
        val idxSingle = html.indexOf(markerSingle)
        if (idxSingle >= 0) {
            val start = idxSingle + markerSingle.length
            val end = html.indexOf('\'', start)
            if (end > start) {
                return unescapeHtmlAttributeJson(html.substring(start, end))
            }
        }

        if (html.contains("var rgGames =")) {
            val startIdx = html.indexOf("var rgGames =")
            val arrStart = html.indexOf('[', startIdx)
            if (arrStart >= 0) {
                val arrStr = extractBalancedJsonArray(html, arrStart)
                if (!arrStr.isNullOrBlank()) {
                    return """{"rgGames":$arrStr}"""
                }
            }
        }
        return null
    }

    private fun extractBalancedJsonArray(text: String, openBracketIndex: Int): String? {
        if (openBracketIndex < 0 || openBracketIndex >= text.length || text[openBracketIndex] != '[') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in openBracketIndex until text.length) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '[') depth++
                else if (c == ']') {
                    depth--
                    if (depth == 0) {
                        return text.substring(openBracketIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun extractRgGamesViaRegex(
        rawJson: String,
        knownTitles: Map<Long, String>
    ): List<LocalGame> {
        val result = LinkedHashMap<Long, LocalGame>()
        val itemRegex = Regex(
            """\"appid\"\s*:\s*(\d+)[^}]*?\"name\"\s*:\s*\"((?:\\.|[^\"\\])*)\""""
        )
        for (match in itemRegex.findAll(rawJson)) {
            val appId = match.groupValues[1].toLongOrNull() ?: continue
            if (appId <= 0L) continue
            val rawName = match.groupValues[2]
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\")
            val cleanName = decodeUnicodeEscapes(rawName).trim().ifBlank {
                knownTitles[appId] ?: "Steam App #$appId"
            }
            result[appId] = LocalGame(
                id = "steam_$appId",
                title = cleanName,
                coverImageUrl = AppConfig.getSteamHeaderImageUrl(appId),
                isPlayed = false,
                isOwned = true,
                is100Percent = false,
                personalRating = 0,
                personalNote = "Steam Bibliothek Import",
                isRecommended = false,
                genres = "Steam Library",
                playtimeHours = 0
            )
        }
        return result.values.toList()
    }

    private fun decodeUnicodeEscapes(input: String): String {
        if (!input.contains("\\u")) return input
        return Regex("""\\u([0-9a-fA-F]{4})""").replace(input) { matchResult ->
            val code = matchResult.groupValues[1].toInt(16)
            code.toChar().toString()
        }
    }

    private fun parseSteamCommunityXmlGamesList(
        steamId64: String,
        includeProfileSummaryFallback: Boolean = false
    ): List<LocalGame> {
        val urlsToTry = buildList {
            add("https://steamcommunity.com/profiles/$steamId64/games/?tab=all&xml=1")
            if (includeProfileSummaryFallback) {
                add("https://steamcommunity.com/profiles/$steamId64/?xml=1")
            }
        }
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
     * Importiert die KOMPLETTE öffentliche oder eingeloggte Steam-Wunschliste (`isOwned = false`)
     * inklusive aktueller Live-Preise aus dem Steam Store!
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
            val wishlistItems = try {
                val envelope = steamService.getWishlist(
                    steamId = steamId64,
                    apiKey = steamKey
                )
                envelope.response?.items.orEmpty()
                    .filter { it.appId > 0L }
                    .sortedBy { it.priority ?: Int.MAX_VALUE }
            } catch (_: Exception) {
                emptyList()
            }

            val bulkTitleMap = fetchBulkWishlistTitles(steamId64)
            val dynamicWishlistIds = if (hasActiveSteamWebSession()) {
                fetchDynamicStoreWishlistIds()
            } else emptyList()

            val appIdsOrdered = buildList {
                wishlistItems.forEach { add(it.appId) }
                bulkTitleMap.keys.forEach { if (!contains(it)) add(it) }
                dynamicWishlistIds.forEach { if (!contains(it)) add(it) }
            }

            if (appIdsOrdered.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // Bulk-Preise für die Wunschlisten-Spiele laden
            val bulkPrices = fetchBulkSteamPrices(appIdsOrdered)

            val resolvedWishlist = coroutineScope {
                appIdsOrdered.chunked(12).flatMap { chunk ->
                    chunk.map { appId ->
                        async {
                            val title = knownTitles[appId]?.takeIf { !it.startsWith("Steam App #") }
                                ?: bulkTitleMap[appId]?.takeIf { it.isNotBlank() }
                                ?: resolveSteamAppTitle(appId)
                                ?: "Steam App #$appId"
                            val priceInfo = bulkPrices[appId]
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
                                genres = "Steam Wishlist",
                                currentPriceCents = priceInfo?.currentPriceCents ?: -1,
                                originalPriceCents = priceInfo?.originalPriceCents ?: -1,
                                discountPercent = priceInfo?.discountPercent ?: 0,
                                formattedPrice = priceInfo?.formattedCurrentPrice.orEmpty()
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

    private fun fetchDynamicStoreWishlistIds(): List<Long> {
        val list = mutableListOf<Long>()
        try {
            val body = httpGetString("https://store.steampowered.com/dynamicstore/userdata/?t=${System.currentTimeMillis()}")
                ?: return emptyList()
            if (!body.trim().startsWith("{")) return emptyList()
            val json = JSONObject(body)
            val rgWishlist = json.optJSONArray("rgWishlist") ?: return emptyList()
            for (i in 0 until rgWishlist.length()) {
                val appId = rgWishlist.optLong(i, 0L)
                if (appId > 0L) list.add(appId)
            }
        } catch (_: Exception) {
        }
        return list
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

    // =========================================================================
    // STEAMDB & LIVE PREIS-TRACKER / PREISALARM
    // =========================================================================

    /**
     * Lädt Live-Preise für mehrere Steam-AppIDs in einem einzigen gebündelten HTTP-Aufruf (`filters=price_overview`).
     */
    fun fetchBulkSteamPrices(appIds: List<Long>): Map<Long, SteamPriceOverviewInfo> {
        if (appIds.isEmpty()) return emptyMap()
        val result = LinkedHashMap<Long, SteamPriceOverviewInfo>()
        for (chunk in appIds.distinct().chunked(25)) {
            try {
                val joined = chunk.joinToString(",")
                val url = "${AppConfig.STEAM_STORE_BASE_URL}appdetails?appids=$joined&cc=de&l=german&filters=price_overview"
                val body = httpGetString(url) ?: continue
                val root = JSONObject(body)
                for (appId in chunk) {
                    val appObj = root.optJSONObject(appId.toString()) ?: continue
                    if (!appObj.optBoolean("success", false)) continue
                    val dataObj = appObj.optJSONObject("data") ?: continue
                    val priceObj = dataObj.optJSONObject("price_overview")
                    if (priceObj != null) {
                        val finalCents = priceObj.optInt("final", -1)
                        val initialCents = priceObj.optInt("initial", finalCents)
                        val discount = priceObj.optInt("discount_percent", 0)
                        val formattedFinal = priceObj.optString("final_formatted", "").ifBlank {
                            if (finalCents >= 0) String.format(Locale.GERMANY, "%.2f €", finalCents / 100.0) else ""
                        }
                        val formattedInitial = priceObj.optString("initial_formatted", "").ifBlank {
                            if (initialCents > 0) String.format(Locale.GERMANY, "%.2f €", initialCents / 100.0) else ""
                        }
                        if (finalCents >= 0) {
                            result[appId] = SteamPriceOverviewInfo(
                                steamAppId = appId,
                                currentPriceCents = finalCents,
                                originalPriceCents = initialCents,
                                discountPercent = discount,
                                formattedCurrentPrice = formattedFinal,
                                formattedOriginalPrice = formattedInitial
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    /**
     * Lädt den detaillierten Live-Preis + Rabatt + historischen Bestpreis (SteamDB / CheapShark) für ein einzelnes Spiel.
     */
    suspend fun fetchGamePriceOverview(game: LocalGame): Result<SteamPriceOverviewInfo> = withContext(Dispatchers.IO) {
        val appId = resolveSteamAppIdForGame(game)
            ?: return@withContext Result.failure(
                IllegalStateException("Für '${game.title}' konnte keine Steam-AppID für die Preisabfrage ermittelt werden.")
            )

        try {
            val body = httpGetString("${AppConfig.STEAM_STORE_BASE_URL}appdetails?appids=$appId&cc=de&l=german")
                ?: return@withContext Result.failure(IllegalStateException("Steam Store Preisabfrage nicht erreichbar."))
            val root = JSONObject(body)
            val appObj = root.optJSONObject(appId.toString())
            if (appObj == null || !appObj.optBoolean("success", false)) {
                return@withContext Result.failure(IllegalStateException("Keine Preisdaten für AppID $appId verfügbar."))
            }
            val dataObj = appObj.optJSONObject("data")
                ?: return@withContext Result.failure(IllegalStateException("Leere Store-Antwort für AppID $appId."))

            val isFree = dataObj.optBoolean("is_free", false)
            val priceObj = dataObj.optJSONObject("price_overview")

            // Historischer Bestpreis (All-Time Low) abrufen
            val historicalLowText = fetchHistoricalLowPriceText(appId)

            if (isFree && priceObj == null) {
                return@withContext Result.success(
                    SteamPriceOverviewInfo(
                        steamAppId = appId,
                        currentPriceCents = 0,
                        originalPriceCents = 0,
                        discountPercent = 0,
                        formattedCurrentPrice = "Kostenlos (Free-to-Play)",
                        formattedOriginalPrice = "0,00 €",
                        lowestHistoricalPrice = historicalLowText.ifBlank { "Kostenlos" }
                    )
                )
            }

            if (priceObj != null) {
                val finalCents = priceObj.optInt("final", 0)
                val initialCents = priceObj.optInt("initial", finalCents)
                val discount = priceObj.optInt("discount_percent", 0)
                val formattedFinal = priceObj.optString("final_formatted", "").ifBlank {
                    String.format(Locale.GERMANY, "%.2f €", finalCents / 100.0)
                }
                val formattedInitial = priceObj.optString("initial_formatted", "").ifBlank {
                    String.format(Locale.GERMANY, "%.2f €", initialCents / 100.0)
                }
                return@withContext Result.success(
                    SteamPriceOverviewInfo(
                        steamAppId = appId,
                        currentPriceCents = finalCents,
                        originalPriceCents = initialCents,
                        discountPercent = discount,
                        formattedCurrentPrice = formattedFinal,
                        formattedOriginalPrice = formattedInitial,
                        lowestHistoricalPrice = historicalLowText
                    )
                )
            }

            Result.failure(IllegalStateException("Für '${game.title}' ist aktuell kein Kaufpreis auf Steam hinterlegt."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchHistoricalLowPriceText(steamAppId: Long): String {
        return try {
            val body = httpGetString("https://www.cheapshark.com/api/1.0/games?steamAppID=$steamAppId")
                ?: return ""
            if (!body.trim().startsWith("[")) return ""
            val arr = JSONArray(body)
            if (arr.length() == 0) return ""
            val first = arr.optJSONObject(0) ?: return ""
            val cheapest = first.optString("cheapest", "").toDoubleOrNull()
            if (cheapest != null && cheapest >= 0.0) {
                String.format(Locale.GERMANY, "%.2f €", cheapest)
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    // =========================================================================
    // STEAM INVENTAR VIEWER & COMMUNITY MARKET PREISE
    // =========================================================================

    /**
     * Lädt das Steam-Inventar eines Nutzers für das gewählte Spiel (`appId` / `contextId`).
     * Unterstützt Steam Community Items (Sammelkarten, Hintergründe, Emoticons: 753/6),
     * Counter-Strike 2 (730/2), Dota 2 (570/2), TF2 (440/2), Rust (252490/2) und beliebige eigene AppIDs.
     */
    suspend fun fetchSteamInventory(
        rawSteamIdInput: String,
        appId: Long,
        contextId: Long
    ): Result<List<SteamInventoryItem>> = withContext(Dispatchers.IO) {
        val steamId64 = normalizeSteamId64(rawSteamIdInput)
        if (!steamId64.matches(Regex("^\\d{17}$"))) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    "Bitte verknüpfe zuerst deine 17-stellige SteamID64 oder logge dich mit Steam ein, um dein Inventar zu laden."
                )
            )
        }

        val url = "https://steamcommunity.com/inventory/$steamId64/$appId/$contextId?l=german&count=250"
        return@withContext try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank() || body == "null") {
                    return@use Result.failure(
                        IllegalStateException(
                            "Inventar für AppID $appId konnte nicht geladen werden (HTTP ${response.code}). " +
                                "Stelle sicher, dass dein 'Inventar' in den Steam-Datenschutzeinstellungen auf 'Öffentlich' steht oder nutze den Steam Web-Login."
                        )
                    )
                }

                val root = JSONObject(body)
                if (root.optInt("success", 0) != 1 && !root.has("assets")) {
                    val totalCount = root.optInt("total_inventory_count", 0)
                    if (totalCount == 0) {
                        return@use Result.success(emptyList())
                    }
                }

                val assetsArr = root.optJSONArray("assets") ?: JSONArray()
                val descriptionsArr = root.optJSONArray("descriptions") ?: JSONArray()

                if (assetsArr.length() == 0) {
                    return@use Result.success(emptyList())
                }

                // Mappe Beschreibungen anhand von (classid, instanceid)
                val descMap = HashMap<String, JSONObject>()
                for (i in 0 until descriptionsArr.length()) {
                    val descObj = descriptionsArr.optJSONObject(i) ?: continue
                    val cId = descObj.optString("classid", "")
                    val iId = descObj.optString("instanceid", "0")
                    if (cId.isNotBlank()) {
                        descMap["${cId}_$iId"] = descObj
                        if (!descMap.containsKey(cId)) {
                            descMap[cId] = descObj
                        }
                    }
                }

                // Gruppiere identische Items (z.B. mehrere gleiche Kisten/Sammelkarten) und addiere ihre Anzahl (`amount`)
                val groupedItems = LinkedHashMap<String, SteamInventoryItem>()

                for (i in 0 until assetsArr.length()) {
                    val assetObj = assetsArr.optJSONObject(i) ?: continue
                    val assetId = assetObj.optString("assetid", "asset_$i")
                    val classId = assetObj.optString("classid", "")
                    val instanceId = assetObj.optString("instanceid", "0")
                    val amount = assetObj.optString("amount", "1").toIntOrNull() ?: 1

                    val descObj = descMap["${classId}_$instanceId"] ?: descMap[classId] ?: continue
                    val rawName = descObj.optString("name", "").ifBlank {
                        descObj.optString("market_name", "Steam Item #$classId")
                    }
                    val marketHashName = descObj.optString("market_hash_name", rawName)
                    val typeDesc = descObj.optString("type", "Steam Inventar-Gegenstand")
                    val iconPath = descObj.optString("icon_url_large", "").ifBlank {
                        descObj.optString("icon_url", "")
                    }
                    val fullIconUrl = if (iconPath.isNotBlank()) {
                        "https://community.akamai.steamstatic.com/economy/image/$iconPath/256fx256f"
                    } else ""

                    val tradable = descObj.optInt("tradable", 0) == 1
                    val marketable = descObj.optInt("marketable", 0) == 1

                    var rarityColor = descObj.optString("name_color", "").trim()
                    val tagsArr = descObj.optJSONArray("tags")
                    if (tagsArr != null) {
                        for (t in 0 until tagsArr.length()) {
                            val tagObj = tagsArr.optJSONObject(t) ?: continue
                            val tagColor = tagObj.optString("color", "").trim()
                            if (tagColor.isNotBlank()) {
                                rarityColor = tagColor
                                break
                            }
                        }
                    }
                    if (rarityColor.isNotBlank() && !rarityColor.startsWith("#")) {
                        rarityColor = "#$rarityColor"
                    }

                    val groupKey = "${classId}_$instanceId"
                    val existing = groupedItems[groupKey]
                    if (existing == null) {
                        groupedItems[groupKey] = SteamInventoryItem(
                            assetId = assetId,
                            classId = classId,
                            appId = appId,
                            contextId = contextId,
                            name = decodeHtmlEntities(rawName),
                            marketHashName = marketHashName,
                            typeDescription = decodeHtmlEntities(typeDesc),
                            iconUrl = fullIconUrl,
                            amount = amount,
                            rarityColorHex = rarityColor,
                            isTradable = tradable,
                            isMarketable = marketable
                        )
                    } else {
                        groupedItems[groupKey] = existing.copy(
                            amount = existing.amount + amount
                        )
                    }
                }

                Result.success(groupedItems.values.toList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ruft den aktuellen Euro-Marktpreis eines handelbaren Steam-Inventar-Items aus dem Steam Community Market ab (`currency=3` = EUR).
     */
    suspend fun fetchSteamMarketItemPrice(
        appId: Long,
        marketHashName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (marketHashName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Kein Market-Hash-Name vorhanden."))
        }
        try {
            val encodedName = URLEncoder.encode(marketHashName, "UTF-8")
            val url = "https://steamcommunity.com/market/priceoverview/?appid=$appId&currency=3&market_hash_name=$encodedName"
            val body = httpGetString(url)
                ?: return@withContext Result.failure(IllegalStateException("Marktpreis aktuell nicht abrufbar."))
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) {
                return@withContext Result.failure(IllegalStateException("Kein aktuelles Marktangebot gefunden."))
            }
            val lowest = json.optString("lowest_price", "")
            val median = json.optString("median_price", "")
            val volume = json.optString("volume", "")
            val label = buildString {
                if (lowest.isNotBlank()) append("Ab $lowest")
                else if (median.isNotBlank()) append("Ø $median")
                else append("Unbekannt")
                if (volume.isNotBlank()) append(" ($volume verkauft/24h)")
            }
            Result.success(label)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // STEAM ERRUNGENSCHAFTEN (ACHIEVEMENTS) MIT SPOILER-SCHUTZ
    // =========================================================================

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

    suspend fun resolveSteamAppIdForGame(game: LocalGame): Long? {
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
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
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
