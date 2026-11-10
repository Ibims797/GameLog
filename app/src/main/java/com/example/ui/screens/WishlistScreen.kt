package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalGame
import com.example.ui.components.GameListCard
import com.example.ui.theme.PlatinumGlowPrimary
import com.example.ui.viewmodel.SteamImportState

/**
 * Tab 2: "Wunschliste" (`isOwned == false`).
 * Zeigt vorgemerkte Spiele aus der IGDB/Steam-Suche oder dem Steam-Wishlist-Import
 * inklusive automatischem Live-Updater, Live-SteamDB-Preisen und Preisalarm-Filter.
 */
@Composable
fun WishlistScreen(
    wishlistGames: List<LocalGame>,
    linkedSteamId: String = "",
    steamImportState: SteamImportState = SteamImportState(),
    onOpenSearchForWishlist: () -> Unit,
    onNavigateToSteamWishlistImport: () -> Unit,
    onToggleLiveUpdater: (Boolean) -> Unit = {},
    onTriggerLiveSyncNow: () -> Unit = {},
    onRefreshPricesAndAlerts: () -> Unit = {},
    onSelectGame: (LocalGame) -> Unit,
    onQuickToggle100Percent: (LocalGame) -> Unit,
    onMoveToOwnedLibrary: (LocalGame) -> Unit
) {
    var filterOnlyAlerts by remember { mutableStateOf(false) }

    val activeAlertsCount = wishlistGames.count { it.targetPriceAlertEur > 0.0 }
    val triggeredAlertsCount = wishlistGames.count { it.isPriceAlertTriggered }

    val displayedGames = remember(wishlistGames, filterOnlyAlerts) {
        if (filterOnlyAlerts) {
            wishlistGames.filter { it.targetPriceAlertEur > 0.0 || it.isPriceAlertTriggered }
        } else {
            wishlistGames
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("wishlist_lazy_column"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MEINE WUNSCHLISTE",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "${wishlistGames.size} geplante Abenteuer • Tippe auf ein Spiel für SteamDB & Preisalarm",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onRefreshPricesAndAlerts,
                                    modifier = Modifier.testTag("wishlist_refresh_prices_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sell,
                                        contentDescription = "Steam-Preise & Preisalarme prüfen",
                                        tint = PlatinumGlowPrimary
                                    )
                                }
                                if (linkedSteamId.isNotBlank()) {
                                    IconButton(
                                        onClick = onTriggerLiveSyncNow,
                                        enabled = !steamImportState.isImportingWishlist,
                                        modifier = Modifier.testTag("wishlist_live_sync_now_button")
                                    ) {
                                        if (steamImportState.isImportingWishlist) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Steam-Wunschliste sofort prüfen",
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Filterleiste für Preisalarme
                        if (wishlistGames.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !filterOnlyAlerts,
                                    onClick = { filterOnlyAlerts = false },
                                    label = { Text("Alle (${wishlistGames.size})") }
                                )
                                FilterChip(
                                    selected = filterOnlyAlerts,
                                    onClick = { filterOnlyAlerts = !filterOnlyAlerts },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = if (triggeredAlertsCount > 0) Color(0xFF4ADE80) else PlatinumGlowPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            if (triggeredAlertsCount > 0) {
                                                "🔔 Preisalarme ($triggeredAlertsCount erreicht / $activeAlertsCount aktiv)"
                                            } else {
                                                "🔔 Preisalarme ($activeAlertsCount)"
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        // Live-Updater Steuerleiste bei verknüpfter Steam-ID
                        if (linkedSteamId.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            tint = if (steamImportState.isLiveUpdaterActive) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = if (steamImportState.isLiveUpdaterActive) {
                                                    "Steam Wishlist & Preisalarm Live-Updater aktiv"
                                                } else {
                                                    "Steam Wishlist Live-Updater pausiert"
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (steamImportState.lastLiveSyncTimeText.isNotBlank()) {
                                                    "Prüft live neue Wunschlisten-Spiele & Preisalarme • ${steamImportState.lastLiveSyncTimeText}"
                                                } else {
                                                    "Erkennt neu hinzugefügte Steam-Wishlist-Spiele & Rabatte automatisch"
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = steamImportState.isLiveUpdaterActive,
                                        onCheckedChange = onToggleLiveUpdater,
                                        modifier = Modifier.testTag("wishlist_live_updater_switch")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (displayedGames.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdded,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (filterOnlyAlerts) {
                                    "Noch keine Preisalarme auf der Wunschliste"
                                } else {
                                    "Deine Wunschliste ist leer"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (filterOnlyAlerts) {
                                    "Tippe auf ein beliebiges Spiel und wähle unter 'SteamDB & Preisalarm Tracker' z.B. 'Alarm auf aktuellen Preis setzen'."
                                } else {
                                    "Füge kommende Story-Highlights über die IGDB/Steam-Suche hinzu oder verknüpfe deine Steam-ID für den automatischen Live-Updater."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onOpenSearchForWishlist,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("empty_wishlist_search_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Spiel suchen",
                                        textAlign = TextAlign.Center
                                    )
                                }
                                OutlinedButton(
                                    onClick = if (linkedSteamId.isNotBlank()) {
                                        onTriggerLiveSyncNow
                                    } else {
                                        onNavigateToSteamWishlistImport
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("empty_wishlist_steam_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (linkedSteamId.isNotBlank()) "Jetzt laden" else "Steam Wishlist",
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(displayedGames, key = { it.id }) { game ->
                    GameListCard(
                        game = game,
                        onClick = { onSelectGame(game) },
                        onQuickToggle100Percent = { onQuickToggle100Percent(game) },
                        onMoveToLibrary = { onMoveToOwnedLibrary(game) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenSearchForWishlist,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("wishlist_add_game_fab"),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Zur Wunschliste hinzufügen")
                Text(
                    text = "Zur Wunschliste",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
