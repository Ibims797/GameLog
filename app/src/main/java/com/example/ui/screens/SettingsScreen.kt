package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.config.AppConfig
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppThemeStyle
import com.example.data.preferences.CustomThemeColors
import com.example.ui.auth.SteamOpenIdLoginDialog
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.CyberYellow
import com.example.ui.theme.FantasyBloodRed
import com.example.ui.theme.FantasyRuneGold
import com.example.ui.theme.NordicBronzeGold
import com.example.ui.theme.NordicFrostSilver
import com.example.ui.theme.NordicGlacierCyan
import com.example.ui.theme.PlatinumGlowPrimary
import com.example.ui.theme.SamuraiImperialGold
import com.example.ui.theme.SamuraiJade
import com.example.ui.theme.SamuraiSakuraRose
import com.example.ui.theme.SpaceIceCyan
import com.example.ui.theme.SpaceNebulaViolet
import com.example.ui.theme.SpaceStarWhite
import com.example.ui.theme.SynthPink
import com.example.ui.theme.SynthSunsetOrange
import com.example.ui.theme.SynthTurquoise
import com.example.ui.theme.WastelandAmber
import com.example.ui.theme.WastelandPipGreen
import com.example.ui.theme.WastelandRust
import com.example.ui.theme.colorToHexString
import com.example.ui.theme.parseHexColorSafe
import com.example.ui.viewmodel.AdminVaultState
import com.example.ui.viewmodel.SteamImportState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlin.math.roundToInt

/**
 * Tab 5: "Optionen / Einstellungen"
 * Features:
 * 1. Sprachauswahl mit automatischer Erkennung der Systemsprache beim ersten App-Start.
 * 2. Dynamisches Theme-System mit 7 handgefertigten Designs + vollem Custom-Theme-Editor (Farbwähler, RGB-Regler, Hex-Code).
 * 3. Steam-Login (OpenID 2.0) & Vollständiger Steam-ID Import für Owned Games und Live-Wishlist.
 * 4. Geschützter Cloud-Admin-Tresor (`/admin_vault/api_secrets`) exklusiv für `nico.schaefer.hn@gmail.com`.
 */
@Composable
fun SettingsScreen(
    selectedTheme: AppThemeStyle,
    onSelectTheme: (AppThemeStyle) -> Unit,
    customThemeColors: CustomThemeColors = CustomThemeColors(),
    onSaveCustomThemeColors: (CustomThemeColors) -> Unit = {},
    selectedLanguage: AppLanguage = AppLanguage.SYSTEM,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    linkedSteamId: String,
    steamImportState: SteamImportState,
    adminVaultState: AdminVaultState,
    onSaveSteamId: (String) -> Unit,
    onImportSteamLibrary: (String) -> Unit,
    onImportSteamWishlist: (String) -> Unit,
    onSaveAdminSecrets: (twitchClientId: String, twitchClientSecret: String, steamApiKey: String) -> Unit,
    onReloadAdminSecrets: () -> Unit,
    onCleanupDuplicates: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val strings = LocalAppStrings.current
    var steamIdInput by remember(linkedSteamId) { mutableStateOf(linkedSteamId) }
    var showSteamOpenIdWebView by remember { mutableStateOf(false) }

    // Custom Theme Editor State
    var customPrimaryHex by remember(customThemeColors.primaryHex) { mutableStateOf(customThemeColors.primaryHex) }
    var customSecondaryHex by remember(customThemeColors.secondaryHex) { mutableStateOf(customThemeColors.secondaryHex) }
    var customBackgroundHex by remember(customThemeColors.backgroundHex) { mutableStateOf(customThemeColors.backgroundHex) }
    var customSurfaceHex by remember(customThemeColors.surfaceHex) { mutableStateOf(customThemeColors.surfaceHex) }
    var activeColorSlot by remember { mutableIntStateOf(0) } // 0 = Primary, 1 = Secondary, 2 = Background, 3 = Surface
    var showCustomEditorExpanded by remember(selectedTheme) {
        mutableStateOf(selectedTheme == AppThemeStyle.CUSTOM_DESIGN)
    }

    var twitchClientIdInput by remember(adminVaultState.twitchClientId) {
        mutableStateOf(adminVaultState.twitchClientId.ifBlank { AppConfig.TWITCH_CLIENT_ID })
    }
    var twitchClientSecretInput by remember(adminVaultState.twitchClientSecret) {
        mutableStateOf(adminVaultState.twitchClientSecret.ifBlank { AppConfig.TWITCH_CLIENT_SECRET })
    }
    var steamApiKeyInput by remember(adminVaultState.steamApiKey) {
        mutableStateOf(adminVaultState.steamApiKey.ifBlank { AppConfig.STEAM_API_KEY })
    }
    var showSecretsPlain by remember { mutableStateOf(false) }

    LaunchedEffect(linkedSteamId) {
        if (linkedSteamId.isNotBlank() && steamIdInput.isBlank()) {
            steamIdInput = linkedSteamId
        }
    }

    val currentUser = Firebase.auth.currentUser
    val systemLangCode = remember { AppLanguage.getDeviceSystemLanguageCode().uppercase() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. SPRACHE / LANGUAGE (AUTOMATISCHE SYSTEMSPRACHE + MANUELLE AUSWAHL)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.languageSectionTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "${strings.languageSectionSubtitle} (${strings.systemLanguageDetectedLabel}: $systemLangCode)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val isSelected = selectedLanguage == lang
                            val labelText = if (lang == AppLanguage.SYSTEM) {
                                "${lang.flagEmoji} ${lang.nativeLabel} ($systemLangCode)"
                            } else {
                                "${lang.flagEmoji} ${lang.nativeLabel}"
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectLanguage(lang) },
                                label = {
                                    Text(
                                        text = labelText,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("lang_chip_${lang.id}")
                            )
                        }
                    }
                }
            }
        }

        // 1. DYNAMISCHES THEME-SYSTEM (7 PRESET-DESIGNS + EIGENES CUSTOM-DESIGN)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.themeSectionTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = strings.themeSectionSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AppThemeStyle.entries.forEach { style ->
                        val isSelected = style == selectedTheme
                        val previewColors = when (style) {
                            AppThemeStyle.CYBERPUNK_NEON -> listOf(CyberYellow, CyberCyan, CyberMagenta)
                            AppThemeStyle.DARK_FANTASY -> listOf(FantasyBloodRed, FantasyRuneGold, Color(0xFF2B2635))
                            AppThemeStyle.SCI_FI_SPACE -> listOf(SpaceStarWhite, SpaceIceCyan, SpaceNebulaViolet)
                            AppThemeStyle.RETRO_SYNTHWAVE -> listOf(SynthPink, SynthTurquoise, SynthSunsetOrange)
                            AppThemeStyle.NORDIC_VALHALLA -> listOf(NordicGlacierCyan, NordicBronzeGold, NordicFrostSilver)
                            AppThemeStyle.WASTELAND_PIPTECH -> listOf(WastelandPipGreen, WastelandAmber, WastelandRust)
                            AppThemeStyle.SAMURAI_SAKURA -> listOf(SamuraiSakuraRose, SamuraiImperialGold, SamuraiJade)
                            AppThemeStyle.CUSTOM_DESIGN -> listOf(
                                parseHexColorSafe(customPrimaryHex, Color(0xFF00E5FF)),
                                parseHexColorSafe(customSecondaryHex, Color(0xFFFF4081)),
                                parseHexColorSafe(customSurfaceHex, Color(0xFF181C2E))
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    onSelectTheme(style)
                                    if (style == AppThemeStyle.CUSTOM_DESIGN) {
                                        showCustomEditorExpanded = true
                                    }
                                }
                                .testTag("theme_option_${style.id}"),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            onSelectTheme(style)
                                            if (style == AppThemeStyle.CUSTOM_DESIGN) {
                                                showCustomEditorExpanded = true
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = style.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = style.subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    previewColors.forEach { swatchColor ->
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )

                    // CUSTOM DESIGN EDITOR (VORGEFERTIGTE ANPASSEN ODER KOMPLETT EIGENES DESIGN BAUEN)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCustomEditorExpanded = !showCustomEditorExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ColorLens,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = strings.customThemeEditorTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = strings.customThemeEditorSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showCustomEditorExpanded = !showCustomEditorExpanded },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showCustomEditorExpanded) "Einklappen" else "Anpassen",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            if (showCustomEditorExpanded) {
                                // Button: Aktuelles Vorlagen-Theme als Basis in den Custom-Editor laden
                                OutlinedButton(
                                    onClick = {
                                        val (p, s, bg, surf) = when (selectedTheme) {
                                            AppThemeStyle.CYBERPUNK_NEON -> listOf("#FCEE0A", "#00F0FF", "#08090F", "#121524")
                                            AppThemeStyle.DARK_FANTASY -> listOf("#C92A2A", "#E6B800", "#0E0C12", "#1A1622")
                                            AppThemeStyle.SCI_FI_SPACE -> listOf("#5CE1E6", "#9D4EDD", "#060814", "#10162B")
                                            AppThemeStyle.RETRO_SYNTHWAVE -> listOf("#FF2A85", "#00F5D4", "#140726", "#230E3D")
                                            AppThemeStyle.NORDIC_VALHALLA -> listOf("#38BDF8", "#F59E0B", "#0B131E", "#132032")
                                            AppThemeStyle.WASTELAND_PIPTECH -> listOf("#22C55E", "#FACC15", "#0B120C", "#142217")
                                            AppThemeStyle.SAMURAI_SAKURA -> listOf("#FB7185", "#FBBF24", "#130D14", "#221724")
                                            AppThemeStyle.CUSTOM_DESIGN -> listOf(
                                                customPrimaryHex,
                                                customSecondaryHex,
                                                customBackgroundHex,
                                                customSurfaceHex
                                            )
                                        }
                                        customPrimaryHex = p
                                        customSecondaryHex = s
                                        customBackgroundHex = bg
                                        customSurfaceHex = surf
                                        onSaveCustomThemeColors(
                                            CustomThemeColors(
                                                primaryHex = p,
                                                secondaryHex = s,
                                                backgroundHex = bg,
                                                surfaceHex = surf
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Vorlage aus aktuellem Design übernehmen & anpassen",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                // 4 Farb-Slots zur Auswahl
                                val slotLabels = listOf(
                                    strings.colorPrimaryLabel,
                                    strings.colorSecondaryLabel,
                                    strings.colorBackgroundLabel,
                                    strings.colorSurfaceLabel
                                )
                                val slotHexValues = listOf(
                                    customPrimaryHex,
                                    customSecondaryHex,
                                    customBackgroundHex,
                                    customSurfaceHex
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    slotLabels.forEachIndexed { index, label ->
                                        val isSlotSelected = activeColorSlot == index
                                        val slotColor = parseHexColorSafe(slotHexValues[index], Color.Cyan)
                                        FilterChip(
                                            selected = isSlotSelected,
                                            onClick = { activeColorSlot = index },
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(slotColor)
                                                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        )
                                    }
                                }

                                val currentActiveHex = when (activeColorSlot) {
                                    0 -> customPrimaryHex
                                    1 -> customSecondaryHex
                                    2 -> customBackgroundHex
                                    else -> customSurfaceHex
                                }
                                val currentActiveColor = parseHexColorSafe(currentActiveHex, Color(0xFF00E5FF))

                                val updateActiveHex: (String) -> Unit = { newHex ->
                                    when (activeColorSlot) {
                                        0 -> customPrimaryHex = newHex
                                        1 -> customSecondaryHex = newHex
                                        2 -> customBackgroundHex = newHex
                                        else -> customSurfaceHex = newHex
                                    }
                                }

                                // Schnell-Farbpalette (Swatches) passend zum gewählten Slot
                                val quickSwatches = if (activeColorSlot <= 1) {
                                    listOf(
                                        "#00E5FF", "#FF4081", "#FCEE0A", "#22C55E",
                                        "#A855F7", "#F97316", "#EF4444", "#38BDF8",
                                        "#FBBF24", "#14B8A6", "#EC4899", "#6366F1"
                                    )
                                } else {
                                    listOf(
                                        "#0B0E17", "#141A29", "#08090F", "#121524",
                                        "#0E0C12", "#1A1622", "#140726", "#230E3D",
                                        "#0B131E", "#132032", "#0B120C", "#1F242D"
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    quickSwatches.forEach { swatchHex ->
                                        val c = parseHexColorSafe(swatchHex, Color.Cyan)
                                        val isCurrentSwatch = currentActiveHex.equals(swatchHex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                                .border(
                                                    width = if (isCurrentSwatch) 2.5.dp else 1.dp,
                                                    color = if (isCurrentSwatch) Color.White else Color.White.copy(alpha = 0.35f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    updateActiveHex(swatchHex)
                                                }
                                        )
                                    }
                                }

                                // RGB Sliders für millimetergenaue Anpassung
                                val redInt = (currentActiveColor.red * 255f).roundToInt().coerceIn(0, 255)
                                val greenInt = (currentActiveColor.green * 255f).roundToInt().coerceIn(0, 255)
                                val blueInt = (currentActiveColor.blue * 255f).roundToInt().coerceIn(0, 255)

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "R: $redInt",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.width(46.dp)
                                        )
                                        Slider(
                                            value = redInt.toFloat(),
                                            onValueChange = { newR ->
                                                val updated = Color(
                                                    red = newR.roundToInt().coerceIn(0, 255) / 255f,
                                                    green = greenInt / 255f,
                                                    blue = blueInt / 255f
                                                )
                                                updateActiveHex(colorToHexString(updated))
                                            },
                                            valueRange = 0f..255f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "G: $greenInt",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.width(46.dp)
                                        )
                                        Slider(
                                            value = greenInt.toFloat(),
                                            onValueChange = { newG ->
                                                val updated = Color(
                                                    red = redInt / 255f,
                                                    green = newG.roundToInt().coerceIn(0, 255) / 255f,
                                                    blue = blueInt / 255f
                                                )
                                                updateActiveHex(colorToHexString(updated))
                                            },
                                            valueRange = 0f..255f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "B: $blueInt",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.width(46.dp)
                                        )
                                        Slider(
                                            value = blueInt.toFloat(),
                                            onValueChange = { newB ->
                                                val updated = Color(
                                                    red = redInt / 255f,
                                                    green = greenInt / 255f,
                                                    blue = newB.roundToInt().coerceIn(0, 255) / 255f
                                                )
                                                updateActiveHex(colorToHexString(updated))
                                            },
                                            valueRange = 0f..255f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // Direkte Hex-Eingabe + Live-Farbvorschau
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(currentActiveColor)
                                            .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    )
                                    OutlinedTextField(
                                        value = currentActiveHex,
                                        onValueChange = { updateActiveHex(it) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        label = { Text("Hex-Farbcode (#RRGGBB)") }
                                    )
                                }

                                Button(
                                    onClick = {
                                        onSaveCustomThemeColors(
                                            CustomThemeColors(
                                                primaryHex = customPrimaryHex,
                                                secondaryHex = customSecondaryHex,
                                                backgroundHex = customBackgroundHex,
                                                surfaceHex = customSurfaceHex
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("apply_custom_theme_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Palette, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.applyCustomThemeButton,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. GESCHÜTZTER CLOUD-ADMIN-TRESOR (STRIKT EXKLUSIV FÜR nico.schaefer.hn@gmail.com)
        val isVerifiedAdmin = adminVaultState.isAdmin &&
            currentUser != null &&
            (currentUser.email.equals(AppConfig.ADMIN_EMAIL, ignoreCase = true) ||
                currentUser.providerData.any { it.email.equals(AppConfig.ADMIN_EMAIL, ignoreCase = true) }) &&
            currentUser.isEmailVerified

        if (isVerifiedAdmin) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = PlatinumGlowPrimary.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = PlatinumGlowPrimary
                                )
                                Column {
                                    Text(
                                        text = "ADMIN API-KEY CLOUD-TRESOR",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = PlatinumGlowPrimary
                                    )
                                    Text(
                                        text = "Exklusiver Zugriff für ${AppConfig.ADMIN_EMAIL}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = { showSecretsPlain = !showSecretsPlain }) {
                                    Icon(
                                        imageVector = if (showSecretsPlain) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = "Keys ein-/ausblenden"
                                    )
                                }
                                IconButton(onClick = onReloadAdminSecrets) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Aus Cloud neu laden"
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Zero-APK-Secrets Schutz aktiv: Im APK sind keinerlei API-Keys enthalten. Dein 32-stelliger Steam Web API Key wird für den offiziellen Endpunkt IPlayerService/GetOwnedGames/v1/ (inkl. include_appinfo=true & include_played_free_games=true) genutzt.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val visualTransform = if (showSecretsPlain) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        }

                        OutlinedTextField(
                            value = twitchClientIdInput,
                            onValueChange = { twitchClientIdInput = it },
                            label = { Text("Twitch Client ID (für IGDB API v4)") },
                            singleLine = true,
                            visualTransformation = visualTransform,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_twitch_client_id_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        OutlinedTextField(
                            value = twitchClientSecretInput,
                            onValueChange = { twitchClientSecretInput = it },
                            label = { Text("Twitch Client Secret (OAuth2)") },
                            singleLine = true,
                            visualTransformation = visualTransform,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_twitch_client_secret_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        OutlinedTextField(
                            value = steamApiKeyInput,
                            onValueChange = {
                                steamApiKeyInput = it
                                AppConfig.updateRuntimeAdminKeys(
                                    twitchClientId = twitchClientIdInput,
                                    twitchClientSecret = twitchClientSecretInput,
                                    steamApiKey = it
                                )
                            },
                            label = { Text("Steam Web API Key (32-stellig für GetOwnedGames/v1)") },
                            singleLine = true,
                            visualTransformation = visualTransform,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_steam_api_key_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Button(
                            onClick = {
                                onSaveAdminSecrets(
                                    twitchClientIdInput,
                                    twitchClientSecretInput,
                                    steamApiKeyInput
                                )
                            },
                            enabled = !adminVaultState.isSaving && !adminVaultState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_admin_vault_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (adminVaultState.isSaving || adminVaultState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Im geschützten Admin-Tresor speichern")
                            }
                        }

                        if (adminVaultState.statusMessage != null) {
                            Surface(
                                color = if (adminVaultState.isError) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = adminVaultState.statusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (adminVaultState.isError) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. STEAM WEB API IMPORT & STEAM OPENID LOGIN
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "STEAM BIBLIOTHEK & WISHLIST IMPORT",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = "Verknüpfe deine 64-Bit Steam-ID manuell oder über den offiziellen Steam-Login, um alle Owned Games über IPlayerService/GetOwnedGames/v1/ (inkl. Free-to-Play) und deine Wunschliste zu importieren.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = steamIdInput,
                        onValueChange = { steamIdInput = it },
                        label = { Text("SteamID64 (17-stellig) oder Steam-Profilname") },
                        placeholder = { Text("z.B. 76561197960435530") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_steam_id_input"),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSteamOpenIdWebView = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_steam_openid_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.SportsEsports, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Steam Login")
                        }

                        Button(
                            onClick = {
                                if (isVerifiedAdmin && steamApiKeyInput.isNotBlank()) {
                                    AppConfig.updateRuntimeAdminKeys(
                                        twitchClientId = twitchClientIdInput,
                                        twitchClientSecret = twitchClientSecretInput,
                                        steamApiKey = steamApiKeyInput
                                    )
                                    onSaveAdminSecrets(
                                        twitchClientIdInput,
                                        twitchClientSecretInput,
                                        steamApiKeyInput
                                    )
                                }
                                onSaveSteamId(steamIdInput)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("settings_save_steam_id_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Steam-ID speichern")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isVerifiedAdmin && steamApiKeyInput.isNotBlank()) {
                                    AppConfig.updateRuntimeAdminKeys(
                                        twitchClientId = twitchClientIdInput,
                                        twitchClientSecret = twitchClientSecretInput,
                                        steamApiKey = steamApiKeyInput
                                    )
                                    onSaveAdminSecrets(
                                        twitchClientIdInput,
                                        twitchClientSecretInput,
                                        steamApiKeyInput
                                    )
                                }
                                onImportSteamLibrary(steamIdInput)
                            },
                            enabled = !steamImportState.isImportingLibrary && !steamImportState.isImportingWishlist,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("import_steam_library_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (steamImportState.isImportingLibrary) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Owned Games Import")
                            }
                        }

                        OutlinedButton(
                            onClick = { onImportSteamWishlist(steamIdInput) },
                            enabled = !steamImportState.isImportingLibrary && !steamImportState.isImportingWishlist,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("import_steam_wishlist_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (steamImportState.isImportingWishlist) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wishlist Import")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onCleanupDuplicates,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cleanup_duplicates_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bibliothek bereinigen (Duplikate & DLCs entfernen)")
                    }

                    if (steamImportState.statusMessage != null) {
                        Surface(
                            color = if (steamImportState.isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = steamImportState.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (steamImportState.isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. SICHERHEITS-ARCHITEKTUR & ACCOUNT STATUS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "ACCOUNT & SYNCHRONISATIONS-MODUS",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val isVerifiedAdminStatus = adminVaultState.isAdmin &&
                        currentUser != null &&
                        currentUser.email.equals(AppConfig.ADMIN_EMAIL, ignoreCase = true) &&
                        currentUser.isEmailVerified

                    if (isVerifiedAdminStatus) {
                        val twitchConfigured = AppConfig.TWITCH_CLIENT_ID.isNotBlank() && AppConfig.TWITCH_CLIENT_SECRET.isNotBlank()
                        Text(
                            text = "• Admin-Konto aktiv (${AppConfig.ADMIN_EMAIL})\n" +
                                "• IGDB API v4 Status: ${if (twitchConfigured) "Aktiv im RAM (Admin-Tresor)" else "Steam-Store Fallback aktiv"}\n" +
                                "• Steam API-Key Status: ${if (AppConfig.STEAM_API_KEY.isNotBlank()) "Aktiv im RAM (Admin-Tresor)" else "Öffentlicher Steam-Community-Sync aktiv"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = if (currentUser != null) {
                                "• Cloud-Modus aktiv: Bewertungen, Empfehlungen & 100%-Runs werden live mit dem globalen Community-Ranking synchronisiert."
                            } else {
                                "• Offline / Nur-Steam Modus aktiv: Deine Spielebibliothek, Wunschliste, Errungenschaften & Notizen werden lokal gespeichert. Für Sterne-Bewertungen & Community-Ranking bitte mit Google anmelden."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = when {
                                            currentUser?.displayName != null -> currentUser.displayName!!
                                            linkedSteamId.isNotBlank() -> "Steam-Spieler ($linkedSteamId)"
                                            else -> "Offline Spieler-Profil"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when {
                                            currentUser?.email != null -> "${currentUser.email} (Google Cloud aktiv)"
                                            linkedSteamId.isNotBlank() -> "Nur mit Steam angemeldet • Bewertungen erfordern Google-Login"
                                            else -> "Offline-Modus • Für Bewertungen mit Google anmelden"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("sign_out_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Konto wechseln / Abmelden",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (currentUser != null) "Sign Out" else "Login")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSteamOpenIdWebView) {
        SteamOpenIdLoginDialog(
            onDismiss = { showSteamOpenIdWebView = false },
            onSteamIdVerified = { verifiedId ->
                steamIdInput = verifiedId
                onSaveSteamId(verifiedId)
                showSteamOpenIdWebView = false
            }
        )
    }
}
