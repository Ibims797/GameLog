package com.example.data.remote

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit-Service für die Twitch OAuth2 Authentifizierung (`AppConfig.TWITCH_AUTH_BASE_URL`).
 * Ruft den Client-Credentials Bearer Token für die IGDB API ab.
 */
interface TwitchAuthApiService {
    @POST("oauth2/token")
    suspend fun getAccessToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("grant_type") grantType: String = "client_credentials"
    ): TwitchTokenResponse
}

/**
 * Retrofit-Service für die IGDB API v4 (`AppConfig.IGDB_BASE_URL`).
 * Nutzt POST auf `games` mit Apicalypse Plain-Text-Syntax im `RequestBody`
 * sowie den Pflicht-Headern `Client-ID` und `Authorization: Bearer <token>`.
 */
interface IgdbApiService {
    @POST("games")
    suspend fun searchGames(
        @Header("Client-ID") clientId: String,
        @Header("Authorization") bearerToken: String,
        @Body apicalypseBody: RequestBody
    ): List<IgdbGameDto>
}

/**
 * Retrofit-Service für die Steam Web API (`AppConfig.STEAM_API_BASE_URL`).
 * Importiert die eigene Spielebibliothek (`GetOwnedGames`), die Wunschliste (`GetWishlist`)
 * sowie die persönlichen & globalen Spiel-Errungenschaften (`GetPlayerAchievements` & `GetSchemaForGame`).
 */
interface SteamApiService {
    @GET("IPlayerService/GetOwnedGames/v0001/")
    suspend fun getOwnedGames(
        @Query("key") apiKey: String,
        @Query("steamid") steamId: String,
        @Query("include_appinfo") includeAppInfo: Int = 1,
        @Query("include_played_free_games") includePlayedFreeGames: Int = 1,
        @Query("include_free_sub") includeFreeSub: Int = 1,
        @Query("skip_unvetted_apps") skipUnvettedApps: Boolean = false,
        @Query("format") format: String = "json"
    ): SteamOwnedGamesEnvelope

    @GET("IWishlistService/GetWishlist/v1/")
    suspend fun getWishlist(
        @Query("steamid") steamId: String,
        @Query("key") apiKey: String? = null
    ): SteamWishlistEnvelope

    @GET("ISteamUser/ResolveVanityURL/v0001/")
    suspend fun resolveVanityUrl(
        @Query("key") apiKey: String,
        @Query("vanityurl") vanityUrl: String
    ): SteamVanityEnvelope

    @GET("ISteamUserStats/GetPlayerAchievements/v0001/")
    suspend fun getPlayerAchievements(
        @Query("appid") appId: Long,
        @Query("key") apiKey: String,
        @Query("steamid") steamId: String,
        @Query("l") language: String = "german"
    ): SteamPlayerAchievementsEnvelope

    @GET("ISteamUserStats/GetSchemaForGame/v2/")
    suspend fun getSchemaForGame(
        @Query("key") apiKey: String,
        @Query("appid") appId: Long,
        @Query("l") language: String = "german"
    ): SteamGameSchemaEnvelope
}

/**
 * Retrofit-Service für die öffentliche Steam Store API (`AppConfig.STEAM_STORE_BASE_URL`).
 */
interface SteamStoreApiService {
    @GET("storesearch/")
    suspend fun searchStoreGames(
        @Query("term") query: String,
        @Query("l") language: String = "german",
        @Query("cc") countryCode: String = "DE"
    ): SteamStoreSearchResponse
}
