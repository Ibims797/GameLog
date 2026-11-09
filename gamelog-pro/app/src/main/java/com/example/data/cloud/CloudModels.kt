package com.example.data.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Cloud-Modell für `GlobalGameStats` in Firestore (`/global_game_stats/{gameId}`).
 * Aggregiert alle Bewertungen, Empfehlungen und 100%-Runs aller Community-Nutzer.
 */
@IgnoreExtraProperties
data class GlobalGameStats(
    @DocumentId val id: String = "",
    val title: String = "",
    val coverImageUrl: String = "",
    val averageRating: Double = 0.0,
    val ratingSum: Long = 0L,
    val ratingCount: Long = 0L,
    val recommendCount: Long = 0L,
    val total100PercentRuns: Long = 0L,
    val isCommunityEntry: Boolean = true,
    val lastUpdatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Cloud-Modell für den individuellen Beitrag eines Nutzers (`/users/{userId}/game_contributions/{gameId}`).
 * Dient sowohl als persönliches Cloud-Backup als auch zur präzisen Delta-Berechnung für `GlobalGameStats`.
 */
@IgnoreExtraProperties
data class UserGameContribution(
    @DocumentId val id: String = "",
    val userId: String = "",
    val gameId: String = "",
    val title: String = "",
    val coverImageUrl: String = "",
    val isPlayed: Boolean = false,
    val isOwned: Boolean = true,
    val is100Percent: Boolean = false,
    val isRecommended: Boolean = false,
    val personalRating: Long = 0L,
    val personalNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Cloud-Modell für den geschützten Admin-API-Key-Tresor (`/admin_vault/api_secrets`).
 * Durch Zero-Trust Firestore Security Rules ausschließlich vom verifizierten Admin
 * (`nico.schaefer.hn@gmail.com`) les- und schreibbar.
 * Dadurch befinden sich KEINERLEI API-Keys im APK oder in `BuildConfig`.
 */
@IgnoreExtraProperties
data class AdminApiSecrets(
    @DocumentId val id: String = "",
    val twitchClientId: String = "",
    val twitchClientSecret: String = "",
    val steamApiKey: String = "",
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
