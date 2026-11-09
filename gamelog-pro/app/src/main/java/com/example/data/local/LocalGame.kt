package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lokale Room-Entity für ein Spiel in "Meine Bibliothek" (`isOwned = true`)
 * oder auf der "Wunschliste" (`isOwned = false`).
 *
 * Die Datenbank startet komplett LEER (keine vorgefertigten Spiele).
 */
@Entity(tableName = "local_games")
data class LocalGame(
    @PrimaryKey
    val id: String, // Eindeutige API-ID (z.B. "rawg_3328" oder "steam_292030")
    val title: String,
    val coverImageUrl: String,
    val isPlayed: Boolean = false,
    val isOwned: Boolean = true, // true = Meine Bibliothek, false = Wunschliste
    val is100Percent: Boolean = false, // Platin/Gold 100% Run Status
    val personalRating: Int = 0, // 0 = Unbewertet, 1-5 Sterne
    val personalNote: String = "",
    val isRecommended: Boolean = false, // "Würdest du es weiterempfehlen?" Toggle
    val releaseYear: String = "",
    val genres: String = "",
    val playtimeHours: Int = 0,
    val achievementsJson: String = "", // Lokal gecachte Errungenschaften für den Offline-Modus
    val updatedAtEpoch: Long = System.currentTimeMillis()
)
