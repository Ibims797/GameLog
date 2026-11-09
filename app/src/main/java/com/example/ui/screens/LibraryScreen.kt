package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.ui.theme.PlatinumGlowPrimary
import com.example.ui.viewmodel.SteamImportState

private enum class LibraryFilter {
    ALL,
    PLATINUM_100,
    PLAYED,
    UNPLAYED
}

/**
 * Tab 1: "Meine Bibliothek" (`isOwned == true`).
 * Startet komplett leer und lädt bei verknüpfter Steam-ID automatisch die eigene Steam-Spielebibliothek.
 */
@Composable
fun LibraryScreen(
    games: List<LocalGame>,
    linkedSteamId: String = "",
    steamImportState: SteamImportState = SteamImportState(),
    onOpenSearch: () -> Unit,
    onSyncSteamLibraryNow: () -> Unit = {},
    onNavigateToSettingsSteamImport: () -> Unit,
    onSelectGame: (LocalGame) -> Unit,
    onQuickToggle100Percent: (LocalGame) -> Unit
) {
    var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }

    val filteredGames = remember(games, activeFilter) {
        when (activeFilter) {
            LibraryFilter.ALL -> games
            LibraryFilter.PLATINUM_100 -> games.filter { it.is100Percent }
            LibraryFilter.PLAYED -> games.filter { it.isPlayed }
            LibraryFilter.UNPLAYED -> games.filter { !it.isPlayed }
        }
    }

    val totalGames = games.size
    val totalPlayed = games.count { it.isPlayed }
    val total100Runs = games.count { it.is100Percent }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("library_lazy_column"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Header mit Statistiken
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    shape = RoundedCornerShape(22.dp)
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
                                        colors = listOf(
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "MEINE BIBLIOTHEK",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Tippe auf ein Spiel für Errungenschaften, Notizen & 100% Run",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatMiniPill(
                                    label = "Spiele",
                                    value = totalGames.toString(),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatMiniPill(
                                    label = "Gespielt",
                                    value = totalPlayed.toString(),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatMiniPill(
                                    label = "100% Runs",
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Lade deine Steam-Spielebibliothek automatisch...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Filter-Leiste
            if (games.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            label = { Text("Alle ($totalGames)") }
                        )
                        FilterChip(
                            selected = activeFilter == LibraryFilter.PLATINUM_100,
                            onClick = { activeFilter = LibraryFilter.PLATINUM_100 },
                            label = { Text("100% Platin ($total100Runs)") }
                        )
                        FilterChip(
                            selected = activeFilter == LibraryFilter.PLAYED,
                            onClick = { activeFilter = LibraryFilter.PLAYED },
                            label = { Text("Gespielt ($totalPlayed)") }
                        )
                    }
                }
            }

            // Leerer Zustand
            if (filteredGames.isEmpty()) {
                item {
                    EmptyLibraryCard(
                        isFilterActive = games.isNotEmpty(),
                        linkedSteamId = linkedSteamId,
                        isImporting = steamImportState.isImportingLibrary,
                        statusMessage = steamImportState.statusMessage,
                        onOpenSearch = onOpenSearch,
                        onSyncSteamNow = onSyncSteamLibraryNow,
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
                Icon(imageVector = Icons.Default.Add, contentDescription = "Spiel suchen")
                Text(
                    text = "Spiel hinzufügen",
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
    onOpenSearch: () -> Unit,
    onSyncSteamNow: () -> Unit,
    onNavigateToSteamImport: () -> Unit
) {
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
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = if (isFilterActive) {
                    "Keine Spiele für diesen Filter"
                } else {
                    "Deine Bibliothek ist noch leer"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isFilterActive) {
                    "Wähle oben einen anderen Filter oder markiere ein Spiel mit dem 100%-Platin-Button."
                } else if (linkedSteamId.isNotBlank()) {
                    "Verknüpfte Steam-ID: $linkedSteamId • Tippe unten auf 'Steam synchronisieren' oder suche einzelne Spiele über IGDB/Steam."
                } else {
                    "Suche nach Story-Spielen über die IGDB/Steam-API oder verknüpfe deine Steam-ID für den vollständigen Bibliotheks-Import."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (!statusMessage.isNullOrBlank() && !isFilterActive) {
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
        }
    }
}
