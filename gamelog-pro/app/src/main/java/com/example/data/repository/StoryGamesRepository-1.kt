package com.example.data.repository

import com.example.config.AppConfig
import com.example.data.cloud.AdminApiSecrets
import com.example.data.cloud.CloudGameRepository
import com.example.data.cloud.GlobalGameStats
import com.example.data.local.GameDao
import com.example.data.local.LocalGame
import com.example.data.remote.ExternalGameSearchResult
import com.example.data.remote.ExternalGamesRepository
import com.example.data.remote.SteamAchievement
import com.example.data.remote.SteamAchievementSerializer
import com.example.data.remote.SteamInventoryItem
import com.example.data.remote.SteamLibraryImportReport
import com.example.data.remote.SteamPriceOverviewInfo
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow

data class LiveWishlistSyncSummary(
    val totalCount: Int,
    val newlyAddedTitles: List<String>,
    val triggeredAlertTitles: List<String> = emptyList()
)

/**
 * Zentrales Hybrid-Repository (Room lokal + Firebase Cloud-Sync + IGDB/Steam/SteamDB Retrofit + Cloud Admin Vault).
 */
class StoryGamesRepository(
    private val gameDao: GameDao,
    private val cloudRepository: CloudGameRepository,
    private val externalRepository: ExternalGamesRepository
) {
    val ownedLibraryGames: Flow<List<LocalGame>> = gameDao.observeOwnedLibraryGames()
    val wishlistGames: Flow<List<LocalGame>> = gameDao.observeWishlistGames()
    val allLocalGames: Flow<List<LocalGame>> = gameDao.observeAllGames()
    val communityRankings: Flow<List<GlobalGameStats>> = cloudRepository.observeCommunityRankings()

    fun observeGameById(gameId: String): Flow<LocalGame?> = gameDao.observeGameById(gameId)

    /**
     * Prüft, ob aktuell ein verifiziertes Google-Konto angemeldet ist.
     */
    fun isGoogleSignedIn(): Boolean = Firebase.auth.currentUser != null

    /**
     * Prüft strikt, ob das aktuell angemeldete Google-Konto der verifizierte Admin (`nico.schaefer.hn@gmail.com`) ist.
     */
    fun isCurrentUserAdmin(): Boolean = cloudRepository.isCurrentUserAdmin()

    /**
     * Prüft, ob eine aktive Steam-WebView-Session (`steamLoginSecure`) vorhanden ist,
     * mit der auch geschützte/Freunde-Bibliotheken (250+ Spiele) geladen werden können.
     */
    fun hasActiveSteamWebSession(): Boolean = externalRepository.hasActiveSteamWebSession()

    suspend fun getOwnedLibraryGamesCount(): Int {
        return gameDao.getAllGamesSnapshot().count { it.isOwned }
    }

    /**
     * Lädt die API-Keys aus dem geschützten Cloud-Tresor (`/admin_vault/api_secrets`),
     * sofern der aktuell angemeldete Nutzer der verifizierte Admin (`nico.schaefer.hn@gmail.com`) ist.
     */
    suspend fun loadAdminVaultSecrets(): Result<AdminApiSecrets?> {
        return cloudRepository.fetchAdminVaultSecrets()
    }

    /**
     * Speichert die API-Keys im geschützten Cloud-Tresor (`/admin_vault/api_secrets`).
     */
    suspend fun saveAdminVaultSecrets(
        twitchClientId: String,
        twitchClientSecret: String,
        steamApiKey: String
    ): Result<AdminApiSecrets> {
        return cloudRepository.saveAdminVaultSecrets(
            twitchClientId = twitchClientId,
            twitchClientSecret = twitchClientSecret,
            steamApiKey = steamApiKey
        )
    }

    /**
     * Exklusive Admin-Funktion (`nico.schaefer.hn@gmail.com`):
     * Löscht ein Spiel aus dem globalen Community-Ranking (`/global_game_stats/{gameId}`).
     */
    suspend fun deleteGameFromCommunityRankingAsAdmin(gameId: String): Result<Unit> {
        return cloudRepository.deleteGameFromCommunityRanking(gameId)
    }

    /**
     * Synchronisiert bei App-Start vorhandene Cloud-Einträge des Nutzers in die lokale Room-Datenbank.
     */
    suspend fun restoreUserCloudBackupIfNeeded() {
        if (!isGoogleSignedIn()) return
        val cloudResult = cloudRepository.fetchUserContributionsOnce()
        val contributions = cloudResult.getOrNull() ?: return
        val existingIds = gameDao.getAllGamesSnapshot().map { it.id }.toSet()

        val missingGames = contributions.filter { it.gameId.isNotBlank() && it.gameId !in existingIds }
            .map { contrib ->
                LocalGame(
                    id = contrib.gameId,
                    title = contrib.title,
                    coverImageUrl = contrib.coverImageUrl,
                    isPlayed = contrib.isPlayed,
                    isOwned = contrib.isOwned,
                    is100Percent = contrib.is100Percent,
                    personalRating = contrib.personalRating.toInt().coerceIn(0, 5),
                    personalNote = contrib.personalNote,
                    isRecommended = contrib.isRecommended
                )
            }

        if (missingGames.isNotEmpty()) {
            gameDao.upsertGames(missingGames)
        }
    }

    /**
     * Speichert ein Spiel lokal in Room (ohne es ins Community-Ranking einzutragen).
     */
    suspend fun saveGameLocally(game: LocalGame): LocalGame {
        val updatedGame = game.copy(updatedAtEpoch = System.currentTimeMillis())
        gameDao.upsertGame(updatedGame)
        return updatedGame
    }

    /**
     * Speichert oder aktualisiert ein Spiel lokal in Room und synchronisiert es mit der Cloud.
     * WICHTIG: In `GlobalGameStats` (Community-Ranking) wird das Spiel NUR eingetragen/aktualisiert,
     * wenn eine Bewertung (`personalRating > 0`) oder eine Weiterempfehlung (`isRecommended == true`) vorliegt!
     */
    suspend fun saveGameAndSyncToCloud(game: LocalGame): Result<GlobalGameStats> {
        val updatedGame = game.copy(updatedAtEpoch = System.currentTimeMillis())
        gameDao.upsertGame(updatedGame)
        return cloudRepository.syncGameToCloud(updatedGame)
    }

    /**
     * Fügt ein Suchergebnis aus IGDB / Steam zur eigenen Bibliothek (`isOwned = true`)
     * oder zur Wunschliste (`isOwned = false`) hinzu.
     * WICHTIG: Bloßes Hinzufügen zur Bibliothek oder Wunschliste fügt das Spiel NICHT zum Community-Ranking hinzu!
     */
    suspend fun addSearchResultToCollection(
        result: ExternalGameSearchResult,
        toOwnedLibrary: Boolean
    ): LocalGame {
        val existing = gameDao.getGameById(result.apiId)
        val baseGame = if (existing != null) {
            existing.copy(
                isOwned = toOwnedLibrary,
                updatedAtEpoch = System.currentTimeMillis()
            )
        } else {
            LocalGame(
                id = result.apiId,
                title = result.title,
                coverImageUrl = result.coverImageUrl,
                isPlayed = false,
                isOwned = toOwnedLibrary,
                is100Percent = false,
                personalRating = 0,
                personalNote = "",
                isRecommended = false,
                releaseYear = result.releaseYear,
                genres = result.genres
            )
        }

        val appId = baseGame.id.removePrefix("steam_").toLongOrNull()
        val withPrice = if (appId != null && baseGame.currentPriceCents < 0) {
            val priceMap = externalRepository.fetchBulkSteamPrices(listOf(appId))
            val info = priceMap[appId]
            if (info != null) {
                applyPriceInfoToGame(baseGame, info)
            } else baseGame
        } else baseGame

        gameDao.upsertGame(withPrice)
        return withPrice
    }

    /**
     * Verschiebt ein Spiel von der Wunschliste in "Meine Bibliothek" (`isOwned = true`).
     */
    suspend fun moveWishlistGameToLibrary(game: LocalGame) {
        val updated = game.copy(
            isOwned = true,
            updatedAtEpoch = System.currentTimeMillis()
        )
        gameDao.upsertGame(updated)
    }

    suspend fun deleteLocalGame(gameId: String) {
        gameDao.deleteGameById(gameId)
    }

    suspend fun searchExternalGames(query: String): Result<List<ExternalGameSearchResult>> {
        if (isCurrentUserAdmin() && AppConfig.TWITCH_CLIENT_ID.isBlank()) {
            loadAdminVaultSecrets()
        }
        return externalRepository.searchGames(query)
    }

    private suspend fun buildKnownSteamTitlesMap(): Map<Long, String> {
        return gameDao.getAllGamesSnapshot()
            .mapNotNull { game ->
                val appId = game.id.removePrefix("steam_").toLongOrNull() ?: return@mapNotNull null
                if (game.title.isNotBlank() && !game.title.startsWith("Steam App #")) {
                    appId to game.title
                } else null
            }
            .toMap()
    }

    private fun applyPriceInfoToGame(
        game: LocalGame,
        info: SteamPriceOverviewInfo,
        overrideTargetAlertEur: Double? = null
    ): LocalGame {
        val targetAlert = overrideTargetAlertEur ?: game.targetPriceAlertEur
        val currentPriceEur = if (info.currentPriceCents >= 0) info.currentPriceCents / 100.0 else -1.0
        val alertTriggered = targetAlert > 0.0 && currentPriceEur >= 0.0 && currentPriceEur <= targetAlert + 0.001
        return game.copy(
            currentPriceCents = info.currentPriceCents,
            originalPriceCents = info.originalPriceCents,
            discountPercent = info.discountPercent,
            formattedPrice = info.formattedPrice.ifBlank { game.formattedPrice },
            lowestHistoricalPrice = info.lowestHistoricalPrice.ifBlank { game.lowestHistoricalPrice },
            targetPriceAlertEur = targetAlert,
            isPriceAlertTriggered = alertTriggered,
            updatedAtEpoch = System.currentTimeMillis()
        )
    }

    /**
     * Importiert ALLE Spiele aus der Steam-Spielebibliothek (Owned Games) und speichert sie in Room.
     * Gibt einen detaillierten `SteamLibraryImportReport` zurück (inkl. Hinweis, falls ohne Steam-Web-Session
     * nur die ~6 Showcase-Spiele sichtbar waren).
     */
    suspend fun importSteamLibraryWithReport(steamIdInput: String): Result<SteamLibraryImportReport> {
        if (isCurrentUserAdmin() && AppConfig.STEAM_API_KEY.isBlank()) {
            loadAdminVaultSecrets()
        }
        val knownTitles = buildKnownSteamTitlesMap()
        val result = externalRepository.importSteamOwnedGamesWithReport(steamIdInput, knownTitles)
        return result.map { report ->
            val existingMap = gameDao.getAllGamesSnapshot().associateBy { it.id }
            val merged = report.games.map { imported ->
                val prev = existingMap[imported.id]
                if (prev != null) {
                    val mergedPriceCents = if (imported.currentPriceCents >= 0) imported.currentPriceCents else prev.currentPriceCents
                    val mergedOriginalCents = if (imported.originalPriceCents >= 0) imported.originalPriceCents else prev.originalPriceCents
                    val mergedDiscount = if (imported.currentPriceCents >= 0) imported.discountPercent else prev.discountPercent
                    val mergedFormatted = imported.formattedPrice.ifBlank { prev.formattedPrice }
                    val targetAlert = prev.targetPriceAlertEur
                    val triggered = targetAlert > 0.0 && mergedPriceCents >= 0 && (mergedPriceCents / 100.0) <= targetAlert + 0.001

                    prev.copy(
                        title = if (imported.title.startsWith("Steam App #") && !prev.title.startsWith("Steam App #")) {
                            prev.title
                        } else {
                            imported.title
                        },
                        isOwned = true,
                        playtimeHours = maxOf(prev.playtimeHours, imported.playtimeHours),
                        isPlayed = prev.isPlayed || imported.isPlayed,
                        currentPriceCents = mergedPriceCents,
                        originalPriceCents = mergedOriginalCents,
                        discountPercent = mergedDiscount,
                        formattedPrice = mergedFormatted,
                        lowestHistoricalPrice = imported.lowestHistoricalPrice.ifBlank { prev.lowestHistoricalPrice },
                        targetPriceAlertEur = targetAlert,
                        isPriceAlertTriggered = triggered,
                        updatedAtEpoch = System.currentTimeMillis()
                    )
                } else {
                    imported
                }
            }
            gameDao.upsertGames(merged)
            report.copy(games = merged)
        }
    }

    suspend fun importSteamLibrary(steamIdInput: String): Result<Int> {
        return importSteamLibraryWithReport(steamIdInput).map { it.games.size }
    }

    /**
     * Importiert ALLE Spiele aus der Steam-Wunschliste (`isOwned = false`), aktualisiert deren Live-Preise
     * und prüft automatisch alle aktiven Preisalarme (`targetPriceAlertEur`).
     */
    suspend fun importSteamWishlistWithDiff(steamIdInput: String): Result<LiveWishlistSyncSummary> {
        if (isCurrentUserAdmin() && AppConfig.STEAM_API_KEY.isBlank()) {
            loadAdminVaultSecrets()
        }
        val existingSnapshot = gameDao.getAllGamesSnapshot()
        val existingMap = existingSnapshot.associateBy { it.id }
        val knownTitles = buildKnownSteamTitlesMap()

        val result = externalRepository.importSteamWishlist(steamIdInput, knownTitles)
        return result.map { importedList ->
            val newlyAddedTitles = mutableListOf<String>()
            val triggeredAlertTitles = mutableListOf<String>()

            // Falls einige Wunschlisten-Spiele noch keinen Preis haben, laden wir sie gebündelt nach
            val missingPriceAppIds = importedList.mapNotNull { game ->
                val appId = game.id.removePrefix("steam_").toLongOrNull() ?: return@mapNotNull null
                if (game.currentPriceCents < 0) appId else null
            }
            val bulkPrices = if (missingPriceAppIds.isNotEmpty()) {
                externalRepository.fetchBulkSteamPrices(missingPriceAppIds)
            } else emptyMap()

            val newWishlistGames = importedList.mapNotNull { rawImported ->
                val appId = rawImported.id.removePrefix("steam_").toLongOrNull()
                val bulkInfo = if (appId != null) bulkPrices[appId] else null
                val imported = if (bulkInfo != null && rawImported.currentPriceCents < 0) {
                    applyPriceInfoToGame(rawImported, bulkInfo)
                } else rawImported

                val prev = existingMap[imported.id]
                when {
                    prev == null -> {
                        newlyAddedTitles.add(imported.title)
                        imported
                    }
                    !prev.isOwned -> {
                        val mergedPriceCents = if (imported.currentPriceCents >= 0) imported.currentPriceCents else prev.currentPriceCents
                        val mergedOriginalCents = if (imported.originalPriceCents >= 0) imported.originalPriceCents else prev.originalPriceCents
                        val mergedDiscount = if (imported.currentPriceCents >= 0) imported.discountPercent else prev.discountPercent
                        val mergedFormatted = imported.formattedPrice.ifBlank { prev.formattedPrice }
                        val targetAlert = prev.targetPriceAlertEur
                        val isTriggeredNow = targetAlert > 0.0 && mergedPriceCents >= 0 &&
                            (mergedPriceCents / 100.0) <= targetAlert + 0.001

                        if (isTriggeredNow && !prev.isPriceAlertTriggered) {
                            triggeredAlertTitles.add(
                                "${imported.title} (${mergedFormatted.ifBlank { String.format(java.util.Locale.GERMANY, "%.2f€", mergedPriceCents / 100.0) }})"
                            )
                        }

                        prev.copy(
                            title = if (!imported.title.startsWith("Steam App #")) imported.title else prev.title,
                            coverImageUrl = imported.coverImageUrl.ifBlank { prev.coverImageUrl },
                            currentPriceCents = mergedPriceCents,
                            originalPriceCents = mergedOriginalCents,
                            discountPercent = mergedDiscount,
                            formattedPrice = mergedFormatted,
                            lowestHistoricalPrice = imported.lowestHistoricalPrice.ifBlank { prev.lowestHistoricalPrice },
                            targetPriceAlertEur = targetAlert,
                            isPriceAlertTriggered = isTriggeredNow,
                            updatedAtEpoch = System.currentTimeMillis()
                        )
                    }
                    else -> null
                }
            }
            if (newWishlistGames.isNotEmpty()) {
                gameDao.upsertGames(newWishlistGames)
            }
            LiveWishlistSyncSummary(
                totalCount = newWishlistGames.size,
                newlyAddedTitles = newlyAddedTitles,
                triggeredAlertTitles = triggeredAlertTitles
            )
        }
    }

    suspend fun importSteamWishlist(steamIdInput: String): Result<Int> {
        return importSteamWishlistWithDiff(steamIdInput).map { it.totalCount }
    }

    /**
     * Lädt Live-Preisdaten (Steam Store + historischer Tiefstpreis / SteamDB-Link) für ein einzelnes Spiel
     * und aktualisiert optional den Preisalarm (`newTargetAlertEur`).
     */
    suspend fun refreshGamePriceAndAlert(
        game: LocalGame,
        newTargetAlertEur: Double? = null
    ): Result<Pair<LocalGame, SteamPriceOverviewInfo>> {
        val priceResult = externalRepository.fetchGamePriceOverview(game)
        return priceResult.map { info ->
            val latest = gameDao.getGameById(game.id) ?: game
            val updated = applyPriceInfoToGame(latest, info, overrideTargetAlertEur = newTargetAlertEur)
            gameDao.upsertGame(updated)
            updated to info
        }
    }

    /**
     * Speichert oder deaktiviert (`0.0`) den Preisalarm für ein Spiel sofort lokal und prüft den aktuellen Preis.
     */
    suspend fun updatePriceAlertTarget(game: LocalGame, targetAlertEur: Double): LocalGame {
        val sanitizedTarget = targetAlertEur.coerceAtLeast(0.0)
        val latest = gameDao.getGameById(game.id) ?: game
        val currentEur = if (latest.currentPriceCents >= 0) latest.currentPriceCents / 100.0 else -1.0
        val isTriggered = sanitizedTarget > 0.0 && currentEur >= 0.0 && currentEur <= sanitizedTarget + 0.001
        val updated = latest.copy(
            targetPriceAlertEur = sanitizedTarget,
            isPriceAlertTriggered = isTriggered,
            updatedAtEpoch = System.currentTimeMillis()
        )
        gameDao.upsertGame(updated)
        return updated
    }

    /**
     * Aktualisiert gebündelt die aktuellen Steam-Preise für alle Spiele in der Sammlung
     * und gibt eine Liste aller Spiele zurück, deren Preisalarm erreicht wurde.
     */
    suspend fun refreshAllPricesAndCheckAlerts(): Result<List<String>> = runCatching {
        val allGames = gameDao.getAllGamesSnapshot()
        val steamGames = allGames.mapNotNull { game ->
            val appId = game.id.removePrefix("steam_").toLongOrNull() ?: return@mapNotNull null
            appId to game
        }
        if (steamGames.isEmpty()) return@runCatching emptyList()

        // Priorisiere Wunschlisten-Spiele und Spiele mit aktivem Preisalarm
        val prioritized = steamGames.sortedByDescending { (_, g) ->
            (if (g.targetPriceAlertEur > 0.0) 2 else 0) + (if (!g.isOwned) 1 else 0)
        }.take(100)

        val priceMap = externalRepository.fetchBulkSteamPrices(prioritized.map { it.first })
        val updatedGames = mutableListOf<LocalGame>()
        val triggeredDescriptions = mutableListOf<String>()

        for ((appId, game) in prioritized) {
            val info = priceMap[appId] ?: continue
            val updated = applyPriceInfoToGame(game, info)
            updatedGames.add(updated)
            if (updated.isPriceAlertTriggered) {
                triggeredDescriptions.add("${updated.title} (${updated.formattedPrice})")
            }
        }

        if (updatedGames.isNotEmpty()) {
            gameDao.upsertGames(updatedGames)
        }
        triggeredDescriptions
    }

    /**
     * Lädt das Steam-Inventar des Nutzers für das gewählte Spiel (`appId`/`contextId`).
     */
    suspend fun fetchSteamInventory(
        steamIdInput: String,
        appId: Long,
        contextId: Long
    ): Result<List<SteamInventoryItem>> {
        return externalRepository.fetchSteamInventory(
            rawSteamIdInput = steamIdInput,
            appId = appId,
            contextId = contextId
        )
    }

    /**
     * Lädt den aktuellen Steam Community Market Preis für ein Inventar-Item.
     */
    suspend fun fetchSteamMarketItemPrice(
        appId: Long,
        marketHashName: String
    ): Result<String> {
        return externalRepository.fetchSteamMarketItemPrice(appId, marketHashName)
    }

    /**
     * Lädt die Steam-Errungenschaften eines Spiels und speichert sie lokal in `LocalGame.achievementsJson`.
     */
    suspend fun fetchAndCacheGameAchievements(
        game: LocalGame,
        steamIdInput: String
    ): Result<List<SteamAchievement>> {
        if (isCurrentUserAdmin() && AppConfig.STEAM_API_KEY.isBlank()) {
            loadAdminVaultSecrets()
        }
        val result = externalRepository.fetchGameAchievements(game, steamIdInput)
        result.onSuccess { achievements ->
            if (achievements.isNotEmpty()) {
                val json = SteamAchievementSerializer.toJsonString(achievements)
                val allUnlocked = achievements.all { it.isUnlocked }
                val latestLocal = gameDao.getGameById(game.id) ?: game
                val updated = latestLocal.copy(
                    achievementsJson = json,
                    is100Percent = latestLocal.is100Percent || allUnlocked,
                    isPlayed = latestLocal.isPlayed || achievements.any { it.isUnlocked },
                    updatedAtEpoch = System.currentTimeMillis()
                )
                gameDao.upsertGame(updated)
            }
        }
        return result
    }
}
