package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LocalGame
import com.example.ui.components.GameListCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PlatinumGlowPrimary
import com.example.ui.viewmodel.SteamImportState
import java.util.Locale

private enum class LibraryFilter {
    ALL,
    PLATINUM_100,
    PLAYED,
    PRICE_ALERTS
}

/**
 * Tab 1: "Meine Bibliothek" (`isOwned == true`).
 * Enthält eine Echtzeit-Suchleiste für die eigene Bibliothek (250+ Spiele),
 * Filter-Chips (Alle, 100% Platin, Gespielt, Preisalarme) und Duplikatschutz.
 */
@Composable
fun LibraryScreen(
    games: List<LocalGame>,
    linkedSteamId: String = "",
    steamImportState: SteamImportState = SteamImportState(),
    onOpenSearch: () -> Unit,
    onSyncSteamLibraryNow: () -> Unit = {},
    onOpenSteamWebLogin: () -> Unit = {},
    onRefreshPricesAndAlerts: () -> Unit = {},
    onCleanupDuplicates: () -> Unit = {},
    onNavigateToSettingsSteamImport: () -> Unit,
    onSelectGame: (LocalGame) -> Unit,
    onQuickToggle100Percent: (LocalGame) -> Unit
) {
    val strings = LocalAppStrings.current
    var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Zusätzlicher Schutzmechanismus: Zeigt Spiele in der UI garantiert dedupliziert an
    val distinctGames = remember(games) {
        val seen = mutableSetOf<String>()
        games.filter { game ->
            val norm = game.title.lowercase(Locale.ROOT)
                .replace("™", "")
                .replace("®", "")
                .replace(":", " ")
                .replace("-", " ")
                .trim()
            val key = if (norm.isNotBlank()) norm else game.id
            seen.add(key)
        }
    }

    val filteredGames = remember(distinctGames, activeFilter, searchQuery) {
        val byCategory = when (activeFilter) {
            LibraryFilter.ALL -> distinctGames
            LibraryFilter.PLATINUM_100 -> distinctGames.filter { it.is100Percent }
            LibraryFilter.PLAYED -> distinctGames.filter { it.isPlayed }
            LibraryFilter.PRICE_ALERTS -> distinctGames.filter { it.targetPriceAlertEur > 0.0 || it.isPriceAlertTriggered }
        }
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isBlank()) {
            byCategory
        } else {
            val q = trimmedQuery.lowercase(Locale.ROOT)
            byCategory.filter { game ->
                game.title.lowercase(Locale.ROOT).contains(q) ||
                    game.genres.lowercase(Locale.ROOT).contains(q) ||
                    game.personalNote.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    val totalGames = distinctGames.size
    val totalPlayed = distinctGames.count { it.isPlayed }
    val total100Runs = distinctGames.count { it.is100Percent }
    val totalAlerts = distinctGames.count { it.targetPriceAlertEur > 0.0 }
    val showFullLibraryUnlockCard = linkedSteamId.isNotBlank() &&
        (steamImportState.onlyPublicShowcaseWarning || (totalGames in 1..15 && !steamImportState.hasSteamWebSession))

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("library_lazy_column"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Header mit Statistiken
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner_1794173800351),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.50f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.libraryTitle,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = strings.librarySubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = onCleanupDuplicates,
                                        modifier = Modifier.testTag("library_cleanup_duplicates_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CleaningServices,
                                            contentDescription = "Bibliothek bereinigen / Duplikate entfernen",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = onRefreshPricesAndAlerts,
                                        modifier = Modifier.testTag("library_refresh_prices_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sell,
                                            contentDescription = "Steam-Preise & Preisalarme aktualisieren",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    if (linkedSteamId.isNotBlank()) {
                                        IconButton(
                                            onClick = onSyncSteamLibraryNow,
                                            enabled = !steamImportState.isImportingLibrary
                                        ) {
                                            if (steamImportState.isImportingLibrary) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Steam-Bibliothek neu synchronisieren",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatMiniPill(
                                    label = strings.statGames,
                                    value = totalGames.toString(),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatMiniPill(
                                    label = strings.statPlayed,
                                    value = totalPlayed.toString(),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatMiniPill(
                                    label = strings.stat100Runs,
                                    value = "🏆 $total100Runs",
                                    tint = PlatinumGlowPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Live Lade-Banner während des automatischen Steam-Bibliotheks-Imports
            if (steamImportState.isImportingLibrary) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Lade deine komplette Steam-Spielebibliothek automatisch...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // WICHTIGER HINWEIS-BANNER: Warum nur 6 Spiele gefunden wurden + 1-Tap Freischaltung für alle 250+ Spiele!
            if (showFullLibraryUnlockCard && !steamImportState.isImportingLibrary) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                color = PlatinumGlowPrimary.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        )
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
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = PlatinumGlowPrimary
                                )
                                Text(
                                    text = "Alle 250+ Steam-Spiele freischalten",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PlatinumGlowPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Logge dich einmal über den offiziellen Steam Web-Login ein oder stelle auf Steam unter 'Privatsphäre -> Spieldetails' auf 'Öffentlich'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onOpenSteamWebLogin,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("unlock_all_250_steam_games_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Steam Web-Login (Alle 250+ Spiele laden)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // NEU: Echtzeit-Suchleiste & Filter-Chips für die eigene Bibliothek
            if (distinctGames.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = strings.librarySearchPlaceholder,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.testTag("library_clear_search_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Suche löschen"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_search_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            FilterChip(
                                selected = activeFilter == LibraryFilter.ALL,
                                onClick = { activeFilter = LibraryFilter.ALL },
                                label = { Text("${strings.filterAll} ($totalGames)") }
                            )
                            FilterChip(
                                selected = activeFilter == LibraryFilter.PLATINUM_100,
                                onClick = { activeFilter = LibraryFilter.PLATINUM_100 },
                                label = { Text("${strings.filter100Platinum} ($total100Runs)") }
                            )
                            FilterChip(
                                selected = activeFilter == LibraryFilter.PLAYED,
                                onClick = { activeFilter = LibraryFilter.PLAYED },
                                label = { Text("${strings.filterPlayed} ($totalPlayed)") }
                            )
                            FilterChip(
                                selected = activeFilter == LibraryFilter.PRICE_ALERTS,
                                onClick = { activeFilter = LibraryFilter.PRICE_ALERTS },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text("${strings.filterPriceAlerts} ($totalAlerts)") }
                            )
                        }
                    }
                }
            }

            // Leerer Zustand
            if (filteredGames.isEmpty()) {
                item {
                    EmptyLibraryCard(
                        isFilterActive = distinctGames.isNotEmpty(),
                        linkedSteamId = linkedSteamId,
                        isImporting = steamImportState.isImportingLibrary,
                        statusMessage = steamImportState.statusMessage,
                        onResetFilterAndSearch = {
                            searchQuery = ""
                            activeFilter = LibraryFilter.ALL
                        },
                        onOpenSearch = onOpenSearch,
                        onSyncSteamNow = onSyncSteamLibraryNow,
                        onOpenSteamWebLogin = onOpenSteamWebLogin,
                        onNavigateToSteamImport = onNavigateToSettingsSteamImport
                    )
                }
            } else {
                items(filteredGames, key = { it.id }) { game ->
                    GameListCard(
                        game = game,
                        onClick = { onSelectGame(game) },
                        onQuickToggle100Percent = { onQuickToggle100Percent(game) }
                    )
                }
            }
        }

        // FAB zum Hinzufügen von Spielen via IGDB / Steam API
        FloatingActionButton(
            onClick = onOpenSearch,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("library_add_game_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = strings.addGameButton)
                Text(
                    text = strings.addGameButton,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatMiniPill(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    isFilterActive: Boolean,
    linkedSteamId: String,
    isImporting: Boolean,
    statusMessage: String?,
    onResetFilterAndSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onSyncSteamNow: () -> Unit,
    onOpenSteamWebLogin: () -> Unit,
    onNavigateToSteamImport: () -> Unit
) {
    val strings = LocalAppStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = if (isFilterActive) {
                    strings.noGamesForSearchOrFilter
                } else {
                    "Deine Bibliothek ist noch leer"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (isFilterActive) {
                OutlinedButton(
                    onClick = onResetFilterAndSearch,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(strings.resetSearchAndFilter)
                }
            } else {
                Text(
                    text = if (linkedSteamId.isNotBlank()) {
                        "Verknüpfte Steam-ID: $linkedSteamId • Tippe unten auf 'Steam Sync' oder 'Steam Web-Login'."
                    } else {
                        "Suche nach Story-Spielen über die IGDB/Steam-API oder melde dich per Steam an für den vollständigen Bibliotheks-Import."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (!statusMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("empty_library_search_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "IGDB / API Suche",
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = if (linkedSteamId.isNotBlank()) onSyncSteamNow else onNavigateToSteamImport,
                        enabled = !isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("empty_library_steam_import_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (linkedSteamId.isNotBlank()) "Steam Sync" else "Steam Import",
                            textAlign = TextAlign.Center
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenSteamWebLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Public, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Offizieller Steam Web-Login (250+ Spiele freischalten)")
                }
            }
        }
    }
}
