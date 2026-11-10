package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lokale Room-Entity für ein Spiel in "Meine Bibliothek" (`isOwned = true`)
 * oder auf der "Wunschliste" (`isOwned = false`).
 *
 * Enthält zusätzlich Live-Preisdaten (Steam / SteamDB) sowie einen konfigurierbaren Preisalarm.
 */
@Entity(tableName = "local_games")
data class LocalGame(
    @PrimaryKey
    val id: String, // Eindeutige API-ID (z.B. "igdb_1942" oder "steam_292030")
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
    val currentPriceCents: Int = -1, // -1 = unbekannt, 0 = Kostenlos, > 0 = Preis in Cent (z.B. 1999 = 19,99 €)
    val originalPriceCents: Int = -1,
    val discountPercent: Int = 0,
    val formattedPrice: String = "", // z.B. "19,99 €" oder "Kostenlos"
    val lowestHistoricalPrice: String = "", // Historischer Bestpreis (SteamDB / CheapShark)
    val targetPriceAlertEur: Double = 0.0, // > 0.0 = Aktiver Preisalarm-Zielpreis in Euro
    val isPriceAlertTriggered: Boolean = false, // true, sobald aktueller Preis <= Zielpreis
    val updatedAtEpoch: Long = System.currentTimeMillis()
)
