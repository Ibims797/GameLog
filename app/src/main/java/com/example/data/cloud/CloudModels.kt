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
    val star5Count: Long = 0L,
    val star4Count: Long = 0L,
    val star3Count: Long = 0L,
    val star2Count: Long = 0L,
    val star1Count: Long = 0L,
    val isCommunityEntry: Boolean = true,
    val lastUpdatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    /**
     * Liefert die anonyme Anzahl der 5★, 4★, 3★, 2★ und 1★ Bewertungen.
     * Falls ein älterer Cloud-Eintrag noch keine Einzelzähler gespeichert hat, aber `ratingCount > 0` besitzt,
     * wird die Verteilung exakt aus `ratingCount` und `ratingSum` / `averageRating` berechnet.
     */
    fun resolvedStarCounts(): Map<Int, Long> {
        val explicitTotal = star5Count + star4Count + star3Count + star2Count + star1Count
        if (explicitTotal > 0L || ratingCount <= 0L) {
            return mapOf(
                5 to star5Count.coerceAtLeast(0L),
                4 to star4Count.coerceAtLeast(0L),
                3 to star3Count.coerceAtLeast(0L),
                2 to star2Count.coerceAtLeast(0L),
                1 to star1Count.coerceAtLeast(0L)
            )
        }
        // Fallback für bestehende Einträge vor Einführung der Einzelzähler
        val count = ratingCount.coerceAtLeast(1L)
        val effectiveSum = if (ratingSum > 0L) {
            ratingSum
        } else {
            kotlin.math.round(averageRating * count.toDouble()).toLong()
        }.coerceIn(count, count * 5L)

        val baseStar = (effectiveSum / count).toInt().coerceIn(1, 5)
        val remainder = (effectiveSum % count).coerceAtLeast(0L)
        val result = mutableMapOf(5 to 0L, 4 to 0L, 3 to 0L, 2 to 0L, 1 to 0L)
        if (remainder == 0L || baseStar == 5) {
            result[baseStar] = count
        } else {
            val upperStar = (baseStar + 1).coerceAtMost(5)
            result[upperStar] = remainder
            result[baseStar] = (count - remainder).coerceAtLeast(0L)
        }
        return result
    }
}

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
