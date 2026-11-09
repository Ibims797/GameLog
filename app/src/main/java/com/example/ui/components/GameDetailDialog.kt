package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.LocalGame
import com.example.data.remote.SteamAchievement
import com.example.ui.theme.PlatinumGlowAccent
import com.example.ui.theme.PlatinumGlowPrimary
import com.example.ui.theme.PlatinumGlowSecondary
import com.example.ui.viewmodel.GameAchievementsState

private enum class AchievementFilter {
    ALL,
    UNLOCKED,
    LOCKED
}

/**
 * Detailansicht (Klick auf ein Spiel):
 * - Steam Errungenschaften (Achievements):
 *   Zeigt bereits erreichte (`isUnlocked == true`) und noch fehlende (`isUnlocked == false`) Errungenschaften.
 *   Versteckte / Spoiler-Errungenschaften sind wie bei Steam standardmäßig verborgen und werden erst durch
 *   Antippen ("Klicken zum Anzeigen") sichtbar gemacht!
 * - 5-Sterne-Rating & "Würdest du es weiterempfehlen?" Toggle:
 *   Nur aktiv, wenn der Nutzer mit Google angemeldet ist (`isGoogleSignedIn == true`).
 * - 100% Platin-Run Status & persönliche Notizen:
 *   Sowohl online (Cloud) als auch im Offline-/Steam-Modus verfügbar.
 */
@Composable
fun GameDetailDialog(
    game: LocalGame,
    achievementsState: GameAchievementsState = GameAchievementsState(),
    isGoogleSignedIn: Boolean = true,
    onRefreshAchievements: () -> Unit = {},
    onRequestGoogleLogin: () -> Unit = {},
    onDismiss: () -> Unit,
    onSaveAndSync: (rating: Int, note: String, isRecommended: Boolean, is100Percent: Boolean, isPlayed: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var rating by remember(game.id) { mutableIntStateOf(game.personalRating) }
    var note by remember(game.id) { mutableStateOf(game.personalNote) }
    var isRecommended by remember(game.id) { mutableStateOf(game.isRecommended) }
    var is100Percent by remember(game.id, game.is100Percent) { mutableStateOf(game.is100Percent) }
    var isPlayed by remember(game.id, game.isPlayed) { mutableStateOf(game.isPlayed) }

    var achievementFilter by remember(game.id) { mutableStateOf(AchievementFilter.ALL) }
    var revealedSpoilers by remember(game.id) { mutableStateOf(setOf<String>()) }
    var hideAllLockedAsSpoilers by remember(game.id) { mutableStateOf(false) }

    val allAchievements = achievementsState.achievements
    val unlockedCount = allAchievements.count { it.isUnlocked }
    val lockedCount = allAchievements.size - unlockedCount
    val filteredAchievements = remember(allAchievements, achievementFilter) {
        when (achievementFilter) {
            AchievementFilter.ALL -> allAchievements
            AchievementFilter.UNLOCKED -> allAchievements.filter { it.isUnlocked }
            AchievementFilter.LOCKED -> allAchievements.filter { !it.isUnlocked }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 760.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = if (is100Percent) 2.5.dp else 1.dp,
                    brush = if (is100Percent) {
                        Brush.linearGradient(
                            listOf(PlatinumGlowPrimary, PlatinumGlowSecondary, PlatinumGlowAccent)
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline)
                        )
                    },
                    shape = RoundedCornerShape(24.dp)
                ),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Cover-Bild
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (game.coverImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = game.coverImageUrl,
                            contentDescription = game.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (is100Percent) PlatinumGlowPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "ID: ${game.id}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (game.playtimeHours > 0) {
                                Text(
                                    text = "• ${game.playtimeHours} Std. Spielzeit",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Button für den "100% Run" (Platin / Gold Highlight)
                    Button(
                        onClick = {
                            is100Percent = !is100Percent
                            if (is100Percent) isPlayed = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("detail_100_percent_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (is100Percent) {
                                PlatinumGlowPrimary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (is100Percent) {
                                Color(0xFF1A1300)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (is100Percent) {
                                "100% PLATIN RUN ABGESCHLOSSEN (AKTIV)"
                            } else {
                                "Als 100% Platin-Run markieren"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. STEAM ERRUNGENSCHAFTEN (ACHIEVEMENTS) MIT SPOILER-SCHUTZ
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("achievements_section_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = PlatinumGlowPrimary
                                    )
                                    Column {
                                        Text(
                                            text = "STEAM ERRUNGENSCHAFTEN",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (allAchievements.isNotEmpty()) {
                                            val pct = (unlockedCount * 100) / allAchievements.size.coerceAtLeast(1)
                                            Text(
                                                text = "$unlockedCount von ${allAchievements.size} erreicht ($pct%) • $lockedCount fehlen noch",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Text(
                                                text = "Freigeschaltete & fehlende Erfolge (mit Spoiler-Schutz)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = onRefreshAchievements,
                                    enabled = !achievementsState.isLoading,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    if (achievementsState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Errungenschaften aktualisieren",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (allAchievements.isNotEmpty()) {
                                val progress = unlockedCount.toFloat() / allAchievements.size.coerceAtLeast(1).toFloat()
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (progress >= 1f) PlatinumGlowPrimary else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )

                                // Filter-Chips: Alle | Schon erreicht | Fehlt noch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = achievementFilter == AchievementFilter.ALL,
                                        onClick = { achievementFilter = AchievementFilter.ALL },
                                        label = { Text("Alle (${allAchievements.size})") }
                                    )
                                    FilterChip(
                                        selected = achievementFilter == AchievementFilter.UNLOCKED,
                                        onClick = { achievementFilter = AchievementFilter.UNLOCKED },
                                        label = { Text("Erreicht ($unlockedCount)") }
                                    )
                                    FilterChip(
                                        selected = achievementFilter == AchievementFilter.LOCKED,
                                        onClick = { achievementFilter = AchievementFilter.LOCKED },
                                        label = { Text("Fehlt ($lockedCount)") }
                                    )
                                }

                                // Optionale Zusatz-Sicherheit: Alle noch offenen Erfolge als Spoiler verbergen
                                if (lockedCount > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "🔒 Versteckte Spoiler sind geschützt (antippen zum Aufdecken)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { hideAllLockedAsSpoilers = !hideAllLockedAsSpoilers }
                                        ) {
                                            Text(
                                                text = if (hideAllLockedAsSpoilers) "Standard-Spoiler" else "Alle offenen tarnen",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filteredAchievements.forEach { ach ->
                                        val isProtectedSpoiler = !ach.isUnlocked &&
                                            (ach.isHiddenSpoiler || hideAllLockedAsSpoilers)
                                        val isRevealed = ach.apiName in revealedSpoilers

                                        AchievementRowItem(
                                            achievement = ach,
                                            isProtectedSpoiler = isProtectedSpoiler,
                                            isSpoilerRevealed = isRevealed,
                                            onToggleRevealSpoiler = {
                                                revealedSpoilers = if (isRevealed) {
                                                    revealedSpoilers - ach.apiName
                                                } else {
                                                    revealedSpoilers + ach.apiName
                                                }
                                            }
                                        )
                                    }
                                }
                            } else if (achievementsState.isLoading) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Lade Errungenschaften von Steam...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = achievementsState.errorMessage
                                        ?: "Für dieses Spiel sind aktuell keine Steam-Errungenschaften hinterlegt.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 3. Gespielt-Status FilterChip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Story durchgespielt / gespielt:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FilterChip(
                            selected = isPlayed,
                            onClick = { isPlayed = !isPlayed },
                            label = { Text(if (isPlayed) "Gespielt ✓" else "Noch ungespielt") },
                            leadingIcon = if (isPlayed) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null
                        )
                    }

                    // 4. CLOUD-BEWERTUNG & EMPFEHLUNG (NUR MIT GOOGLE-ANMELDUNG AKTIV!)
                    if (!isGoogleSignedIn) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Bewertungen & Empfehlungen nur mit Google-Login",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Du nutzt aktuell den Steam-/Offline-Modus. Deine Bibliothek, Errungenschaften, 100%-Runs und Notizen funktionieren komplett lokal. Um Sterne-Bewertungen und Empfehlungen für das globale Community-Ranking abzugeben, melde dich bitte mit Google an.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                OutlinedButton(
                                    onClick = onRequestGoogleLogin,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Zu Google-Login wechseln")
                                }
                            }
                        }
                    }

                    // 5. Interaktives 5-Sterne-Rating (Gesperrt falls nicht mit Google angemeldet)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isGoogleSignedIn) 1f else 0.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isGoogleSignedIn) {
                                    "Persönliche Bewertung ($rating / 5 Sterne)"
                                } else {
                                    "Persönliche Bewertung (Erfordert Google-Anmeldung)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (star in 1..5) {
                                    IconButton(
                                        onClick = {
                                            if (isGoogleSignedIn) {
                                                rating = if (rating == star) 0 else star
                                                if (rating > 0) isPlayed = true
                                            }
                                        },
                                        enabled = isGoogleSignedIn,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("star_rating_button_$star")
                                    ) {
                                        Icon(
                                            imageVector = if (star <= rating) {
                                                Icons.Filled.Star
                                            } else {
                                                Icons.Outlined.StarOutline
                                            },
                                            contentDescription = "$star Sterne",
                                            tint = if (star <= rating) {
                                                PlatinumGlowPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. Toggle für "Würdest du es weiterempfehlen?" (Gesperrt falls nicht mit Google angemeldet)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isGoogleSignedIn) 1f else 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = null,
                                    tint = if (isRecommended && isGoogleSignedIn) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Column {
                                    Text(
                                        text = "Würdest du es weiterempfehlen?",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isGoogleSignedIn) {
                                            "Zählt für das globale Community-Ranking"
                                        } else {
                                            "Nur mit Google-Anmeldung verfügbar"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isRecommended,
                                onCheckedChange = { if (isGoogleSignedIn) isRecommended = it },
                                enabled = isGoogleSignedIn,
                                modifier = Modifier.testTag("recommend_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    // 7. Textfeld für persönliche Notizen (funktioniert online & offline!)
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Persönliche Story-Notizen & Gedanken zum Run") },
                        placeholder = { Text("Lieblingskapitel, Entscheidungen, Bosskämpfe, Platin-Tipps...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("personal_note_input"),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 5
                    )

                    // 8. Speichern + Löschen Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("delete_game_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Spiel löschen",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                onSaveAndSync(rating, note, isRecommended, is100Percent, isPlayed)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("save_game_details_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = if (isGoogleSignedIn) Icons.Default.CloudUpload else Icons.Default.Save,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isGoogleSignedIn) "Speichern & Cloud-Sync" else "Lokal speichern (Offline)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Einzelne Errungenschafts-Zeile mit echtem Steam-Spoiler-Schutz:
 * - Freigeschaltete Erfolge (`isUnlocked == true`) werden farbig mit Häkchen angezeigt.
 * - Noch fehlende Erfolge (`isUnlocked == false`), die als Spoiler eingestuft sind (`isProtectedSpoiler == true`),
 *   verbergen ihren Titel, ihr Icon und ihre Beschreibung hinter einer klickbaren Spoiler-Abdeckung.
 * - Erst durch Antippen (`onToggleRevealSpoiler`) wird der Spoiler aufgedeckt!
 */
@Composable
private fun AchievementRowItem(
    achievement: SteamAchievement,
    isProtectedSpoiler: Boolean,
    isSpoilerRevealed: Boolean,
    onToggleRevealSpoiler: () -> Unit
) {
    val isCurrentlyHidden = isProtectedSpoiler && !isSpoilerRevealed

    val containerColor = when {
        achievement.isUnlocked -> MaterialTheme.colorScheme.surface
        isCurrentlyHidden -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    }

    val borderColor = when {
        achievement.isUnlocked -> PlatinumGlowPrimary.copy(alpha = 0.55f)
        isCurrentlyHidden -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .then(
                if (isProtectedSpoiler) {
                    Modifier.clickable { onToggleRevealSpoiler() }
                } else {
                    Modifier
                }
            )
            .testTag("achievement_item_${achievement.apiName}"),
        color = containerColor,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Achievement Icon oder Spoiler-Schloss
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentlyHidden) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Versteckter Spoiler",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    val iconUrl = if (achievement.isUnlocked) {
                        achievement.unlockedIconUrl.ifBlank { achievement.lockedIconUrl }
                    } else {
                        achievement.lockedIconUrl.ifBlank { achievement.unlockedIconUrl }
                    }
                    if (iconUrl.isNotBlank()) {
                        AsyncImage(
                            model = iconUrl,
                            contentDescription = achievement.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (achievement.isUnlocked) 1f else 0.65f),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (achievement.isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (achievement.isUnlocked) PlatinumGlowPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Titel & Beschreibung (oder Spoiler-Schutz-Hinweis)
            Column(modifier = Modifier.weight(1f)) {
                if (isCurrentlyHidden) {
                    Text(
                        text = "Versteckte Errungenschaft (Spoiler)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Möglicher Story-Spoiler • Hier tippen, um Details anzuzeigen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (achievement.isUnlocked) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (achievement.description.isNotBlank()) {
                        Text(
                            text = achievement.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isProtectedSpoiler && isSpoilerRevealed) {
                        Text(
                            text = "👁️ Spoiler eingeblendet (Erneut tippen zum Verbergen)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Rechts: Status-Badge ("✓ Erreicht" vs. "Offen" / Spoiler-Auge)
            if (achievement.isUnlocked) {
                Surface(
                    color = PlatinumGlowPrimary.copy(alpha = 0.18f),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Freigeschaltet",
                        tint = PlatinumGlowPrimary,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
            } else if (isProtectedSpoiler) {
                Icon(
                    imageVector = if (isCurrentlyHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Spoiler umschalten",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Noch nicht freigeschaltet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
