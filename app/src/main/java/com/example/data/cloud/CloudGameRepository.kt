package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.config.AppConfig
import com.example.data.local.LocalGame
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Cloud-Repository für Firebase Firestore (`GlobalGameStats`, `UserGameContribution` & `AdminApiSecrets`).
 * - Synchronisiert Bewertungen und Empfehlungen atomar in die globale Community-Tabelle.
 * - Spiele ohne Bewertung (`ratingCount == 0`) und ohne Empfehlung (`recommendCount == 0`) werden
 *   NICHT im Community-Ranking gelistet (bloßes Hinzufügen zur Bibliothek oder ein 100%-Run allein erzeugt keinen Ranking-Eintrag).
 * - Bietet dem verifizierten Admin (`nico.schaefer.hn@gmail.com`) das Löschen einzelner Spiele aus dem Community-Ranking.
 */
class CloudGameRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth
        get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Nutzer muss mit Google angemeldet sein, um Cloud-Daten zu synchronisieren.")
    }

    /**
     * Prüft anhand des aktuell angemeldeten Google-Kontos, ob der Nutzer der verifizierte Admin (`nico.schaefer.hn@gmail.com`) ist.
     */
    fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        val emailMatches = user.email.equals(AppConfig.ADMIN_EMAIL, ignoreCase = true) ||
            user.providerData.any { it.email.equals(AppConfig.ADMIN_EMAIL, ignoreCase = true) }
        return emailMatches && user.isEmailVerified
    }

    private fun sanitizeFirestoreId(rawId: String): String {
        return rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "unknown_game" }
    }

    /**
     * Lädt die geheimen API-Keys aus dem geschützten Cloud-Tresor (`/admin_vault/api_secrets`).
     */
    suspend fun fetchAdminVaultSecrets(): Result<AdminApiSecrets?> {
        requireUserId()
        val docRef = db.collection(AppConfig.COLLECTION_ADMIN_VAULT)
            .document(AppConfig.DOCUMENT_API_SECRETS)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                val secrets = snap.toObject(
                    AdminApiSecrets::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (secrets != null) {
                    AppConfig.updateRuntimeAdminKeys(
                        twitchClientId = secrets.twitchClientId,
                        twitchClientSecret = secrets.twitchClientSecret,
                        steamApiKey = secrets.steamApiKey
                    )
                }
                Result.success(secrets)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Speichert oder aktualisiert die API-Keys im geschützten Cloud-Tresor (`/admin_vault/api_secrets`).
     */
    suspend fun saveAdminVaultSecrets(
        twitchClientId: String,
        twitchClientSecret: String,
        steamApiKey: String
    ): Result<AdminApiSecrets> {
        val uid = requireUserId()
        val docRef = db.collection(AppConfig.COLLECTION_ADMIN_VAULT)
            .document(AppConfig.DOCUMENT_API_SECRETS)

        val cleanTwitchId = twitchClientId.trim().take(256)
        val cleanTwitchSecret = twitchClientSecret.trim().take(256)
        val cleanSteamKey = steamApiKey.trim().take(256)

        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updateMap = mapOf(
                    "twitchClientId" to cleanTwitchId,
                    "twitchClientSecret" to cleanTwitchSecret,
                    "steamApiKey" to cleanSteamKey,
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            } else {
                val createMap = mapOf(
                    "twitchClientId" to cleanTwitchId,
                    "twitchClientSecret" to cleanTwitchSecret,
                    "steamApiKey" to cleanSteamKey,
                    "updatedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createMap).await()
            }

            AppConfig.updateRuntimeAdminKeys(
                twitchClientId = cleanTwitchId,
                twitchClientSecret = cleanTwitchSecret,
                steamApiKey = cleanSteamKey
            )

            Result.success(
                AdminApiSecrets(
                    id = AppConfig.DOCUMENT_API_SECRETS,
                    twitchClientId = cleanTwitchId,
                    twitchClientSecret = cleanTwitchSecret,
                    steamApiKey = cleanSteamKey,
                    updatedByUid = uid
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Echtzeit-Beobachtung des globalen Community-Rankings (`GlobalGameStats`).
     * WICHTIG: Zeigt ausschließlich Spiele an, die mindestens eine echte Bewertung (`ratingCount > 0`)
     * ODER mindestens eine Weiterempfehlung (`recommendCount > 0`) besitzen!
     * Bloße Bibliotheks-Einträge oder reine 100%-Runs ohne Bewertung/Empfehlung erscheinen NICHT im Ranking.
     */
    fun observeCommunityRankings(): Flow<List<GlobalGameStats>> = flow {
        val uid = requireUserId()
        val path = AppConfig.COLLECTION_GLOBAL_STATS
        emitAll(
            db.collection(path)
                .whereEqualTo("isCommunityEntry", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GlobalGameStats::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { stat ->
                            stat.ratingCount > 0L || stat.recommendCount > 0L || stat.averageRating > 0.0
                        }
                        .sortedWith(
                            compareByDescending<GlobalGameStats> { it.averageRating }
                                .thenByDescending { it.recommendCount }
                                .thenByDescending { it.total100PercentRuns }
                        )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Exklusive Admin-Funktion (`nico.schaefer.hn@gmail.com`):
     * Löscht ein Spiel dauerhaft aus dem globalen Community-Ranking (`/global_game_stats/{gameId}`).
     */
    suspend fun deleteGameFromCommunityRanking(gameId: String): Result<Unit> {
        requireUserId()
        if (!isCurrentUserAdmin()) {
            return Result.failure(
                IllegalAccessException("Zugriff verweigert: Nur der Admin (${AppConfig.ADMIN_EMAIL}) kann Spiele aus dem Ranking löschen.")
            )
        }
        val docId = sanitizeFirestoreId(gameId)
        val docRef = db.collection(AppConfig.COLLECTION_GLOBAL_STATS).document(docId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Echtzeit-Beobachtung der eigenen Cloud-Beiträge des angemeldeten Nutzers.
     */
    fun observeUserContributions(): Flow<List<UserGameContribution>> = flow {
        val uid = requireUserId()
        val path = "${AppConfig.COLLECTION_USERS}/$uid/${AppConfig.SUBCOLLECTION_CONTRIBUTIONS}"
        emitAll(
            db.collection(AppConfig.COLLECTION_USERS)
                .document(uid)
                .collection(AppConfig.SUBCOLLECTION_CONTRIBUTIONS)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserGameContribution::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Lädt alle bisherigen Cloud-Einträge des Nutzers einmalig.
     */
    suspend fun fetchUserContributionsOnce(): Result<List<UserGameContribution>> {
        val uid = requireUserId()
        val path = "${AppConfig.COLLECTION_USERS}/$uid/${AppConfig.SUBCOLLECTION_CONTRIBUTIONS}"
        return try {
            val snapshot = db.collection(AppConfig.COLLECTION_USERS)
                .document(uid)
                .collection(AppConfig.SUBCOLLECTION_CONTRIBUTIONS)
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val list = snapshot.toObjects(
                UserGameContribution::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    /**
     * Synchronisiert ein lokales Spiel (`LocalGame`) mit der Cloud:
     * 1. Speichert den persönlichen Stand unter `/users/{uid}/game_contributions/{gameId}`.
     * 2. Aktualisiert `/global_game_stats/{gameId}` NUR DANN, wenn das Spiel bewertet (`personalRating > 0`)
     *    oder empfohlen (`isRecommended == true`) wurde (oder eine frühere Bewertung/Empfehlung geändert wurde).
     *    Bloßes Hinzufügen zur Bibliothek oder ein 100%-Run allein legt KEINEN Eintrag im Community-Ranking an!
     */
    suspend fun syncGameToCloud(game: LocalGame): Result<GlobalGameStats> {
        val uid = requireUserId()
        val docId = sanitizeFirestoreId(game.id)
        val userDocRef = db.collection(AppConfig.COLLECTION_USERS)
            .document(uid)
            .collection(AppConfig.SUBCOLLECTION_CONTRIBUTIONS)
            .document(docId)
        val globalDocRef = db.collection(AppConfig.COLLECTION_GLOBAL_STATS)
            .document(docId)

        return try {
            val existingUserSnap = userDocRef.get().await()

            val oldRating = if (existingUserSnap.exists()) {
                (existingUserSnap.getLong("personalRating") ?: 0L).toInt().coerceIn(0, 5)
            } else 0
            val oldRecommended = if (existingUserSnap.exists()) {
                existingUserSnap.getBoolean("isRecommended") ?: false
            } else false
            val old100Percent = if (existingUserSnap.exists()) {
                existingUserSnap.getBoolean("is100Percent") ?: false
            } else false

            val newRating = game.personalRating.coerceIn(0, 5)
            val newRecommended = game.isRecommended
            val new100Percent = game.is100Percent

            val safeTitle = game.title.take(200).ifBlank { "Unbekanntes Spiel" }
            val safeCover = game.coverImageUrl.take(1000)

            // 1. Persönlichen Nutzer-Beitrag speichern
            if (existingUserSnap.exists()) {
                val updateUserMap = mapOf(
                    "title" to safeTitle,
                    "coverImageUrl" to safeCover,
                    "isPlayed" to game.isPlayed,
                    "isOwned" to game.isOwned,
                    "is100Percent" to new100Percent,
                    "isRecommended" to newRecommended,
                    "personalRating" to newRating.toLong(),
                    "personalNote" to game.personalNote.take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.update(updateUserMap).await()
            } else {
                val createUserMap = mapOf(
                    "userId" to uid,
                    "gameId" to docId,
                    "title" to safeTitle,
                    "coverImageUrl" to safeCover,
                    "isPlayed" to game.isPlayed,
                    "isOwned" to game.isOwned,
                    "is100Percent" to new100Percent,
                    "isRecommended" to newRecommended,
                    "personalRating" to newRating.toLong(),
                    "personalNote" to game.personalNote.take(2000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(createUserMap).await()
            }

            // 2. Prüfen, ob das Spiel für das globale Community-Ranking relevant ist:
            // Nur Spiele mit Bewertung (1-5 Sterne) oder Weiterempfehlung erscheinen im Community-Ranking!
            val hasRatingOrRecommendationNow = newRating > 0 || newRecommended
            val hadRatingOrRecommendationBefore = oldRating > 0 || oldRecommended

            if (!hasRatingOrRecommendationNow && !hadRatingOrRecommendationBefore) {
                return Result.success(
                    GlobalGameStats(
                        id = docId,
                        title = safeTitle,
                        coverImageUrl = safeCover,
                        averageRating = 0.0,
                        ratingSum = 0L,
                        ratingCount = 0L,
                        recommendCount = 0L,
                        total100PercentRuns = 0L,
                        isCommunityEntry = false,
                        lastUpdatedBy = uid
                    )
                )
            }

            val existingGlobalSnap = globalDocRef.get().await()
            if (!existingGlobalSnap.exists() && !hasRatingOrRecommendationNow) {
                return Result.success(
                    GlobalGameStats(
                        id = docId,
                        title = safeTitle,
                        coverImageUrl = safeCover,
                        averageRating = 0.0
                    )
                )
            }

            val currentRatingSum = if (existingGlobalSnap.exists()) {
                existingGlobalSnap.getLong("ratingSum") ?: 0L
            } else 0L
            val currentRatingCount = if (existingGlobalSnap.exists()) {
                existingGlobalSnap.getLong("ratingCount") ?: 0L
            } else 0L
            val currentRecommendCount = if (existingGlobalSnap.exists()) {
                existingGlobalSnap.getLong("recommendCount") ?: 0L
            } else 0L
            val current100Runs = if (existingGlobalSnap.exists()) {
                existingGlobalSnap.getLong("total100PercentRuns") ?: 0L
            } else 0L
            val currentStar5 = if (existingGlobalSnap.exists()) {
                existingGlobalSnap.getLong("star5Count") ?: 0L
            } else 0L
            val currentStar4 = if (existingGlobalSnap.exists()) {
                existingGlobalSnap.getLong("star4Count") ?: 0L
            } else 0L
            val currentStar3 = if (existingGlobalSnap.exists()) {
                existingGlobalSnap.getLong("star3Count") ?: 0L
            } else 0L
            val currentStar2 = if (existingGlobalSnap.exists()) {
                existingGlobalSnap.getLong("star2Count") ?: 0L
            } else 0L
            val currentStar1 = if (existingGlobalSnap.exists()) {
                existingGlobalSnap.getLong("star1Count") ?: 0L
            } else 0L

            val ratingSumDelta = (newRating - oldRating).toLong()
            val ratingCountDelta = when {
                oldRating == 0 && newRating > 0 -> 1L
                oldRating > 0 && newRating == 0 -> -1L
                else -> 0L
            }
            val recommendDelta = when {
                !oldRecommended && newRecommended -> 1L
                oldRecommended && !newRecommended -> -1L
                else -> 0L
            }
            val runs100Delta = when {
                !old100Percent && new100Percent -> 1L
                old100Percent && !new100Percent -> -1L
                else -> 0L
            }

            fun starDeltaFor(targetStar: Int): Long {
                var d = 0L
                if (oldRating == targetStar) d -= 1L
                if (newRating == targetStar) d += 1L
                return d
            }

            val updatedRatingSum = max(0L, currentRatingSum + ratingSumDelta)
            val updatedRatingCount = max(0L, currentRatingCount + ratingCountDelta)
            val updatedRecommendCount = max(0L, currentRecommendCount + recommendDelta)
            val updated100Runs = max(0L, current100Runs + runs100Delta)
            val updatedStar5 = max(0L, currentStar5 + starDeltaFor(5))
            val updatedStar4 = max(0L, currentStar4 + starDeltaFor(4))
            val updatedStar3 = max(0L, currentStar3 + starDeltaFor(3))
            val updatedStar2 = max(0L, currentStar2 + starDeltaFor(2))
            val updatedStar1 = max(0L, currentStar1 + starDeltaFor(1))

            val rawAverage = if (updatedRatingCount > 0L) {
                updatedRatingSum.toDouble() / updatedRatingCount.toDouble()
            } else {
                0.0
            }
            val roundedAverage = ((rawAverage * 100.0).roundToInt() / 100.0).coerceIn(0.0, 5.0)

            if (existingGlobalSnap.exists()) {
                val updateGlobalMap = mapOf(
                    "title" to safeTitle,
                    "coverImageUrl" to safeCover,
                    "averageRating" to roundedAverage,
                    "ratingSum" to updatedRatingSum,
                    "ratingCount" to updatedRatingCount,
                    "recommendCount" to updatedRecommendCount,
                    "total100PercentRuns" to updated100Runs,
                    "star5Count" to updatedStar5,
                    "star4Count" to updatedStar4,
                    "star3Count" to updatedStar3,
                    "star2Count" to updatedStar2,
                    "star1Count" to updatedStar1,
                    "lastUpdatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                globalDocRef.update(updateGlobalMap).await()
            } else {
                val createGlobalMap = mapOf(
                    "title" to safeTitle,
                    "coverImageUrl" to safeCover,
                    "averageRating" to roundedAverage,
                    "ratingSum" to updatedRatingSum,
                    "ratingCount" to updatedRatingCount,
                    "recommendCount" to updatedRecommendCount,
                    "total100PercentRuns" to updated100Runs,
                    "star5Count" to updatedStar5,
                    "star4Count" to updatedStar4,
                    "star3Count" to updatedStar3,
                    "star2Count" to updatedStar2,
                    "star1Count" to updatedStar1,
                    "isCommunityEntry" to true,
                    "lastUpdatedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                globalDocRef.set(createGlobalMap).await()
            }

            Result.success(
                GlobalGameStats(
                    id = docId,
                    title = safeTitle,
                    coverImageUrl = safeCover,
                    averageRating = roundedAverage,
                    ratingSum = updatedRatingSum,
                    ratingCount = updatedRatingCount,
                    recommendCount = updatedRecommendCount,
                    total100PercentRuns = updated100Runs,
                    star5Count = updatedStar5,
                    star4Count = updatedStar4,
                    star3Count = updatedStar3,
                    star2Count = updatedStar2,
                    star1Count = updatedStar1,
                    isCommunityEntry = true,
                    lastUpdatedBy = uid
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, globalDocRef.path)
            Result.failure(e)
        }
    }

    /**
     * Liest einen fremden Nutzer-Beitrag per ID (wird in Security-Rule-Tests für Cross-User-Isolation geprüft).
     */
    suspend fun getUserContributionForUser(targetUserId: String, gameId: String): Result<UserGameContribution?> {
        val docId = sanitizeFirestoreId(gameId)
        val docRef = db.collection(AppConfig.COLLECTION_USERS)
            .document(targetUserId)
            .collection(AppConfig.SUBCOLLECTION_CONTRIBUTIONS)
            .document(docId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(
                    UserGameContribution::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }
}
