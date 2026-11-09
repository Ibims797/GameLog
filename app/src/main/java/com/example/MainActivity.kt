package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.config.AppConfig
import com.example.data.cloud.CloudGameRepository
import com.example.data.local.AppDatabase
import com.example.data.preferences.AppThemeStyle
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.remote.ExternalGamesRepository
import com.example.data.repository.StoryGamesRepository
import com.example.ui.auth.AuthGateScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.ExternalGameSearchDialog
import com.example.ui.components.GameDetailDialog
import com.example.ui.screens.CommunityRankingScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WishlistScreen
import com.example.ui.theme.StoryGamesTheme
import com.example.ui.viewmodel.StoryGamesViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

/**
 * Die 4 Haupt-Tabs der Bottom Navigation laut Spezifikation:
 * 1. "Meine Bibliothek"
 * 2. "Wunschliste"
 * 3. "Community Ranking"
 * 4. "Einstellungen"
 */
enum class MainTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    LIBRARY(
        route = "library",
        label = "Meine Bibliothek",
        selectedIcon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports,
        testTag = "nav_tab_library"
    ),
    WISHLIST(
        route = "wishlist",
        label = "Wunschliste",
        selectedIcon = Icons.Filled.Bookmark,
        unselectedIcon = Icons.Outlined.BookmarkBorder,
        testTag = "nav_tab_wishlist"
    ),
    COMMUNITY(
        route = "community",
        label = "Community Ranking",
        selectedIcon = Icons.Filled.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents,
        testTag = "nav_tab_community"
    ),
    SETTINGS(
        route = "settings",
        label = "Einstellungen",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StoryGamesRootApp()
        }
    }
}

@Composable
fun StoryGamesRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePreferences = remember { ThemePreferencesRepository(context.applicationContext) }
    val selectedTheme by themePreferences.selectedThemeFlow.collectAsStateWithLifecycle(
        initialValue = AppThemeStyle.CYBERPUNK_NEON
    )
    val linkedSteamId by themePreferences.linkedSteamIdFlow.collectAsStateWithLifecycle(
        initialValue = ""
    )

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isSessionUnlocked by rememberSaveable { mutableStateOf(false) }
    var forceShowAuthGate by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                forceShowAuthGate = false
                isSessionUnlocked = true
            } else {
                AppConfig.clearRuntimeAdminKeys()
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val canEnterApp = !forceShowAuthGate && (
        currentUser != null || isSessionUnlocked || linkedSteamId.isNotBlank()
    )

    StoryGamesTheme(themeStyle = selectedTheme) {
        if (!canEnterApp) {
            AuthGateScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    forceShowAuthGate = false
                    isSessionUnlocked = true
                },
                onSteamIdExtracted = { steamId ->
                    forceShowAuthGate = false
                    isSessionUnlocked = true
                    scope.launch {
                        themePreferences.setLinkedSteamId(steamId)
                    }
                },
                onContinueDirectly = {
                    forceShowAuthGate = false
                    isSessionUnlocked = true
                }
            )
        } else {
            val activeSessionId = currentUser?.uid
                ?: if (linkedSteamId.isNotBlank()) "steam_$linkedSteamId" else "local_player"
            key(activeSessionId) {
                AuthenticatedTrackerHub(
                    currentUserId = activeSessionId,
                    onSignOut = {
                        AppConfig.clearRuntimeAdminKeys()
                        isSessionUnlocked = false
                        forceShowAuthGate = true
                        val credentialManager = CredentialManager.create(context)
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = {
                                currentUser = null
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun AuthenticatedTrackerHub(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val viewModel: StoryGamesViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val roomDb = AppDatabase.getInstance(app)
                val cloudRepo = CloudGameRepository(firestore)
                val externalRepo = ExternalGamesRepository()
                val repository = StoryGamesRepository(
                    gameDao = roomDb.gameDao(),
                    cloudRepository = cloudRepo,
                    externalRepository = externalRepo
                )
                val themePrefs = ThemePreferencesRepository(app)
                StoryGamesViewModel(
                    repository = repository,
                    themePreferences = themePrefs,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val ownedGames by viewModel.ownedLibraryGames.collectAsStateWithLifecycle()
    val wishlistGames by viewModel.wishlistGames.collectAsStateWithLifecycle()
    val communityRankingsState by viewModel.communityRankingsState.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val linkedSteamId by viewModel.linkedSteamId.collectAsStateWithLifecycle()
    val searchDialogState by viewModel.searchDialogState.collectAsStateWithLifecycle()
    val steamImportState by viewModel.steamImportState.collectAsStateWithLifecycle()
    val adminVaultState by viewModel.adminVaultState.collectAsStateWithLifecycle()
    val selectedGameForDetail by viewModel.selectedGameForDetail.collectAsStateWithLifecycle()
    val selectedGameAchievements by viewModel.selectedGameAchievements.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MainTab.LIBRARY) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (currentTab != MainTab.LIBRARY) {
        BackHandler {
            currentTab = MainTab.LIBRARY
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainTab.LIBRARY -> {
                    LibraryScreen(
                        games = ownedGames,
                        linkedSteamId = linkedSteamId,
                        steamImportState = steamImportState,
                        onOpenSearch = { viewModel.openSearchDialog(targetOwnedLibrary = true) },
                        onSyncSteamLibraryNow = { viewModel.syncSteamLibraryAndWishlistAutomatically() },
                        onNavigateToSettingsSteamImport = { currentTab = MainTab.SETTINGS },
                        onSelectGame = { viewModel.selectGameForDetail(it) },
                        onQuickToggle100Percent = { viewModel.toggle100PercentQuick(it) }
                    )
                }

                MainTab.WISHLIST -> {
                    WishlistScreen(
                        wishlistGames = wishlistGames,
                        onOpenSearchForWishlist = { viewModel.openSearchDialog(targetOwnedLibrary = false) },
                        onNavigateToSteamWishlistImport = { currentTab = MainTab.SETTINGS },
                        onSelectGame = { viewModel.selectGameForDetail(it) },
                        onQuickToggle100Percent = { viewModel.toggle100PercentQuick(it) },
                        onMoveToOwnedLibrary = { viewModel.moveWishlistToOwnedLibrary(it) }
                    )
                }

                MainTab.COMMUNITY -> {
                    CommunityRankingScreen(
                        rankingsState = communityRankingsState
                    )
                }

                MainTab.SETTINGS -> {
                    SettingsScreen(
                        selectedTheme = selectedTheme,
                        onSelectTheme = { viewModel.selectTheme(it) },
                        linkedSteamId = linkedSteamId,
                        steamImportState = steamImportState,
                        adminVaultState = adminVaultState,
                        onSaveSteamId = { viewModel.saveSteamId(it) },
                        onImportSteamLibrary = { viewModel.importSteamOwnedGames(it) },
                        onImportSteamWishlist = { viewModel.importSteamWishlist(it) },
                        onSaveAdminSecrets = { twitchId, twitchSecret, steamKey ->
                            viewModel.saveAdminVaultSecrets(twitchId, twitchSecret, steamKey)
                        },
                        onReloadAdminSecrets = { viewModel.loadAdminVaultSecrets() },
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }

    // Externe Spielesuche (IGDB / Steam Store)
    ExternalGameSearchDialog(
        state = searchDialogState,
        onQueryChange = { viewModel.onSearchQueryChange(it) },
        onSearch = { viewModel.executeExternalGameSearch() },
        onAddGame = { viewModel.addSearchResultGame(it) },
        onDismiss = { viewModel.dismissSearchDialog() }
    )

    // Detailansicht (Errungenschaften mit Spoiler-Schutz, Notizen, 5-Sterne-Rating, Weiterempfehlen-Toggle, 100% Run Button)
    selectedGameForDetail?.let { detailGame ->
        GameDetailDialog(
            game = detailGame,
            achievementsState = selectedGameAchievements,
            isGoogleSignedIn = viewModel.isGoogleSignedIn,
            onRefreshAchievements = { viewModel.loadAchievementsForGame(detailGame) },
            onRequestGoogleLogin = {
                viewModel.selectGameForDetail(null)
                onSignOut()
            },
            onDismiss = { viewModel.selectGameForDetail(null) },
            onSaveAndSync = { rating, note, isRec, is100, isPlayed ->
                viewModel.saveGameDetailsAndSync(
                    game = detailGame,
                    newRating = rating,
                    newNote = note,
                    newIsRecommended = isRec,
                    newIs100Percent = is100,
                    newIsPlayed = isPlayed
                )
            },
            onDelete = {
                viewModel.deleteGame(detailGame.id, detailGame.title)
            }
        )
    }
}
