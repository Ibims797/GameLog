package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.LocalGame
import com.example.ui.theme.PlatinumGlowAccent
import com.example.ui.theme.PlatinumGlowPrimary
import com.example.ui.theme.PlatinumGlowSecondary

/**
 * Listen-Element für ein Spiel (`LocalGame`).
 * Features:
 * - Zeigt Cover-Bild via Coil (`AsyncImage`), Spieletitel, Bewertung und Status-Icons.
 * - **100% Run Highlighting**: Spiele mit `is100Percent == true` erhalten einen animierten,
 *   leuchtenden Platin/Gold-Glow-Effekt um das gesamte Listen-Element.
 */
@Composable
fun GameListCard(
    game: LocalGame,
    onClick: () -> Unit,
    onQuickToggle100Percent: () -> Unit,
    onMoveToLibrary: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "platinumGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val cardShape = RoundedCornerShape(18.dp)

    val glowModifier = if (game.is100Percent) {
        Modifier
            .drawBehind {
                // Äußerer leuchtender Platin/Gold-Schein um das Listen-Element
                val glowBrush = Brush.linearGradient(
                    colors = listOf(
                        PlatinumGlowPrimary.copy(alpha = glowAlpha * 0.55f),
                        PlatinumGlowSecondary.copy(alpha = glowAlpha * 0.45f),
                        PlatinumGlowAccent.copy(alpha = glowAlpha * 0.55f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                drawRoundRect(
                    brush = glowBrush,
                    cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                    style = Stroke(width = 8.dp.toPx())
                )
            }
            .border(
                width = 2.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        PlatinumGlowPrimary,
                        PlatinumGlowSecondary,
                        PlatinumGlowAccent,
                        PlatinumGlowPrimary
                    )
                ),
                shape = cardShape
            )
    } else {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            shape = cardShape
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(glowModifier)
            .clip(cardShape)
            .clickable(onClick = onClick)
            .testTag("game_item_card_${game.id}"),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (game.is100Percent) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (game.is100Percent) 10.dp else 3.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover-Bild (via Coil) mit Status-Badge
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (game.coverImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = game.coverImageUrl,
                        contentDescription = "Cover von ${game.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                }

                if (game.is100Percent) {
                    Surface(
                        color = Color(0xDD1A1400),
                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "100% Platin Run",
                                tint = PlatinumGlowPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "100%",
                                style = MaterialTheme.typography.labelMedium,
                                color = PlatinumGlowPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Titel, Bewertung & Status-Icons
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (game.is100Percent) PlatinumGlowPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val metaLine = buildList {
                        if (game.genres.isNotBlank()) add(game.genres)
                        if (game.releaseYear.isNotBlank()) add(game.releaseYear)
                        if (game.playtimeHours > 0) add("${game.playtimeHours}h")
                    }.joinToString(" • ")

                    if (metaLine.isNotBlank()) {
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 5-Sterne Anzeige
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (star in 1..5) {
                        Icon(
                            imageVector = if (star <= game.personalRating) {
                                Icons.Filled.Star
                            } else {
                                Icons.Outlined.StarOutline
                            },
                            contentDescription = null,
                            tint = if (star <= game.personalRating) {
                                PlatinumGlowPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (game.personalRating > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${game.personalRating}/5",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status-Pills (Gespielt, Empfohlen, 100% Platin)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadgePill(
                        icon = if (game.isOwned) Icons.Default.CheckCircle else Icons.Default.Bookmark,
                        label = if (!game.isOwned) "Wunschliste" else if (game.isPlayed) "Gespielt" else "Backlog",
                        tint = if (game.isPlayed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (game.isRecommended) {
                        StatusBadgePill(
                            icon = Icons.Default.ThumbUp,
                            label = "Empfohlen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Schnell-Aktion rechts (100% Trophy Toggle oder "In Bibliothek verschieben")
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FilledTonalIconButton(
                    onClick = onQuickToggle100Percent,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quick_100_button_${game.id}"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (game.is100Percent) {
                            PlatinumGlowPrimary.copy(alpha = 0.24f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "100% Run umschalten",
                        tint = if (game.is100Percent) {
                            PlatinumGlowPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                if (onMoveToLibrary != null && !game.isOwned) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FilledTonalIconButton(
                        onClick = onMoveToLibrary,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("move_to_library_button_${game.id}"),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCartCheckout,
                            contentDescription = "In Meine Bibliothek übernehmen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadgePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}
