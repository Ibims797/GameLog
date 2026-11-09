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
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow

data class LiveWishlistSyncSummary(
    val totalCount: Int,
    val newlyAddedTitles: List<String>
)

/**
 * Zentrales Hybrid-Repository (Room lokal + Firebase Cloud-Sync + IGDB/Steam Retrofit + Cloud Admin Vault).
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
        val newGame = if (existing != null) {
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
        gameDao.upsertGame(newGame)
        return newGame
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

    /**
     * Importiert ALLE Spiele aus der Steam-Spielebibliothek (Owned Games) und speichert sie in Room.
     */
    suspend fun importSteamLibrary(steamIdInput: String): Result<Int> {
        if (isCurrentUserAdmin() && AppConfig.STEAM_API_KEY.isBlank()) {
            loadAdminVaultSecrets()
        }
        val knownTitles = buildKnownSteamTitlesMap()
        val result = externalRepository.importSteamOwnedGames(steamIdInput, knownTitles)
        return result.map { importedList ->
            val existingMap = gameDao.getAllGamesSnapshot().associateBy { it.id }
            val merged = importedList.map { imported ->
                val prev = existingMap[imported.id]
                if (prev != null) {
                    prev.copy(
                        title = if (imported.title.startsWith("Steam App #") && !prev.title.startsWith("Steam App #")) {
                            prev.title
                        } else {
                            imported.title
                        },
                        isOwned = true,
                        playtimeHours = maxOf(prev.playtimeHours, imported.playtimeHours),
                        isPlayed = prev.isPlayed || imported.isPlayed,
                        updatedAtEpoch = System.currentTimeMillis()
                    )
                } else {
                    imported
                }
            }
            gameDao.upsertGames(merged)
            merged.size
        }
    }

    /**
     * Importiert ALLE Spiele aus der Steam-Wunschliste (`isOwned = false`) und gibt zurück,
     * welche neuen Spiele im Vergleich zum bisherigen lokalen Stand hinzugekommen sind (für den Live-Updater).
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
            val newWishlistGames = importedList.mapNotNull { imported ->
                val prev = existingMap[imported.id]
                when {
                    prev == null -> {
                        newlyAddedTitles.add(imported.title)
                        imported
                    }
                    !prev.isOwned -> {
                        prev.copy(
                            title = if (!imported.title.startsWith("Steam App #")) imported.title else prev.title,
                            coverImageUrl = imported.coverImageUrl.ifBlank { prev.coverImageUrl },
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
                newlyAddedTitles = newlyAddedTitles
            )
        }
    }

    suspend fun importSteamWishlist(steamIdInput: String): Result<Int> {
        return importSteamWishlistWithDiff(steamIdInput).map { it.totalCount }
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
