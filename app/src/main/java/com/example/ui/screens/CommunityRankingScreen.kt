package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.cloud.GlobalGameStats
import com.example.data.local.LocalGame
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PlatinumGlowAccent
import com.example.ui.theme.PlatinumGlowPrimary
import com.example.ui.theme.PlatinumGlowSecondary
import com.example.ui.viewmodel.UiState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tab 4: "Community Ranking" (Kompakte, moderne Leaderboard-Ansicht aus `GlobalGameStats`).
 * - Schlanke, übersichtliche Zeilen statt klobiger Blöcke.
 * - Anonyme Gesamt-Statistik (5★, 4★, 3★, 2★, 1★ & 100% Runs) direkt oben im Ranking.
 * - Tipp auf ein Spiel öffnet die detaillierte anonyme Bewertungs- & 100%-Run-Statistik (komplett ohne Namen!).
 * - Direkter 1-Tap Wunschlisten-Sync (App + Steam Store).
 * - Exklusiver Admin-Löschbutton für `nico.schaefer.hn@gmail.com`.
 */
@Composable
fun CommunityRankingScreen(
    rankingsState: UiState<List<GlobalGameStats>>,
    isAdmin: Boolean = false,
    ownedGames: List<LocalGame> = emptyList(),
    wishlistGames: List<LocalGame> = emptyList(),
    onAddToWishlist: (GlobalGameStats) -> Unit = {},
    onDeleteRankingEntry: (GlobalGameStats) -> Unit = {}
) {
    val strings = LocalAppStrings.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedStatsWithRank by remember { mutableStateOf<Pair<Int, GlobalGameStats>?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("community_ranking_lazy_column"),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Kompaktes Header-Banner + Anonyme Gesamtstatistik + Suchleiste
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = strings.rankingTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = strings.rankingSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Live Cloud Sync",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (isAdmin) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = PlatinumGlowPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Admin aktiv: Papierkorb entfernt Einträge aus dem globalen Ranking",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PlatinumGlowPrimary
                                )
                            }
                        }
                    }
                }

                // Schlanke Suchleiste für das Ranking
                if (rankingsState is UiState.Success && rankingsState.data.size > 3) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = strings.rankingSearchPlaceholder,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Suche löschen",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ranking_search_input")
                    )
                }
            }
        }

        when (rankingsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Lade Community-Rangliste...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Cloud-Verbindungsfehler",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = rankingsState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            is UiState.Success -> {
                val allItems = rankingsState.data
                val q = searchQuery.trim().lowercase(Locale.ROOT)
                val filteredItems = if (q.isBlank()) {
                    allItems
                } else {
                    allItems.filter { it.title.lowercase(Locale.ROOT).contains(q) }
                }

                if (filteredItems.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = PlatinumGlowPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (allItems.isEmpty()) {
                                        "Noch keine bewerteten Spiele im Ranking"
                                    } else {
                                        strings.noGamesForSearchOrFilter
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                if (allItems.isEmpty()) {
                                    Text(
                                        text = "Spiele erscheinen im Ranking, sobald du ihnen eine Sterne-Bewertung (1–5) oder Empfehlung gibst.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(filteredItems, key = { _, stat -> stat.id }) { index, stat ->
                        val norm = normalizeTitle(stat.title)
                        val isOwned = ownedGames.any { it.id == stat.id || normalizeTitle(it.title) == norm }
                        val isOnWishlist = wishlistGames.any { it.id == stat.id || normalizeTitle(it.title) == norm }

                        CommunityRankingRow(
                            rank = index + 1,
                            stats = stat,
                            isAdmin = isAdmin,
                            isOwned = isOwned,
                            isOnWishlist = isOnWishlist,
                            onShowAnonymousStats = { selectedStatsWithRank = (index + 1) to stat },
                            onAddToWishlist = { onAddToWishlist(stat) },
                            onDelete = { onDeleteRankingEntry(stat) }
                        )
                    }
                }
            }
        }
    }

    // Detail-Popup für die anonyme Sterne-Verteilung (5★–1★) & 100%-Runs (erscheint NUR beim Klick auf ein Spiel!)
    selectedStatsWithRank?.let { (rank, gameStats) ->
        val norm = normalizeTitle(gameStats.title)
        val isOwned = ownedGames.any { it.id == gameStats.id || normalizeTitle(it.title) == norm }
        val isOnWishlist = wishlistGames.any { it.id == gameStats.id || normalizeTitle(it.title) == norm }
        AnonymousRankingStatsDialog(
            rank = rank,
            stats = gameStats,
            isOwned = isOwned,
            isOnWishlist = isOnWishlist,
            onAddToWishlist = { onAddToWishlist(gameStats) },
            onDismiss = { selectedStatsWithRank = null }
        )
    }
}

private fun normalizeTitle(raw: String): String {
    return raw.lowercase(Locale.ROOT)
        .replace("™", "")
        .replace("®", "")
        .replace(":", " ")
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * Schlanke, elegante Leaderboard-Zeile.
 * Beim Klick auf das Spiel öffnet sich das anonyme Statistik-Popup (5★–1★ & 100% Runs).
 */
@Composable
private fun CommunityRankingRow(
    rank: Int,
    stats: GlobalGameStats,
    isAdmin: Boolean,
    isOwned: Boolean,
    isOnWishlist: Boolean,
    onShowAnonymousStats: () -> Unit,
    onAddToWishlist: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isTopThree = rank <= 3

    val borderBrush = when (rank) {
        1 -> Brush.linearGradient(listOf(PlatinumGlowPrimary, PlatinumGlowSecondary))
        2 -> Brush.linearGradient(listOf(Color(0xFFC0C0C0), MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
        3 -> Brush.linearGradient(listOf(Color(0xFFCD7F32), MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
        else -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            )
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isTopThree) 1.2.dp else 0.8.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onShowAnonymousStats() }
            .testTag("community_rank_card_${stats.id}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isTopThree) 3.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Kompaktes Rang-Badge (#1, #2, #3...)
            Surface(
                modifier = Modifier.size(26.dp),
                shape = CircleShape,
                color = when (rank) {
                    1 -> PlatinumGlowPrimary
                    2 -> Color(0xFFC0C0C0)
                    3 -> Color(0xFFCD7F32)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rank <= 3) Color(0xFF121212) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 2. Schlankes Steam-Cover-Thumbnail (60x34dp im 16:9 Kapsel-Format)
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (stats.coverImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = stats.coverImageUrl,
                        contentDescription = stats.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 3. Titel & kompakte einzeilige Metrik-Leiste
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stats.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bewertung (Ø 4.8 • 12)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = PlatinumGlowPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", stats.averageRating),
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumGlowPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "(${stats.ratingCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Empfehlungen
                    if (stats.recommendCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${stats.recommendCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 100% Platin Runs
                    if (stats.total100PercentRuns > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = PlatinumGlowAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${stats.total100PercentRuns}×100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = PlatinumGlowAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 4. Kompakte Aktions-Buttons rechts (nebeneinander, flach & platzsparend)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    isOwned -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = strings.inLibraryBadge,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = strings.inLibraryBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    isOnWishlist -> {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = strings.onWishlistBadge,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = strings.onWishlistBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    else -> {
                        FilledTonalIconButton(
                            onClick = onAddToWishlist,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("ranking_add_wishlist_${stats.id}"),
                            shape = RoundedCornerShape(8.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = strings.addToWishlistShort,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (isAdmin) {
                    FilledTonalIconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("admin_delete_ranking_button_${stats.id}"),
                        shape = RoundedCornerShape(8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Aus Community-Ranking löschen",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detail-Modal beim Antippen eines Ranking-Spiels:
 * Zeigt die exakte anonyme Verteilung (5★, 4★, 3★, 2★, 1★) mit Fortschrittsbalken,
 * die Anzahl der 100% Platin-Runs und Weiterempfehlungen – garantiert ohne Spielernamen.
 */
@Composable
private fun AnonymousRankingStatsDialog(
    rank: Int,
    stats: GlobalGameStats,
    isOwned: Boolean,
    isOnWishlist: Boolean,
    onAddToWishlist: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val starCounts = remember(stats) { stats.resolvedStarCounts() }
    val totalStarVotes = starCounts.values.sum().coerceAtLeast(stats.ratingCount)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("anonymous_game_stats_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header mit Cover, Rang, Titel & Schließen-Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (stats.coverImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = stats.coverImageUrl,
                                contentDescription = stats.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Platz #$rank im Community-Ranking",
                            style = MaterialTheme.typography.labelSmall,
                            color = PlatinumGlowPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stats.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen"
                        )
                    }
                }

                // Anonymitäts-Garantie Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "100% Anonym: Es werden nur Gesamtzahlen ohne Spielernamen gespeichert.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 3 Haupt-KPIs: Ø Sterne, 100% Runs & Empfehlungen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ø ${String.format(Locale.US, "%.2f", stats.averageRating)} ★",
                                style = MaterialTheme.typography.titleMedium,
                                color = PlatinumGlowPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$totalStarVotes Bewertungen",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = PlatinumGlowAccent.copy(alpha = 0.14f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🏆 ${stats.total100PercentRuns}",
                                style = MaterialTheme.typography.titleMedium,
                                color = PlatinumGlowAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "100% Runs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "👍 ${stats.recommendCount}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Empfohlen",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // 5★ bis 1★ Aufschlüsselung mit Balken
                Text(
                    text = "ANONYME STERNE-VERTEILUNG (5★ BIS 1★)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (star in 5 downTo 1) {
                        val count = starCounts[star] ?: 0L
                        val fraction = if (totalStarVotes > 0L) {
                            (count.toFloat() / totalStarVotes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val percent = (fraction * 100f).roundToInt()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$star Sterne",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(56.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (fraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                when (star) {
                                                    5 -> PlatinumGlowPrimary
                                                    4 -> MaterialTheme.colorScheme.primary
                                                    3 -> MaterialTheme.colorScheme.secondary
                                                    2 -> Color(0xFFF59E0B)
                                                    else -> MaterialTheme.colorScheme.error
                                                }
                                            )
                                    )
                                }
                            }

                            Text(
                                text = "${count}× ($percent%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(68.dp)
                            )
                        }
                    }
                }

                // Wunschlisten-Aktion & Schließen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isOwned && !isOnWishlist) {
                        Button(
                            onClick = {
                                onAddToWishlist()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = strings.addToWishlistShort)
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Schließen")
                    }
                }
            }
        }
    }
}
