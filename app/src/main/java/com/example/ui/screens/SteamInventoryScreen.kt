package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.remote.SteamInventoryGameContext
import com.example.data.remote.SteamInventoryItem
import com.example.ui.theme.PlatinumGlowPrimary
import com.example.ui.viewmodel.SteamInventoryUiState

/**
 * Tab 4: "Steam-Inventar"
 * Zeigt das echte Steam-Inventar des Nutzers (`steamcommunity.com/inventory/{steamId64}/{appId}/{contextId}`)
 * für Steam Community (Sammelkarten, Hintergründe, Emoticons), Counter-Strike 2, Dota 2, Team Fortress 2,
 * Rust, PUBG oder eine beliebige eigene AppID inkl. Live-Marktpreisabfrage in Euro (`€`).
 */
@Composable
fun SteamInventoryScreen(
    linkedSteamId: String,
    hasSteamWebSession: Boolean,
    inventoryState: SteamInventoryUiState,
    onSelectContext: (SteamInventoryGameContext) -> Unit,
    onUpdateCustomInput: (String, String) -> Unit,
    onLoadCustomInventory: (Long, Long) -> Unit,
    onRefreshInventory: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onCheckMarketPrice: (SteamInventoryItem) -> Unit,
    onOpenSteamWebLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomAppIdBox by remember { mutableStateOf(false) }

    LaunchedEffect(linkedSteamId) {
        if (linkedSteamId.isNotBlank() && inventoryState.items.isEmpty() && !inventoryState.isLoading) {
            onRefreshInventory()
        }
    }

    val filteredItems = remember(inventoryState.items, inventoryState.searchQuery) {
        val q = inventoryState.searchQuery.trim()
        if (q.isBlank()) {
            inventoryState.items
        } else {
            inventoryState.items.filter { item ->
                item.name.contains(q, ignoreCase = true) ||
                    item.typeLabel.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Header-Karte mit Steam-Status & Schnell-Aktionen
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
            ),
            shape = RoundedCornerShape(18.dp)
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
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "STEAM-INVENTAR & MARKTPREISE",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (linkedSteamId.isNotBlank()) {
                                    "Profil: $linkedSteamId • ${inventoryState.items.sumOf { it.amount }} Gegenstände (${inventoryState.items.size} Stacks)"
                                } else {
                                    "Noch keine Steam-ID verknüpft"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefreshInventory,
                        enabled = !inventoryState.isLoading && linkedSteamId.isNotBlank(),
                        modifier = Modifier.testTag("refresh_inventory_button")
                    ) {
                        if (inventoryState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Inventar neu laden",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Spiel-Kategorien (Steam Community, CS2, Dota 2, TF2, Rust, PUBG, Eigene AppID)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SteamInventoryGameContext.entries.forEach { ctx ->
                        FilterChip(
                            selected = !showCustomAppIdBox && inventoryState.selectedContext == ctx,
                            onClick = {
                                showCustomAppIdBox = false
                                onSelectContext(ctx)
                            },
                            label = { Text(ctx.label) },
                            modifier = Modifier.testTag("inventory_context_chip_${ctx.appId}")
                        )
                    }
                    FilterChip(
                        selected = showCustomAppIdBox,
                        onClick = { showCustomAppIdBox = !showCustomAppIdBox },
                        label = { Text("Eigene AppID") }
                    )
                }

                if (showCustomAppIdBox) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inventoryState.customAppIdInput,
                            onValueChange = { onUpdateCustomInput(it, inventoryState.customContextIdInput) },
                            label = { Text("AppID (z.B. 730)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = inventoryState.customContextIdInput,
                            onValueChange = { onUpdateCustomInput(inventoryState.customAppIdInput, it) },
                            label = { Text("Context (2/6)") },
                            singleLine = true,
                            modifier = Modifier.width(110.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                val appId = inventoryState.customAppIdInput.trim().toLongOrNull() ?: 753L
                                val ctxId = inventoryState.customContextIdInput.trim().toLongOrNull() ?: 2L
                                onLoadCustomInventory(appId, ctxId)
                            },
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Laden")
                        }
                    }
                }

                if (!hasSteamWebSession || linkedSteamId.isBlank()) {
                    OutlinedButton(
                        onClick = onOpenSteamWebLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inventory_steam_web_login_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (linkedSteamId.isBlank()) {
                                "Mit Steam anmelden (Inventar & 250+ Spiele laden)"
                            } else {
                                "Steam Web-Login (Privates Inventar & alle 250+ Spiele freischalten)"
                            }
                        )
                    }
                }
            }
        }

        // Suchleiste innerhalb des Inventars
        if (inventoryState.items.isNotEmpty()) {
            OutlinedTextField(
                value = inventoryState.searchQuery,
                onValueChange = onUpdateSearchQuery,
                placeholder = { Text("Im Inventar suchen (Name, Sammelkarte, Skin, Seltenheit)...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (inventoryState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onUpdateSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Suche leeren")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_search_field"),
                shape = RoundedCornerShape(14.dp)
            )
        }

        // Inhalt: Loading, Fehler oder Item-Liste
        when {
            inventoryState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Lade ${inventoryState.selectedContext.label}-Inventar von Steam...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            inventoryState.errorMessage != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = inventoryState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = onOpenSteamWebLogin,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Public, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Über Steam Web-Login anmelden")
                        }
                    }
                }
            }

            filteredItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (inventoryState.items.isEmpty()) {
                                "Keine Gegenstände in '${inventoryState.selectedContext.label}' gefunden.\nWähle oben ein anderes Spiel (z.B. Steam Community, CS2, Dota 2, TF2)!"
                            } else {
                                "Keine Gegenstände für '${inventoryState.searchQuery}' gefunden."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("inventory_lazy_column"),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = filteredItems,
                        key = { "${it.appId}_${it.assetId}_${it.classId}" }
                    ) { item ->
                        val priceKey = "${item.appId}_${item.classId}"
                        val cachedPrice = inventoryState.itemPrices[priceKey]
                        val isLoadingPrice = priceKey in inventoryState.loadingPriceKeys

                        SteamInventoryItemCard(
                            item = item,
                            cachedMarketPrice = cachedPrice,
                            isLoadingMarketPrice = isLoadingPrice,
                            onCheckMarketPrice = { onCheckMarketPrice(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamInventoryItemCard(
    item: SteamInventoryItem,
    cachedMarketPrice: String?,
    isLoadingMarketPrice: Boolean,
    onCheckMarketPrice: () -> Unit
) {
    val context = LocalContext.current
    val rarityBorderColor = remember(item.rarityColorHex) {
        val hex = item.rarityColorHex.trim().removePrefix("#")
        if (hex.length == 6) {
            try {
                Color(android.graphics.Color.parseColor("#$hex"))
            } catch (_: Exception) {
                null
            }
        } else null
    } ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    val marketUrl = remember(item.appId, item.marketHashName) {
        if (item.marketHashName.isNotBlank()) {
            "https://steamcommunity.com/market/listings/${item.appId}/${Uri.encode(item.marketHashName)}"
        } else {
            "https://steamcommunity.com/market/"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, rarityBorderColor, RoundedCornerShape(16.dp))
            .testTag("inventory_item_card_${item.assetId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            val iconBoxSize = (maxWidth * 0.20f).coerceIn(64.dp, 84.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Item-Bild (mit ContentScale.Fit dynamisch eingepasst)
                Box(
                    modifier = Modifier
                        .size(iconBoxSize)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, rarityBorderColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.iconUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.iconUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.18f),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = item.iconUrl,
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (item.amount > 1) {
                        Surface(
                            color = Color(0xDD111827),
                            shape = RoundedCornerShape(topStart = 8.dp),
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Text(
                                text = "×${item.amount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PlatinumGlowPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.typeLabel.isNotBlank()) {
                        Text(
                            text = item.typeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.rarityColorHex.isNotBlank()) rarityBorderColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (item.isMarketable) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (item.isMarketable) "Handelbar im Markt" else if (item.isTradable) "Tauschbar" else "Accountgebunden",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.isMarketable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (cachedMarketPrice != null) {
                            Surface(
                                color = Color(0xFF14532D).copy(alpha = 0.85f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Markt: $cachedMarketPrice",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4ADE80),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.marketHashName.isNotBlank()) {
                        IconButton(
                            onClick = onCheckMarketPrice,
                            enabled = !isLoadingMarketPrice,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("check_item_price_button_${item.assetId}")
                        ) {
                            if (isLoadingMarketPrice) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sell,
                                    contentDescription = "Marktpreis prüfen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(marketUrl)))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Im Steam Markt öffnen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
