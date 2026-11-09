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
 * Synchronisiert lokale Bewertungen, "Empfohlen"-Tags und 100%-Runs atomar in die globale Community-Tabelle
 * und verwaltet den geschützten Admin-API-Key-Tresor (`/admin_vault/api_secrets`).
 */
class CloudGameRepository(
    private val db: FirebaseFirestore
) {
    // Convenience-Konstruktor: Verwendet zwingend R.string.firestore_database_id
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
     * Prüft anhand des aktuell angemeldeten Google-Kontos, ob der Nutzer der verifizierte Admin ist.
     */
    fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email.equals(AppConfig.ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    private fun sanitizeFirestoreId(rawId: String): String {
        return rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "unknown_game" }
    }

    /**
     * Lädt die geheimen API-Keys aus dem geschützten Cloud-Tresor (`/admin_vault/api_secrets`).
     * Firestore Security Rules blockieren diesen Aufruf für jeden Nicht-Admin mit `PERMISSION_DENIED`.
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
     * Nur der verifizierte Admin (`nico.schaefer.hn@gmail.com`) hat Schreibrechte in Firestore.
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
     * Verwendet den mit `firestore.rules` abgestimmten Filter `whereEqualTo("isCommunityEntry", true)`.
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
                    ).sortedWith(
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
     * Lädt alle bisherigen Cloud-Einträge des Nutzers einmalig (z.B. zum Wiederherstellen auf neuem Gerät).
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
     * 1. Speichert den persönlichen Stand unter `/users/{uid}/game_contributions/{gameId}`
     * 2. Berechnet die Differenz zum vorherigen Stand dieses Nutzers und aktualisiert
     *    `/global_game_stats/{gameId}` (averageRating, recommendCount, total100PercentRuns).
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
            val existingGlobalSnap = globalDocRef.get().await()

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

            // 1. User Contribution speichern
            if (existingUserSnap.exists()) {
                val updateUserMap = mapOf(
                    "title" to game.title.take(200).ifBlank { "Unbekanntes Spiel" },
                    "coverImageUrl" to game.coverImageUrl.take(1000),
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
                    "title" to game.title.take(200).ifBlank { "Unbekanntes Spiel" },
                    "coverImageUrl" to game.coverImageUrl.take(1000),
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

            // 2. GlobalGameStats berechnen & aktualisieren
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

            val updatedRatingSum = max(0L, currentRatingSum + ratingSumDelta)
            val updatedRatingCount = max(0L, currentRatingCount + ratingCountDelta)
            val updatedRecommendCount = max(0L, currentRecommendCount + recommendDelta)
            val updated100Runs = max(0L, current100Runs + runs100Delta)

            val rawAverage = if (updatedRatingCount > 0L) {
                updatedRatingSum.toDouble() / updatedRatingCount.toDouble()
            } else {
                0.0
            }
            val roundedAverage = ((rawAverage * 100.0).roundToInt() / 100.0).coerceIn(0.0, 5.0)
            val safeTitle = game.title.take(200).ifBlank { "Unbekanntes Spiel" }
            val safeCover = game.coverImageUrl.take(1000)

            if (existingGlobalSnap.exists()) {
                val updateGlobalMap = mapOf(
                    "title" to safeTitle,
                    "coverImageUrl" to safeCover,
                    "averageRating" to roundedAverage,
                    "ratingSum" to updatedRatingSum,
                    "ratingCount" to updatedRatingCount,
                    "recommendCount" to updatedRecommendCount,
                    "total100PercentRuns" to updated100Runs,
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
     * Liest einen fremden Nutzer-Beitrag per ID (wird u.a. in Security-Rule-Tests für Cross-User-Isolation geprüft).
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
