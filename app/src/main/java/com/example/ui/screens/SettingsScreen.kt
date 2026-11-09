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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.config.AppConfig
import com.example.data.preferences.AppThemeStyle
import com.example.ui.auth.SteamOpenIdLoginDialog
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.CyberYellow
import com.example.ui.theme.FantasyBloodRed
import com.example.ui.theme.FantasyRuneGold
import com.example.ui.theme.PlatinumGlowPrimary
import com.example.ui.theme.SpaceIceCyan
import com.example.ui.theme.SpaceNebulaViolet
import com.example.ui.theme.SpaceStarWhite
import com.example.ui.viewmodel.AdminVaultState
import com.example.ui.viewmodel.SteamImportState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Tab 4: "Einstellungen"
 * Features:
 * 1. Dynamisches Theme-System (Speicherung über DataStore Preferences).
 * 2. Steam-Login (OpenID 2.0) & Steam-ID Import für Owned Games und Wishlist.
 * 3. Geschützter Cloud-Admin-Tresor (`/admin_vault/api_secrets`):
 *    - KEINE API-Keys im APK oder in `BuildConfig`!
 *    - Durch Firestore Security Rules (`isAdmin()`) ausschließlich für den verifizierten Admin
 *      (`nico.schaefer.hn@gmail.com`) les- und schreibbar.
 */
@Composable
fun SettingsScreen(
    selectedTheme: AppThemeStyle,
    onSelectTheme: (AppThemeStyle) -> Unit,
    linkedSteamId: String,
    steamImportState: SteamImportState,
    adminVaultState: AdminVaultState,
    onSaveSteamId: (String) -> Unit,
    onImportSteamLibrary: (String) -> Unit,
    onImportSteamWishlist: (String) -> Unit,
    onSaveAdminSecrets: (twitchClientId: String, twitchClientSecret: String, steamApiKey: String) -> Unit,
    onReloadAdminSecrets: () -> Unit,
    onSignOut: () -> Unit
) {
    var steamIdInput by remember(linkedSteamId) { mutableStateOf(linkedSteamId) }
    var showSteamOpenIdWebView by remember { mutableStateOf(false) }

    var twitchClientIdInput by remember(adminVaultState.twitchClientId) {
        mutableStateOf(adminVaultState.twitchClientId)
    }
    var twitchClientSecretInput by remember(adminVaultState.twitchClientSecret) {
        mutableStateOf(adminVaultState.twitchClientSecret)
    }
    var steamApiKeyInput by remember(adminVaultState.steamApiKey) {
        mutableStateOf(adminVaultState.steamApiKey)
    }
    var showSecretsPlain by remember { mutableStateOf(false) }

    LaunchedEffect(linkedSteamId) {
        if (linkedSteamId.isNotBlank() && steamIdInput.isBlank()) {
            steamIdInput = linkedSteamId
        }
    }

    val currentUser = Firebase.auth.currentUser

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. DYNAMISCHES THEME-SYSTEM (DATASTORE PREFERENCES)
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
                            text = "DYNAMISCHES DESIGN-SYSTEM",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Wähle dein bevorzugtes UI-Theme. Deine Auswahl wird dauerhaft über Jetpack DataStore Preferences gespeichert.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AppThemeStyle.entries.forEach { style ->
                        val isSelected = style == selectedTheme
                        val previewColors = when (style) {
                            AppThemeStyle.CYBERPUNK_NEON -> listOf(CyberYellow, CyberCyan, CyberMagenta)
                            AppThemeStyle.DARK_FANTASY -> listOf(FantasyBloodRed, FantasyRuneGold, Color(0xFF2B2635))
                            AppThemeStyle.SCI_FI_SPACE -> listOf(SpaceStarWhite, SpaceIceCyan, SpaceNebulaViolet)
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
                                .clickable { onSelectTheme(style) }
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
                                        onClick = { onSelectTheme(style) }
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
                }
            }
        }

        // 2. GESCHÜTZTER CLOUD-ADMIN-TRESOR (STRIKT EXKLUSIV FÜR nico.schaefer.hn@gmail.com)
        val isVerifiedAdmin = adminVaultState.isAdmin &&
            currentUser != null &&
            currentUser.email.equals(AppConfig.ADMIN_EMAIL, ignoreCase = true) &&
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
                            text = "Zero-APK-Secrets Schutz aktiv: Im APK sind keinerlei API-Keys enthalten. Deine Schlüssel liegen geschützt in Firestore (/admin_vault/api_secrets) und können durch Server-Security-Rules von keinem anderen Nutzer ausgelesen werden.",
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
                            onValueChange = { steamApiKeyInput = it },
                            label = { Text("Steam Web API Key") },
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
                        text = "Verknüpfe deine 64-Bit Steam-ID manuell oder über den offiziellen Steam OpenID-Login, um deine Owned Games und Wunschliste direkt zu importieren.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = steamIdInput,
                        onValueChange = { steamIdInput = it },
                        label = { Text("SteamID64 oder Steam Vanity-Profilname") },
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
                            onClick = { onSaveSteamId(steamIdInput) },
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
                            onClick = { onImportSteamLibrary(steamIdInput) },
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
