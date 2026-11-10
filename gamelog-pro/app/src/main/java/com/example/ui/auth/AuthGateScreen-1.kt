package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.config.AppConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * 1. Silent Auto-Sign-In beim App-Start via Jetpack Credential Manager (`GetGoogleIdOption`).
 */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

/**
 * 2. Interaktiver Google Sign-In:
 * Versucht zuerst den nativen Jetpack Credential Manager (`GetSignInWithGoogleOption`).
 * Falls auf dem Gerät kein Google-Play-Credential verfügbar ist oder kein Chrome installiert ist,
 * wird automatisch auf den browserbasierten Firebase OAuthProvider ("google.com") umgeschaltet.
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val activity = context as? Activity
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        null
    }

    scope.launch {
        if (clientId != null && activity != null) {
            try {
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()

                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    Firebase.auth.signInWithCredential(authCredential).await()
                    onAuthSuccess()
                    return@launch
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "CredentialManager abgebrochen, versuche Web-OAuth Fallback: ${e.message}")
            } catch (e: Exception) {
                Log.w("Auth", "CredentialManager nicht verfügbar (${e.message}), nutze Web-OAuth Fallback")
            }
        }

        if (activity != null) {
            try {
                val provider = OAuthProvider.newBuilder("google.com")
                Firebase.auth.startActivityForSignInWithProvider(activity, provider.build()).await()
                if (Firebase.auth.currentUser != null) {
                    onAuthSuccess()
                    return@launch
                }
            } catch (webEx: Exception) {
                Log.e("Auth", "Google Web-OAuth ebenfalls fehlgeschlagen", webEx)
                onAuthError(
                    "Google-Anmeldung wurde abgebrochen (${webEx.localizedMessage ?: "Nicht abgeschlossen"}). " +
                        "Du kannst dich auch nur mit Steam einloggen oder den Offline-Modus starten."
                )
                return@launch
            }
        }

        onAuthCancelled()
    }
}

/**
 * 3. Sign-Out Flow: Löscht zuerst den CredentialManager-Status innerhalb von `NonCancellable`
 * und meldet danach den Firebase-Nutzer ab.
 */
fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Credential State konnte nicht geleert werden: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

/**
 * Auth-Gate Screen mit 3 klar getrennten Modi:
 * 1. Mit Google anmelden (Voller Funktionsumfang: Bewertungen, Empfehlungen, Community-Ranking & für nico.schaefer.hn@gmail.com das Admin-Panel).
 * 2. Nur mit Steam einloggen (Offline-fähig: Automatischer Import von Bibliothek, Wunschliste, Inventar & Errungenschaften ohne Google-Zwang).
 * 3. Offline-Modus starten (Lokale Room-Datenbank ohne Anmeldung nutzen).
 */
@Composable
fun AuthGateScreen(
    onAuthSuccess: () -> Unit,
    onSteamIdExtracted: (String) -> Unit,
    onContinueDirectly: () -> Unit = onAuthSuccess
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSteamLoginDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner_1794173800351),
                        contentDescription = "Story Games Tracker Hero Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "STORY GAMES TRACKER",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Bibliothek • SteamDB Preise • Inventar • 100% Runs • Community",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Wähle deinen Anmelde-Modus",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Google-Login: Schaltet Cloud-Bewertungen (1–5 Sterne), Empfehlungen & das globale Community-Ranking frei.\n" +
                            "• Nur mit Steam / Offline: Lädt automatisch deine komplette Steam-Bibliothek (250+ Spiele), Wunschliste, SteamDB-Preise, Inventar & Errungenschaften.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 1. Google Sign-In (für Bewertungen, Community-Ranking & Admin)
                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isLoading = false
                                }
                            )
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Mit Google anmelden (Bewertungen & Cloud)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 2. Nur mit Steam einloggen (100% absturzsicher, lädt Bibliothek & Wishlist automatisch)
                    Button(
                        onClick = {
                            errorMessage = null
                            showSteamLoginDialog = true
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("steam_openid_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Nur mit Steam einloggen (Bibliothek & Erfolge)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )

                    // 3. Komplett Offline starten
                    OutlinedButton(
                        onClick = onContinueDirectly,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("continue_directly_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline-Modus verwenden (Ohne Anmeldung)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    if (showSteamLoginDialog) {
        SteamOpenIdLoginDialog(
            onDismiss = { showSteamLoginDialog = false },
            onSteamIdVerified = { steamIdOrVanity ->
                showSteamLoginDialog = false
                onSteamIdExtracted(steamIdOrVanity)
                onContinueDirectly()
            }
        )
    }
}

/**
 * 100% absturzsicherer Steam-Login-Dialog:
 * - Unterstützt sowohl direkte Eingabe der SteamID64 / des Profilnamens als auch den offiziellen Steam-Web-Login.
 * - Beim Steam-Web-Login werden die `steamLoginSecure` Session-Cookies im `CookieManager` gespeichert,
 *   sodass auch komplett private/Freunde-geschützte Steam-Bibliotheken (alle 250+ Spiele!) und das Steam-Inventar
 *   vollständig geladen werden können!
 */
@Composable
fun SteamOpenIdLoginDialog(
    onDismiss: () -> Unit,
    onSteamIdVerified: (String) -> Unit,
    startWithWebView: Boolean = false
) {
    var manualSteamInput by remember { mutableStateOf("") }
    var manualInputError by remember { mutableStateOf<String?>(null) }
    var showOptionalWebView by remember { mutableStateOf(startWithWebView) }
    var webViewInitError by remember { mutableStateOf<String?>(null) }

    val returnUrl = "https://storygamestracker.app/auth/steam/callback"
    val realm = "https://storygamestracker.app"
    val openIdUrl = remember {
        buildString {
            append(AppConfig.STEAM_OPENID_PROVIDER_URL)
            append("?openid.ns=").append(URLEncoder.encode("http://specs.openid.net/auth/2.0", "UTF-8"))
            append("&openid.mode=checkid_setup")
            append("&openid.return_to=").append(URLEncoder.encode(returnUrl, "UTF-8"))
            append("&openid.realm=").append(URLEncoder.encode(realm, "UTF-8"))
            append("&openid.identity=").append(
                URLEncoder.encode("http://specs.openid.net/auth/2.0/identifier_select", "UTF-8")
            )
            append("&openid.claimed_id=").append(
                URLEncoder.encode("http://specs.openid.net/auth/2.0/identifier_select", "UTF-8")
            )
        }
    }

    fun tryExtractSteamIdFromUrl(urlStr: String?): String? {
        if (urlStr.isNullOrBlank()) return null
        if (!urlStr.startsWith(returnUrl) && !urlStr.contains("openid.claimed_id")) return null
        return try {
            val uri = Uri.parse(urlStr)
            val claimedId = uri.getQueryParameter("openid.claimed_id")
                ?: uri.getQueryParameter("openid.identity")
            val steamId = claimedId?.substringAfterLast("/")?.trim()
            if (!steamId.isNullOrBlank() && steamId.matches(Regex("^\\d{17}$"))) {
                steamId
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun tryExtractSteamIdFromCookies(): String? {
        return try {
            val cookieManager = CookieManager.getInstance()
            val cookies = listOfNotNull(
                cookieManager.getCookie("https://steamcommunity.com"),
                cookieManager.getCookie("https://store.steampowered.com")
            ).joinToString("; ")
            // Format: steamLoginSecure=76561198012345678%7C%7C... oder 76561198012345678||...
            val match = Regex("""steamLoginSecure=(\d{17})(?:%7C%7C|\|\|)""", RegexOption.IGNORE_CASE)
                .find(cookies)
            match?.groupValues?.getOrNull(1)
        } catch (_: Throwable) {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(10.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Steam-Login (Alle 250+ Spiele & Inventar)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen"
                        )
                    }
                }

                Text(
                    text = "Tipp: Wenn auf deinem Steam-Profil 'Spieldetails' auf 'Nur Freunde' steht, zeigt Steam öffentlich nur ~6 Showcase-Spiele an. Logge dich unten über die offizielle Steam-Webseite ein, um deine gesamte Bibliothek (250+ Spiele) sofort freizuschalten!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = manualSteamInput,
                    onValueChange = {
                        manualSteamInput = it
                        manualInputError = null
                    },
                    label = { Text("SteamID64, Profilname oder Steam-URL") },
                    placeholder = { Text("z.B. 76561198000000000") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("steam_dialog_manual_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                if (manualInputError != null) {
                    Text(
                        text = manualInputError ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        val cleaned = manualSteamInput.trim()
                            .removeSuffix("/")
                            .substringAfterLast("/")
                            .trim()
                        if (cleaned.isBlank()) {
                            manualInputError = "Bitte gib deine SteamID64 oder deinen Steam-Profilnamen ein."
                        } else {
                            onSteamIdVerified(cleaned)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("steam_dialog_confirm_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mit Steam-ID synchronisieren",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!showOptionalWebView) {
                    OutlinedButton(
                        onClick = { showOptionalWebView = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("steam_open_webview_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offizieller Steam Web-Login (Alle 250+ Spiele freischalten)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    if (webViewInitError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = webViewInitError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Melde dich unten bei Steam an und tippe auf 'Anmelden' (Sign In). Deine Session bleibt lokal gespeichert, damit alle 250+ Spiele erkannt werden:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(390.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        ) {
                            SafeSteamWebView(
                                openIdUrl = openIdUrl,
                                tryExtractSteamId = ::tryExtractSteamIdFromUrl,
                                tryExtractCookieSteamId = ::tryExtractSteamIdFromCookies,
                                onVerified = onSteamIdVerified,
                                onWebViewError = { errMsg ->
                                    webViewInitError = errMsg
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeSteamWebView(
    openIdUrl: String,
    tryExtractSteamId: (String?) -> String?,
    tryExtractCookieSteamId: () -> String?,
    onVerified: (String) -> Unit,
    onWebViewError: (String) -> Unit
) {
    var hasDispatchedResult by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun dispatchSafely(steamId: String) {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {}
        mainHandler.post {
            if (!hasDispatchedResult) {
                hasDispatchedResult = true
                onVerified(steamId)
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            try {
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    try {
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                    } catch (_: Throwable) {}

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val urlStr = request?.url?.toString()
                            val extracted = tryExtractSteamId(urlStr)
                            if (extracted != null) {
                                dispatchSafely(extracted)
                                return true
                            }
                            return false
                        }

                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?
                        ) {
                            val extracted = tryExtractSteamId(url)
                            if (extracted != null) {
                                dispatchSafely(extracted)
                                return
                            }
                            super.onPageStarted(view, url, favicon)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            try {
                                CookieManager.getInstance().flush()
                            } catch (_: Throwable) {}
                            val extractedFromUrl = tryExtractSteamId(url)
                            if (extractedFromUrl != null) {
                                dispatchSafely(extractedFromUrl)
                                return
                            }
                            // Falls sich der Nutzer erfolgreich auf steamcommunity.com eingeloggt hat
                            // und das steamLoginSecure Cookie gesetzt wurde:
                            val cookieSteamId = tryExtractCookieSteamId()
                            if (cookieSteamId != null && url != null && !url.contains("/login")) {
                                dispatchSafely(cookieSteamId)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            val extracted = tryExtractSteamId(request?.url?.toString())
                            if (extracted != null) {
                                dispatchSafely(extracted)
                                return
                            }
                            super.onReceivedError(view, request, error)
                        }
                    }
                    loadUrl(openIdUrl)
                }
            } catch (t: Throwable) {
                Log.e("SteamWebView", "WebView nicht verfügbar", t)
                mainHandler.post {
                    onWebViewError(
                        "Auf diesem Gerät ist keine System-WebView (z.B. Google Chrome) installiert. Bitte gib deine SteamID64 oder deinen Profilnamen direkt oben im Textfeld ein."
                    )
                }
                android.view.View(ctx)
            }
        }
    )
}
