package com.example.data.repository

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
     * Nur mit Google-Anmeldung sind Cloud-Bewertungen, Empfehlungen und das Community-Ranking aktiv.
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
     * Nur für den verifizierten Admin (`nico.schaefer.hn@gmail.com`) zugänglich.
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
     * Synchronisiert bei App-Start vorhandene Cloud-Einträge des Nutzers in die lokale Room-Datenbank,
     * falls der Nutzer mit Google angemeldet ist.
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
     * Speichert ein Spiel lokal in Room (für Offline- & Steam-Modus).
     */
    suspend fun saveGameLocally(game: LocalGame): LocalGame {
        val updatedGame = game.copy(updatedAtEpoch = System.currentTimeMillis())
        gameDao.upsertGame(updatedGame)
        return updatedGame
    }

    /**
     * Speichert oder aktualisiert ein Spiel lokal in Room und synchronisiert es (sofern mit Google angemeldet)
     * direkt mit `GlobalGameStats` in Firestore.
     */
    suspend fun saveGameAndSyncToCloud(game: LocalGame): Result<GlobalGameStats> {
        val updatedGame = game.copy(updatedAtEpoch = System.currentTimeMillis())
        gameDao.upsertGame(updatedGame)
        return cloudRepository.syncGameToCloud(updatedGame)
    }

    /**
     * Fügt ein Suchergebnis aus IGDB / Steam zur eigenen Bibliothek (`isOwned = true`)
     * oder zur Wunschliste (`isOwned = false`) hinzu.
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
        if (isGoogleSignedIn()) {
            cloudRepository.syncGameToCloud(newGame)
        }
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
        if (isGoogleSignedIn()) {
            cloudRepository.syncGameToCloud(updated)
        }
    }

    suspend fun deleteLocalGame(gameId: String) {
        gameDao.deleteGameById(gameId)
    }

    suspend fun searchExternalGames(query: String): Result<List<ExternalGameSearchResult>> {
        return externalRepository.searchGames(query)
    }

    /**
     * Importiert die Steam-Spielebibliothek (Owned Games) und speichert sie in Room.
     */
    suspend fun importSteamLibrary(steamIdInput: String): Result<Int> {
        val result = externalRepository.importSteamOwnedGames(steamIdInput)
        return result.map { importedList ->
            val existingMap = gameDao.getAllGamesSnapshot().associateBy { it.id }
            val merged = importedList.map { imported ->
                val prev = existingMap[imported.id]
                if (prev != null) {
                    prev.copy(
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
     * Importiert die Steam-Wunschliste (`isOwned = false`) und speichert sie in Room.
     */
    suspend fun importSteamWishlist(steamIdInput: String): Result<Int> {
        val result = externalRepository.importSteamWishlist(steamIdInput)
        return result.map { importedList ->
            val existingMap = gameDao.getAllGamesSnapshot().associateBy { it.id }
            val newWishlistGames = importedList.mapNotNull { imported ->
                val prev = existingMap[imported.id]
                when {
                    prev == null -> imported
                    !prev.isOwned -> prev.copy(updatedAtEpoch = System.currentTimeMillis())
                    else -> null
                }
            }
            if (newWishlistGames.isNotEmpty()) {
                gameDao.upsertGames(newWishlistGames)
            }
            newWishlistGames.size
        }
    }

    /**
     * Lädt die Steam-Errungenschaften eines Spiels und speichert sie lokal in `LocalGame.achievementsJson`,
     * damit sie auch im Offline-Modus sofort zur Verfügung stehen.
     */
    suspend fun fetchAndCacheGameAchievements(
        game: LocalGame,
        steamIdInput: String
    ): Result<List<SteamAchievement>> {
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
