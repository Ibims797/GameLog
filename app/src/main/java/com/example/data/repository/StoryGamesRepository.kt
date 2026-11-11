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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class LiveWishlistSyncSummary(
    val totalCount: Int,
    val newlyAddedTitles: List<String>,
    val triggeredAlertTitles: List<String> = emptyList()
)

data class AddToWishlistResult(
    val localGame: LocalGame,
    val addedDirectlyToSteam: Boolean,
    val statusMessage: String
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

    /**
     * Fügt ein Spiel aus dem Community-Ranking zur Wunschliste hinzu
     * und synchronisiert es bei aktiver Steam-Sitzung direkt auf die offizielle Steam-Wunschliste.
     */
    suspend fun addRankingGameToWishlist(stats: GlobalGameStats): Result<AddToWishlistResult> {
        val existingSnapshot = gameDao.getAllGamesSnapshot()

        // 1. Prüfen, ob das Spiel bereits in der Bibliothek liegt
        val existingOwned = existingSnapshot.find {
            it.isOwned && (it.id == stats.id || normalizeGameTitle(it.title) == normalizeGameTitle(stats.title))
        }
        if (existingOwned != null) {
            return Result.failure(
                IllegalStateException("'${stats.title}' befindet sich bereits in deiner Spielebibliothek!")
            )
        }

        // 2. Steam App-ID ermitteln
        val steamAppId = if (stats.id.startsWith("steam_")) {
            stats.id.removePrefix("steam_").toLongOrNull() ?: 0L
        } else {
            externalRepository.resolveSteamAppId(stats.id, stats.title) ?: 0L
        }

        val canonicalId = if (steamAppId > 0L) "steam_$steamAppId" else stats.id
        val coverUrl = stats.coverImageUrl.ifBlank {
            if (steamAppId > 0L) AppConfig.getSteamHeaderImageUrl(steamAppId) else ""
        }

        // 3. Live-Preise ermitteln
        val bulkPriceMap = if (steamAppId > 0L) {
            externalRepository.fetchBulkSteamPrices(listOf(steamAppId))
        } else emptyMap()
        val priceInfo = bulkPriceMap[steamAppId]

        val existingWish = existingSnapshot.find {
            !it.isOwned && (it.id == canonicalId || normalizeGameTitle(it.title) == normalizeGameTitle(stats.title))
        }

        val wishlistGame = if (existingWish != null) {
            existingWish.copy(
                coverImageUrl = coverUrl.ifBlank { existingWish.coverImageUrl },
                currentPriceCents = if (priceInfo != null && priceInfo.currentPriceCents >= 0) priceInfo.currentPriceCents else existingWish.currentPriceCents,
                originalPriceCents = if (priceInfo != null && priceInfo.originalPriceCents >= 0) priceInfo.originalPriceCents else existingWish.originalPriceCents,
                discountPercent = if (priceInfo != null && priceInfo.currentPriceCents >= 0) priceInfo.discountPercent else existingWish.discountPercent,
                formattedPrice = priceInfo?.formattedCurrentPrice?.ifBlank { existingWish.formattedPrice } ?: existingWish.formattedPrice,
                updatedAtEpoch = System.currentTimeMillis()
            )
        } else {
            LocalGame(
                id = canonicalId,
                title = stats.title,
                coverImageUrl = coverUrl,
                isPlayed = false,
                isOwned = false,
                is100Percent = false,
                personalRating = 0,
                personalNote = "Aus dem Community-Ranking hinzugefügt",
                isRecommended = false,
                genres = "Community Wishlist",
                currentPriceCents = priceInfo?.currentPriceCents ?: -1,
                originalPriceCents = priceInfo?.originalPriceCents ?: -1,
                discountPercent = priceInfo?.discountPercent ?: 0,
                formattedPrice = priceInfo?.formattedCurrentPrice.orEmpty(),
                lowestHistoricalPrice = priceInfo?.lowestHistoricalPrice.orEmpty()
            )
        }

        gameDao.upsertGame(wishlistGame)

        // 4. Direkt zur Steam-Store-Wunschliste hinzufügen
        val steamResult = if (steamAppId > 0L) {
            externalRepository.addGameToSteamStoreWishlist(steamAppId)
        } else null

        val addedOnSteam = steamResult?.successOnSteam == true
        val statusMsg = if (addedOnSteam) {
            "✨ '${stats.title}' zur Wunschliste hinzugefügt & direkt auf deinem Steam-Account gespeichert!"
        } else if (steamAppId > 0L && hasActiveSteamWebSession()) {
            "✨ '${stats.title}' zur Wunschliste hinzugefügt (${steamResult?.message ?: "Preise & Alarm aktiv"})"
        } else if (steamAppId > 0L) {
            "✨ '${stats.title}' zur Wunschliste hinzugefügt. (Tippe auf 'Steam Login' für 1-Klick Direktspeicherung auf Steam)"
        } else {
            "✨ '${stats.title}' zur Wunschliste hinzugefügt!"
        }

        return Result.success(
            AddToWishlistResult(
                localGame = wishlistGame,
                addedDirectlyToSteam = addedOnSteam,
                statusMessage = statusMsg
            )
        )
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
     * Bereinigt den Titel für einen robusten Duplikatsabgleich
     * (ignoriert Groß-/Kleinschreibung, Trademark-Zeichen, Doppelpunkte, Bindestriche, "Edition" etc.).
     */
    fun normalizeGameTitle(rawTitle: String): String {
        return rawTitle.lowercase(Locale.ROOT)
            .replace("™", "")
            .replace("®", "")
            .replace("©", "")
            .replace(":", " ")
            .replace("-", " ")
            .replace("'", "")
            .replace("’", "")
            .replace("–", " ")
            .replace("_", " ")
            .replace(Regex("\\bgoty\\b"), "game of the year")
            .replace(Regex("\\bedition\\b"), "")
            .replace(Regex("\\bthe\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Erkennt typische Nicht-Spiele wie Soundtracks, Dedicated Server, Tools oder Testversionen.
     */
    fun isLikelyNonGameDlcOrTool(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("soundtrack") ||
            lower.contains(" ost") ||
            lower.contains("dlc") ||
            lower.contains("expansion pack") ||
            lower.contains("season pass") ||
            lower.contains("dedicated server") ||
            lower.contains("beta test") ||
            lower.contains("public test") ||
            lower.contains("artbook") ||
            lower.contains("mod kit") ||
            lower.contains("creation kit") ||
            lower.contains("content pack") ||
            lower.contains("wallpaper") ||
            lower.contains("benchmark")
    }

    /**
     * Zentraler Schutzmechanismus gegen Duplikate & fehlerhafte DLC-Einträge:
     * 1. Bereinigt unaufgelöste Platzhalter ("Steam App #..."), sofern keine Bewertung/Notiz vorliegt.
     * 2. Bereinigt Soundtracks, Dedicated Server & DLCs, sofern keine Bewertung/Notiz vorliegt.
     * 3. Führt doppelte Einträge desselben Spiels (z.B. IGDB-Suche vs. Steam-Import) zusammen,
     *    behält Sterne-Bewertungen, Notizen und 100%-Status sicher bei und löscht die Duplikate.
     * 4. Entfernt Spiele von der Wunschliste, die sich bereits in "Meine Bibliothek" befinden.
     */
    suspend fun cleanupDuplicateGames(): Int {
        val allGames = gameDao.getAllGamesSnapshot()
        var removedCount = 0

        // 1. Unaufgelöste Platzhalter ohne Nutzer-Bewertung löschen
        val placeholders = allGames.filter { game ->
            game.title.startsWith("Steam App #") &&
                game.personalRating == 0 &&
                game.personalNote.isBlank() &&
                !game.is100Percent
        }
        for (ph in placeholders) {
            gameDao.deleteGameById(ph.id)
            removedCount++
        }

        // 2. Soundtracks/Tools/Server ohne Bewertung löschen
        val currentGames = gameDao.getAllGamesSnapshot()
        val dlcOrTools = currentGames.filter { game ->
            isLikelyNonGameDlcOrTool(game.title) &&
                game.personalRating == 0 &&
                game.personalNote.isBlank() &&
                !game.is100Percent
        }
        for (dlc in dlcOrTools) {
            gameDao.deleteGameById(dlc.id)
            removedCount++
        }

        // 3. Titel-Duplikate in "Meine Bibliothek" (`isOwned = true`) zusammenführen
        val ownedGames = gameDao.getAllGamesSnapshot().filter { it.isOwned }
        val groupedByTitle = ownedGames.groupBy { normalizeGameTitle(it.title) }

        for ((normTitle, group) in groupedByTitle) {
            if (normTitle.isBlank() || group.size <= 1) continue

            // Bester Haupteintrag: Bewertung > Notiz > Steam-ID > Spielzeit > Preis
            val keeper = group.maxWithOrNull(
                compareBy<LocalGame> { if (it.personalRating > 0 || it.isRecommended || it.is100Percent) 1 else 0 }
                    .thenBy { if (it.personalNote.isNotBlank() && !it.personalNote.startsWith("Steam")) 1 else 0 }
                    .thenBy { if (it.id.startsWith("steam_")) 1 else 0 }
                    .thenBy { it.playtimeHours }
                    .thenBy { if (it.currentPriceCents >= 0) 1 else 0 }
            ) ?: continue

            var mergedKeeper = keeper
            for (other in group) {
                if (other.id == keeper.id) continue
                val mergedRating = if (mergedKeeper.personalRating > 0) mergedKeeper.personalRating else other.personalRating
                val mergedRecommended = mergedKeeper.isRecommended || other.isRecommended
                val merged100 = mergedKeeper.is100Percent || other.is100Percent
                val mergedPlayed = mergedKeeper.isPlayed || other.isPlayed
                val mergedHours = maxOf(mergedKeeper.playtimeHours, other.playtimeHours)
                val mergedNote = when {
                    mergedKeeper.personalNote.isNotBlank() && !mergedKeeper.personalNote.startsWith("Steam") -> mergedKeeper.personalNote
                    other.personalNote.isNotBlank() && !other.personalNote.startsWith("Steam") -> other.personalNote
                    mergedKeeper.personalNote.isNotBlank() -> mergedKeeper.personalNote
                    else -> other.personalNote
                }
                val mergedPriceCents = if (mergedKeeper.currentPriceCents >= 0) mergedKeeper.currentPriceCents else other.currentPriceCents
                val mergedFormattedPrice = mergedKeeper.formattedPrice.ifBlank { other.formattedPrice }
                val mergedAlert = if (mergedKeeper.targetPriceAlertEur > 0.0) mergedKeeper.targetPriceAlertEur else other.targetPriceAlertEur

                mergedKeeper = mergedKeeper.copy(
                    personalRating = mergedRating,
                    isRecommended = mergedRecommended,
                    is100Percent = merged100,
                    isPlayed = mergedPlayed,
                    playtimeHours = mergedHours,
                    personalNote = mergedNote,
                    currentPriceCents = mergedPriceCents,
                    formattedPrice = mergedFormattedPrice,
                    targetPriceAlertEur = mergedAlert,
                    isPriceAlertTriggered = mergedKeeper.isPriceAlertTriggered || other.isPriceAlertTriggered,
                    updatedAtEpoch = System.currentTimeMillis()
                )

                gameDao.deleteGameById(other.id)
                removedCount++
            }
            gameDao.upsertGame(mergedKeeper)
        }

        // 4. Wunschlisten-Einträge entfernen, die der Nutzer bereits in seiner Bibliothek besitzt
        val finalOwnedSnapshot = gameDao.getAllGamesSnapshot().filter { it.isOwned }
        val finalOwnedIds = finalOwnedSnapshot.map { it.id }.toSet()
        val finalOwnedTitles = finalOwnedSnapshot.map { normalizeGameTitle(it.title) }.toSet()

        val wishlistGames = gameDao.getAllGamesSnapshot().filter { !it.isOwned }
        for (wish in wishlistGames) {
            if (wish.id in finalOwnedIds || normalizeGameTitle(wish.title) in finalOwnedTitles) {
                gameDao.deleteGameById(wish.id)
                removedCount++
            }
        }

        return removedCount
    }

    /**
     * Importiert ALLE Spiele aus der Steam-Spielebibliothek (Owned Games) und speichert sie in Room.
     * Enthält einen automatischen Schutzmechanismus:
     * - Bereinigt alte DLC- und Tool-Reste aus vorherigen Syncs.
     * - Führt IGDB- und Steam-Einträge desselben Spiels zusammen.
     * - Verhindert jede Duplizierung beim mehrfachen Anmelden / Synchronisieren.
     */
    suspend fun importSteamLibraryWithReport(steamIdInput: String): Result<SteamLibraryImportReport> {
        if (isCurrentUserAdmin() && AppConfig.STEAM_API_KEY.isBlank()) {
            loadAdminVaultSecrets()
        }
        val knownTitles = buildKnownSteamTitlesMap()
        val result = externalRepository.importSteamOwnedGamesWithReport(steamIdInput, knownTitles)
        return result.map { report ->
            val existingSnapshot = gameDao.getAllGamesSnapshot()
            val existingMap = existingSnapshot.associateBy { it.id }
            val importedIds = report.games.map { it.id }.toSet()

            // 1. Entferne veraltete Steam-Einträge (z.B. alte DLCs/Tools aus frühere fehlerhaften Syncs),
            // die in der autoritativen Steam-Bibliothek nicht vorkommen und keine persönliche Bewertung/Notiz haben
            val staleSteamGames = existingSnapshot.filter { existing ->
                existing.isOwned &&
                    existing.id.startsWith("steam_") &&
                    existing.id !in importedIds &&
                    existing.personalRating == 0 &&
                    !existing.is100Percent &&
                    (existing.personalNote.isBlank() || existing.personalNote.startsWith("Steam"))
            }
            for (stale in staleSteamGames) {
                gameDao.deleteGameById(stale.id)
            }

            // 2. Führe eventuell bereits vorhandene manuelle/IGDB-Einträge mit dem Steam-Spiel zusammen
            val existingByNormTitle = existingSnapshot.filter { !it.id.startsWith("steam_") }
                .associateBy { normalizeGameTitle(it.title) }

            val merged = report.games.map { imported ->
                val prev = existingMap[imported.id]
                val prevByTitle = existingByNormTitle[normalizeGameTitle(imported.title)]

                val userRating = maxOf(prev?.personalRating ?: 0, prevByTitle?.personalRating ?: 0)
                val userRecommended = (prev?.isRecommended == true) || (prevByTitle?.isRecommended == true)
                val user100 = (prev?.is100Percent == true) || (prevByTitle?.is100Percent == true)
                val userPlayed = (prev?.isPlayed == true) || (prevByTitle?.isPlayed == true) || imported.isPlayed
                val userNote = when {
                    prev?.personalNote?.isNotBlank() == true && !prev.personalNote.startsWith("Steam") -> prev.personalNote
                    prevByTitle?.personalNote?.isNotBlank() == true && !prevByTitle.personalNote.startsWith("Steam") -> prevByTitle.personalNote
                    prev?.personalNote?.isNotBlank() == true -> prev.personalNote
                    else -> imported.personalNote
                }

                if (prevByTitle != null && prevByTitle.id != imported.id) {
                    gameDao.deleteGameById(prevByTitle.id)
                }

                val mergedPriceCents = if (imported.currentPriceCents >= 0) imported.currentPriceCents else (prev?.currentPriceCents ?: -1)
                val mergedOriginalCents = if (imported.originalPriceCents >= 0) imported.originalPriceCents else (prev?.originalPriceCents ?: -1)
                val mergedDiscount = if (imported.currentPriceCents >= 0) imported.discountPercent else (prev?.discountPercent ?: 0)
                val mergedFormatted = imported.formattedPrice.ifBlank { prev?.formattedPrice.orEmpty() }
                val targetAlert = prev?.targetPriceAlertEur ?: 0.0
                val triggered = targetAlert > 0.0 && mergedPriceCents >= 0 && (mergedPriceCents / 100.0) <= targetAlert + 0.001

                val cleanTitle = if (imported.title.startsWith("Steam App #") && prev?.title?.startsWith("Steam App #") == false) {
                    prev.title
                } else {
                    imported.title
                }

                val preservedAchievementsJson = imported.achievementsJson.ifBlank {
                    prev?.achievementsJson?.takeIf { it.isNotBlank() }
                        ?: prevByTitle?.achievementsJson.orEmpty()
                }

                imported.copy(
                    title = cleanTitle,
                    isOwned = true,
                    personalRating = userRating,
                    isRecommended = userRecommended,
                    is100Percent = user100,
                    isPlayed = userPlayed,
                    personalNote = userNote,
                    playtimeHours = maxOf(prev?.playtimeHours ?: 0, imported.playtimeHours),
                    currentPriceCents = mergedPriceCents,
                    originalPriceCents = mergedOriginalCents,
                    discountPercent = mergedDiscount,
                    formattedPrice = mergedFormatted,
                    lowestHistoricalPrice = imported.lowestHistoricalPrice.ifBlank { prev?.lowestHistoricalPrice.orEmpty() },
                    targetPriceAlertEur = targetAlert,
                    isPriceAlertTriggered = triggered,
                    achievementsJson = preservedAchievementsJson,
                    updatedAtEpoch = System.currentTimeMillis()
                )
            }

            // 3. Entferne alle Spiele von der Wunschliste, die sich nun in der Bibliothek befinden
            for (imported in merged) {
                val wishMatch = existingSnapshot.find { !it.isOwned && (it.id == imported.id || normalizeGameTitle(it.title) == normalizeGameTitle(imported.title)) }
                if (wishMatch != null) {
                    gameDao.deleteGameById(wishMatch.id)
                }
            }

            gameDao.upsertGames(merged)

            // 4. Abschließender Duplikats-Bereinigungslauf
            cleanupDuplicateGames()

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
     * Aktualisiert gebündelt die aktuellen Steam-Preise (inklusive Free-to-Play Erkennung)
     * für ALLE Spiele in Bibliothek & Wunschliste ohne Limitierung und gibt eine Liste aller Spiele zurück,
     * deren Preisalarm erreicht wurde.
     */
    suspend fun refreshAllPricesAndCheckAlerts(): Result<List<String>> = runCatching {
        val allGames = gameDao.getAllGamesSnapshot()
        if (allGames.isEmpty()) return@runCatching emptyList()

        // Falls einzelne IGDB/manuelle Spiele noch keine Steam-AppID haben,
        // lösen wir diese im Hintergrund auf, damit auch dort Preise automatisch erscheinen.
        val resolvedPairs = mutableListOf<Pair<Long, LocalGame>>()
        var newlyResolvedCount = 0
        for (game in allGames) {
            val directId = if (game.id.startsWith("steam_")) {
                game.id.removePrefix("steam_").toLongOrNull() ?: 0L
            } else 0L
            if (directId > 0L) {
                resolvedPairs.add(directId to game)
            } else if (newlyResolvedCount < 15 && (game.formattedPrice.isBlank() || game.currentPriceCents < 0)) {
                val resolvedId = externalRepository.resolveSteamAppIdForGame(game)
                if (resolvedId != null && resolvedId > 0L) {
                    newlyResolvedCount++
                    resolvedPairs.add(resolvedId to game)
                }
            }
        }

        if (resolvedPairs.isEmpty()) return@runCatching emptyList()

        // Priorisiere Spiele ohne bisherigen Preis, Spiele mit aktivem Preisalarm und Wunschlisten-Spiele,
        // aktualisiere aber garantiert ALLE Spiele (auch 250+ Bibliotheksspiele) in schnellen 50er-Blöcken.
        val prioritized = resolvedPairs.sortedByDescending { (_, g) ->
            (if (g.formattedPrice.isBlank() || g.currentPriceCents < 0) 4 else 0) +
                (if (g.targetPriceAlertEur > 0.0) 2 else 0) +
                (if (!g.isOwned) 1 else 0)
        }

        val triggeredDescriptions = mutableListOf<String>()

        for (chunk in prioritized.chunked(50)) {
            val priceMap = externalRepository.fetchBulkSteamPrices(chunk.map { it.first })
            val chunkUpdated = mutableListOf<LocalGame>()

            for ((appId, game) in chunk) {
                val info = priceMap[appId] ?: continue
                val latest = gameDao.getGameById(game.id) ?: game
                val updated = applyPriceInfoToGame(latest, info)
                chunkUpdated.add(updated)
                if (updated.isPriceAlertTriggered) {
                    triggeredDescriptions.add("${updated.title} (${updated.formattedPrice})")
                }
            }

            if (chunkUpdated.isNotEmpty()) {
                gameDao.upsertGames(chunkUpdated)
            }
        }

        triggeredDescriptions
    }

    /**
     * Synchronisiert im Hintergrund automatisch alle Steam-Errungenschaften und den 100%-Platin-Status
     * für die Spiele in der Bibliothek, ohne dass der Nutzer jedes Spiel einzeln anklicken muss.
     */
    suspend fun autoSyncMissingAchievementsAnd100Percent(steamIdInput: String) {
        if (isCurrentUserAdmin() && AppConfig.STEAM_API_KEY.isBlank()) {
            loadAdminVaultSecrets()
        }
        val allOwned = gameDao.getAllGamesSnapshot().filter { it.isOwned }
        if (allOwned.isEmpty()) return

        // Prüfe bevorzugt gespielte Spiele und alle Spiele, deren Achievements noch nicht geprüft wurden
        val candidates = allOwned
            .filter { it.achievementsJson.isBlank() }
            .sortedByDescending { game ->
                (if (game.playtimeHours > 0) 10_000 + game.playtimeHours else 0) +
                    (if (game.isPlayed) 5_000 else 0)
            }

        if (candidates.isEmpty()) return

        for (chunk in candidates.chunked(6)) {
            coroutineScope {
                chunk.map { game ->
                    async(Dispatchers.IO) {
                        fetchAndCacheGameAchievements(
                            game = game,
                            steamIdInput = steamIdInput,
                            markCheckedOnEmpty = true
                        )
                    }
                }.awaitAll()
            }
        }
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
     * Setzt bei 100% freigeschalteten Errungenschaften automatisch `is100Percent = true` ("100% Platin").
     */
    suspend fun fetchAndCacheGameAchievements(
        game: LocalGame,
        steamIdInput: String,
        markCheckedOnEmpty: Boolean = false
    ): Result<List<SteamAchievement>> {
        if (isCurrentUserAdmin() && AppConfig.STEAM_API_KEY.isBlank()) {
            loadAdminVaultSecrets()
        }
        val result = externalRepository.fetchGameAchievements(game, steamIdInput)
        result.onSuccess { achievements ->
            if (achievements.isNotEmpty()) {
                val json = SteamAchievementSerializer.toJsonString(achievements)
                val allUnlocked = achievements.all { it.isUnlocked }
                val anyUnlocked = achievements.any { it.isUnlocked }
                val latestLocal = gameDao.getGameById(game.id) ?: game
                val became100 = !latestLocal.is100Percent && allUnlocked
                val updated = latestLocal.copy(
                    achievementsJson = json,
                    is100Percent = latestLocal.is100Percent || allUnlocked,
                    isPlayed = latestLocal.isPlayed || anyUnlocked,
                    updatedAtEpoch = System.currentTimeMillis()
                )
                gameDao.upsertGame(updated)
                if (became100 && isGoogleSignedIn()) {
                    cloudRepository.syncGameToCloud(updated)
                }
            } else if (markCheckedOnEmpty) {
                val latestLocal = gameDao.getGameById(game.id) ?: game
                if (latestLocal.achievementsJson.isBlank()) {
                    gameDao.upsertGame(latestLocal.copy(achievementsJson = "[]"))
                }
            }
        }.onFailure {
            if (markCheckedOnEmpty) {
                val latestLocal = gameDao.getGameById(game.id) ?: game
                if (latestLocal.achievementsJson.isBlank()) {
                    gameDao.upsertGame(latestLocal.copy(achievementsJson = "[]"))
                }
            }
        }
        return result
    }
}
