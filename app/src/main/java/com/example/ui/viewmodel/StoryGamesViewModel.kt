package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.data.cloud.GlobalGameStats
import com.example.data.local.LocalGame
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppThemeStyle
import com.example.data.preferences.CustomThemeColors
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.remote.ExternalGameSearchResult
import com.example.data.remote.SteamAchievement
import com.example.data.remote.SteamAchievementSerializer
import com.example.data.remote.SteamInventoryGameContext
import com.example.data.remote.SteamInventoryItem
import com.example.data.remote.SteamPriceOverviewInfo
import com.example.data.repository.StoryGamesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SearchDialogState(
    val isVisible: Boolean = false,
    val targetOwnedLibrary: Boolean = true,
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<ExternalGameSearchResult> = emptyList(),
    val errorMessage: String? = null
)

data class SteamImportState(
    val isImportingLibrary: Boolean = false,
    val isImportingWishlist: Boolean = false,
    val isLiveUpdaterActive: Boolean = true,
    val lastLiveSyncTimeText: String = "Bereit",
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val onlyPublicShowcaseWarning: Boolean = false,
    val hasSteamWebSession: Boolean = false
)

data class GameAchievementsState(
    val isLoading: Boolean = false,
    val achievements: List<SteamAchievement> = emptyList(),
    val errorMessage: String? = null
)

data class GamePriceState(
    val isLoading: Boolean = false,
    val priceInfo: SteamPriceOverviewInfo? = null,
    val errorMessage: String? = null
)

data class SteamInventoryUiState(
    val selectedContext: SteamInventoryGameContext = SteamInventoryGameContext.STEAM_COMMUNITY,
    val customAppIdInput: String = "",
    val customContextIdInput: String = "2",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val items: List<SteamInventoryItem> = emptyList(),
    val itemPrices: Map<String, String> = emptyMap(),
    val loadingPriceKeys: Set<String> = emptySet(),
    val errorMessage: String? = null
)

data class AdminVaultState(
    val isAdmin: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val twitchClientId: String = "",
    val twitchClientSecret: String = "",
    val steamApiKey: String = "",
    val statusMessage: String? = null,
    val isError: Boolean = false
)

/**
 * Haupt-ViewModel für den Story Games Tracker & Community Hub (MVVM).
 */
class StoryGamesViewModel(
    private val repository: StoryGamesRepository,
    private val themePreferences: ThemePreferencesRepository,
    val currentUserId: String
) : ViewModel() {

    val isGoogleSignedIn: Boolean
        get() = repository.isGoogleSignedIn()

    val isCurrentUserAdmin: Boolean
        get() = repository.isCurrentUserAdmin()

    // 1. Lokale Room-Flows ("Meine Bibliothek" & "Wunschliste")
    val ownedLibraryGames: StateFlow<List<LocalGame>> = repository.ownedLibraryGames
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val wishlistGames: StateFlow<List<LocalGame>> = repository.wishlistGames
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // 2. Cloud Community Ranking Flow (`GlobalGameStats` - nur bewertete oder empfohlene Spiele!)
    val communityRankingsState: StateFlow<UiState<List<GlobalGameStats>>> = repository.communityRankings
        .map<List<GlobalGameStats>, UiState<List<GlobalGameStats>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "Community-Ranking erfordert eine Google-Anmeldung."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    // 3. DataStore Preferences Flows (Dynamisches Theme, Custom Theme, Sprache & Verknüpfte Steam-ID)
    val selectedTheme: StateFlow<AppThemeStyle> = themePreferences.selectedThemeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppThemeStyle.CYBERPUNK_NEON
        )

    val customThemeColors: StateFlow<CustomThemeColors> = themePreferences.customThemeColorsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = CustomThemeColors()
        )

    val selectedLanguage: StateFlow<AppLanguage> = themePreferences.selectedLanguageFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppLanguage.fromSystemLocale()
        )

    val linkedSteamId: StateFlow<String> = themePreferences.linkedSteamIdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ""
        )

    // 4. UI-Zustände
    private val _searchDialogState = MutableStateFlow(SearchDialogState())
    val searchDialogState: StateFlow<SearchDialogState> = _searchDialogState.asStateFlow()

    private val _steamImportState = MutableStateFlow(
        SteamImportState(hasSteamWebSession = repository.hasActiveSteamWebSession())
    )
    val steamImportState: StateFlow<SteamImportState> = _steamImportState.asStateFlow()

    private val _adminVaultState = MutableStateFlow(
        AdminVaultState(isAdmin = repository.isCurrentUserAdmin())
    )
    val adminVaultState: StateFlow<AdminVaultState> = _adminVaultState.asStateFlow()

    private val _selectedGameForDetail = MutableStateFlow<LocalGame?>(null)
    val selectedGameForDetail: StateFlow<LocalGame?> = _selectedGameForDetail.asStateFlow()

    private val _selectedGameAchievements = MutableStateFlow(GameAchievementsState())
    val selectedGameAchievements: StateFlow<GameAchievementsState> = _selectedGameAchievements.asStateFlow()

    private val _selectedGamePriceState = MutableStateFlow(GamePriceState())
    val selectedGamePriceState: StateFlow<GamePriceState> = _selectedGamePriceState.asStateFlow()

    private val _inventoryState = MutableStateFlow(SteamInventoryUiState())
    val inventoryState: StateFlow<SteamInventoryUiState> = _inventoryState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var liveUpdaterJob: Job? = null
    private var backgroundEnrichJob: Job? = null

    private var lastAutoSyncedSteamId: String = ""

    init {
        viewModelScope.launch {
            // 0. Beim allerersten App-Start die Systemsprache des Handys einlesen und automatisch setzen
            themePreferences.initializeSystemLanguageIfUnset()

            // 1. Zuerst lokal gespeicherte Admin-Keys aus DataStore in den RAM (AppConfig) laden,
            // damit IPlayerService/GetOwnedGames/v1/ sofort beim App-Start & auch im Nur-Steam-Modus den Key hat!
            themePreferences.restoreCachedApiKeysIntoAppConfig()

            // 2. Automatischer Schutzmechanismus: Bereinigt alte Duplikate & fehlerhafte DLC-Reste
            repository.cleanupDuplicateGames()

            if (repository.isCurrentUserAdmin()) {
                _adminVaultState.value = _adminVaultState.value.copy(
                    isAdmin = true,
                    twitchClientId = AppConfig.TWITCH_CLIENT_ID,
                    twitchClientSecret = AppConfig.TWITCH_CLIENT_SECRET,
                    steamApiKey = AppConfig.STEAM_API_KEY
                )
                loadAdminVaultSecretsInternal()
            }

            if (repository.isGoogleSignedIn()) {
                repository.restoreUserCloudBackupIfNeeded()
            }

            val initialSteamId = themePreferences.linkedSteamIdFlow.first().trim()
                .ifBlank {
                    if (currentUserId.startsWith("steam_")) {
                        currentUserId.removePrefix("steam_").trim()
                    } else ""
                }

            // Sofort beim Start für bereits vorhandene Spiele Preise (inkl. Free-to-Play) & 100%-Platin prüfen
            triggerBackgroundPricesAndAchievementsSync(initialSteamId)

            if (initialSteamId.isNotBlank()) {
                lastAutoSyncedSteamId = initialSteamId
                syncSteamLibraryAndWishlistAutomatically(initialSteamId)
            }

            // Reagiert sofort, wenn die Steam-ID kurz nach dem Login gesetzt oder geändert wird
            launch {
                themePreferences.linkedSteamIdFlow.collect { newSteamId ->
                    val cleaned = newSteamId.trim()
                    if (cleaned.isNotBlank() && cleaned != lastAutoSyncedSteamId) {
                        lastAutoSyncedSteamId = cleaned
                        syncSteamLibraryAndWishlistAutomatically(cleaned)
                    }
                }
            }

            // Startet den Live-Updater für Wunschliste, Bibliothek, Preise & 100%-Runs
            startLiveWishlistAndLibraryUpdater()
        }
    }

    /**
     * Lädt im Hintergrund vollautomatisch für alle Spiele in Bibliothek & Wunschliste:
     * 1. Aktuelle Steam-Preise inklusive "Free-to-Play"-Status & Preisalarm-Prüfung
     * 2. Steam-Errungenschaften & automatische "100% Platin"-Erkennung ohne manuelles Anklicken
     */
    private fun triggerBackgroundPricesAndAchievementsSync(steamIdInput: String = linkedSteamId.value) {
        backgroundEnrichJob?.cancel()
        backgroundEnrichJob = viewModelScope.launch {
            val priceRes = repository.refreshAllPricesAndCheckAlerts()
            priceRes.onSuccess { triggered ->
                if (triggered.isNotEmpty()) {
                    _snackbarMessage.value = "🔔 PREISALARM ERREICHT: ${triggered.take(2).joinToString(", ")}!"
                }
            }
            val effectiveSteamId = steamIdInput.trim().ifBlank {
                linkedSteamId.value.trim().ifBlank {
                    themePreferences.linkedSteamIdFlow.first().trim()
                }
            }
            repository.autoSyncMissingAchievementsAnd100Percent(effectiveSteamId)
        }
    }

    /**
     * Hintergrund-Live-Updater:
     * Prüft alle 20 Sekunden automatisch die verknüpfte Steam-Wunschliste & Preise,
     * sodass neu auf Steam hinzugefügte Wunschlisten-Spiele und erreichte Preisalarme sofort live erscheinen!
     */
    private fun startLiveWishlistAndLibraryUpdater() {
        liveUpdaterJob?.cancel()
        liveUpdaterJob = viewModelScope.launch {
            while (isActive) {
                delay(20_000L)
                if (!_steamImportState.value.isLiveUpdaterActive) continue
                val currentSteamId = linkedSteamId.value.trim().ifBlank {
                    themePreferences.linkedSteamIdFlow.first().trim()
                }
                if (currentSteamId.isBlank()) continue
                if (_steamImportState.value.isImportingLibrary || _steamImportState.value.isImportingWishlist) continue

                val diffResult = repository.importSteamWishlistWithDiff(currentSteamId)
                diffResult.onSuccess { summary ->
                    _steamImportState.value = _steamImportState.value.copy(
                        lastLiveSyncTimeText = "Live synchronisiert (${summary.totalCount} Titel)",
                        hasSteamWebSession = repository.hasActiveSteamWebSession()
                    )
                    if (summary.triggeredAlertTitles.isNotEmpty()) {
                        val preview = summary.triggeredAlertTitles.take(2).joinToString(", ")
                        _snackbarMessage.value = "🔔 PREISALARM ERREICHT: $preview!"
                    } else if (summary.newlyAddedTitles.isNotEmpty()) {
                        val preview = summary.newlyAddedTitles.take(2).joinToString(", ")
                        _snackbarMessage.value = "✨ Live-Update: '$preview' neu auf deiner Wunschliste erkannt!"
                    }
                }
                repository.refreshAllPricesAndCheckAlerts()
                repository.autoSyncMissingAchievementsAnd100Percent(currentSteamId)
            }
        }
    }

    fun toggleLiveWishlistUpdater(enabled: Boolean) {
        _steamImportState.value = _steamImportState.value.copy(
            isLiveUpdaterActive = enabled,
            lastLiveSyncTimeText = if (enabled) "Live-Updater aktiv" else "Live-Updater pausiert"
        )
        if (enabled) {
            triggerImmediateWishlistLiveSync()
        }
    }

    fun triggerImmediateWishlistLiveSync() {
        val currentSteamId = linkedSteamId.value.trim()
        if (currentSteamId.isBlank() || _steamImportState.value.isImportingWishlist) return
        viewModelScope.launch {
            val diffResult = repository.importSteamWishlistWithDiff(currentSteamId)
            diffResult.onSuccess { summary ->
                _steamImportState.value = _steamImportState.value.copy(
                    lastLiveSyncTimeText = "Live synchronisiert (${summary.totalCount} Titel)",
                    hasSteamWebSession = repository.hasActiveSteamWebSession()
                )
                if (summary.triggeredAlertTitles.isNotEmpty()) {
                    val preview = summary.triggeredAlertTitles.take(2).joinToString(", ")
                    _snackbarMessage.value = "🔔 PREISALARM ERREICHT: $preview!"
                } else if (summary.newlyAddedTitles.isNotEmpty()) {
                    val preview = summary.newlyAddedTitles.take(2).joinToString(", ")
                    _snackbarMessage.value = "✨ Live-Update: '$preview' neu auf deiner Wunschliste erkannt!"
                }
            }
        }
    }

    // --- ADMIN CLOUD VAULT & ADMIN RANKING MODERATION (STRIKT FÜR nico.schaefer.hn@gmail.com) ---

    fun loadAdminVaultSecrets() {
        if (!repository.isCurrentUserAdmin()) return
        viewModelScope.launch {
            loadAdminVaultSecretsInternal()
        }
    }

    private suspend fun loadAdminVaultSecretsInternal() {
        if (!repository.isCurrentUserAdmin()) return
        _adminVaultState.value = _adminVaultState.value.copy(
            isAdmin = true,
            isLoading = true,
            statusMessage = null,
            isError = false
        )
        val result = repository.loadAdminVaultSecrets()
        result.fold(
            onSuccess = { secrets ->
                val resolvedTwitchId = secrets?.twitchClientId?.takeIf { it.isNotBlank() } ?: AppConfig.TWITCH_CLIENT_ID
                val resolvedTwitchSecret = secrets?.twitchClientSecret?.takeIf { it.isNotBlank() } ?: AppConfig.TWITCH_CLIENT_SECRET
                val resolvedSteamKey = secrets?.steamApiKey?.takeIf { it.isNotBlank() } ?: AppConfig.STEAM_API_KEY

                if (resolvedTwitchId.isNotBlank() || resolvedTwitchSecret.isNotBlank() || resolvedSteamKey.isNotBlank()) {
                    themePreferences.cacheRuntimeApiKeysLocally(
                        twitchClientId = resolvedTwitchId,
                        twitchClientSecret = resolvedTwitchSecret,
                        steamApiKey = resolvedSteamKey
                    )
                }

                _adminVaultState.value = _adminVaultState.value.copy(
                    isLoading = false,
                    twitchClientId = resolvedTwitchId,
                    twitchClientSecret = resolvedTwitchSecret,
                    steamApiKey = resolvedSteamKey,
                    statusMessage = if (secrets != null && resolvedSteamKey.isNotBlank()) {
                        "API-Keys geladen! Offizieller IPlayerService/GetOwnedGames/v1/ Endpunkt ist aktiv."
                    } else if (secrets != null) {
                        "API-Keys aus dem Cloud-Admin-Tresor geladen."
                    } else {
                        "Cloud-Tresor ist noch leer. Trage deinen 32-stelligen Steam Web API Key ein und speichere ihn."
                    },
                    isError = false
                )
            },
            onFailure = { err ->
                _adminVaultState.value = _adminVaultState.value.copy(
                    isLoading = false,
                    twitchClientId = AppConfig.TWITCH_CLIENT_ID,
                    twitchClientSecret = AppConfig.TWITCH_CLIENT_SECRET,
                    steamApiKey = AppConfig.STEAM_API_KEY,
                    statusMessage = "Lokal gecachte Keys aktiv (${err.localizedMessage ?: "Cloud-Tresor Hinweis"})",
                    isError = false
                )
            }
        )
    }

    fun saveAdminVaultSecrets(
        twitchClientId: String,
        twitchClientSecret: String,
        steamApiKey: String
    ) {
        if (!repository.isCurrentUserAdmin()) return
        viewModelScope.launch {
            _adminVaultState.value = _adminVaultState.value.copy(
                isSaving = true,
                statusMessage = null,
                isError = false
            )
            // 1. Sofort lokal in DataStore & AppConfig speichern, damit GetOwnedGames/v1/ den Key unmittelbar nutzt!
            themePreferences.cacheRuntimeApiKeysLocally(
                twitchClientId = twitchClientId,
                twitchClientSecret = twitchClientSecret,
                steamApiKey = steamApiKey
            )

            // 2. Zusätzlich im geschützten Firestore Admin-Tresor speichern
            val result = repository.saveAdminVaultSecrets(
                twitchClientId = twitchClientId,
                twitchClientSecret = twitchClientSecret,
                steamApiKey = steamApiKey
            )
            result.fold(
                onSuccess = { saved ->
                    _adminVaultState.value = _adminVaultState.value.copy(
                        isSaving = false,
                        twitchClientId = saved.twitchClientId,
                        twitchClientSecret = saved.twitchClientSecret,
                        steamApiKey = saved.steamApiKey,
                        statusMessage = "✅ API-Keys gespeichert! Starte sofortigen Abruf über IPlayerService/GetOwnedGames/v1/...",
                        isError = false
                    )
                    val currentSteam = linkedSteamId.value.trim()
                    if (currentSteam.isNotBlank()) {
                        syncSteamLibraryAndWishlistAutomatically(currentSteam)
                    }
                },
                onFailure = { err ->
                    _adminVaultState.value = _adminVaultState.value.copy(
                        isSaving = false,
                        twitchClientId = AppConfig.TWITCH_CLIENT_ID,
                        twitchClientSecret = AppConfig.TWITCH_CLIENT_SECRET,
                        steamApiKey = AppConfig.STEAM_API_KEY,
                        statusMessage = "Key lokal für GetOwnedGames/v1/ aktiviert (Cloud-Sync Hinweis: ${err.localizedMessage})",
                        isError = false
                    )
                    val currentSteam = linkedSteamId.value.trim()
                    if (currentSteam.isNotBlank()) {
                        syncSteamLibraryAndWishlistAutomatically(currentSteam)
                    }
                }
            )
        }
    }

    /**
     * Exklusive Admin-Funktion (`nico.schaefer.hn@gmail.com`):
     * Löscht ein Spiel aus dem globalen Community-Ranking.
     */
    fun deleteRankingEntryAsAdmin(stats: GlobalGameStats) {
        if (!repository.isCurrentUserAdmin()) {
            _snackbarMessage.value = "Nur der Admin kann Einträge aus dem Ranking löschen."
            return
        }
        viewModelScope.launch {
            val result = repository.deleteGameFromCommunityRankingAsAdmin(stats.id)
            result.fold(
                onSuccess = {
                    _snackbarMessage.value = "'${stats.title}' wurde aus dem Community-Ranking gelöscht."
                },
                onFailure = { err ->
                    _snackbarMessage.value = "Fehler beim Löschen: ${err.localizedMessage ?: "Unbekannt"}"
                }
            )
        }
    }

    /**
     * Fügt ein Spiel aus dem Community-Ranking zur Wunschliste hinzu
     * und synchronisiert es bei aktiver Steam-Sitzung direkt auf Steam.
     */
    fun addRankingGameToWishlist(stats: GlobalGameStats) {
        viewModelScope.launch {
            _snackbarMessage.value = "Füge '${stats.title}' zur Wunschliste hinzu..."
            val result = repository.addRankingGameToWishlist(stats)
            result.fold(
                onSuccess = { res ->
                    _snackbarMessage.value = res.statusMessage
                },
                onFailure = { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Wunschlisten-Fehler aufgetreten."
                }
            )
        }
    }

    // --- SPIEL-DETAILANSICHT, ERRUNGENSCHAFTEN & STEAMDB PREISALARM ---

    fun selectGameForDetail(game: LocalGame?) {
        _selectedGameForDetail.value = game
        if (game == null) {
            _selectedGameAchievements.value = GameAchievementsState()
            _selectedGamePriceState.value = GamePriceState()
            return
        }

        val cached = SteamAchievementSerializer.fromJsonString(game.achievementsJson)
        _selectedGameAchievements.value = GameAchievementsState(
            isLoading = true,
            achievements = cached,
            errorMessage = null
        )

        loadAchievementsForGame(game)
        loadPriceForGame(game)
    }

    fun loadPriceForGame(game: LocalGame) {
        viewModelScope.launch {
            val appId = game.id.removePrefix("steam_").toLongOrNull() ?: 0L
            val initialInfo = if (game.currentPriceCents >= 0 || game.formattedPrice.isNotBlank()) {
                SteamPriceOverviewInfo(
                    steamAppId = appId,
                    currentPriceCents = game.currentPriceCents,
                    originalPriceCents = game.originalPriceCents,
                    discountPercent = game.discountPercent,
                    formattedCurrentPrice = game.formattedPrice,
                    lowestHistoricalPrice = game.lowestHistoricalPrice,
                    steamDbUrl = if (appId > 0L) "https://steamdb.info/app/$appId/" else "https://steamdb.info/search/?a=app&q=${game.title}"
                )
            } else null

            _selectedGamePriceState.value = GamePriceState(
                isLoading = true,
                priceInfo = initialInfo,
                errorMessage = null
            )

            val result = repository.refreshGamePriceAndAlert(game)
            result.fold(
                onSuccess = { (updatedGame, freshPriceInfo) ->
                    _selectedGamePriceState.value = GamePriceState(
                        isLoading = false,
                        priceInfo = freshPriceInfo,
                        errorMessage = null
                    )
                    if (_selectedGameForDetail.value?.id == updatedGame.id) {
                        _selectedGameForDetail.value = updatedGame
                    }
                    if (updatedGame.isPriceAlertTriggered && !game.isPriceAlertTriggered) {
                        _snackbarMessage.value = "🔔 Preisalarm erreicht für '${updatedGame.title}' (${updatedGame.formattedPrice})!"
                    }
                },
                onFailure = { err ->
                    _selectedGamePriceState.value = GamePriceState(
                        isLoading = false,
                        priceInfo = initialInfo,
                        errorMessage = err.localizedMessage ?: "SteamDB/Store Preis konnte nicht geladen werden."
                    )
                }
            )
        }
    }

    /**
     * Setzt oder deaktiviert (`0.0`) den Preisalarm für ein Spiel (z.B. auf den aktuellen Preis oder Wunschpreis).
     */
    fun savePriceAlertForGame(game: LocalGame, targetAlertEur: Double) {
        viewModelScope.launch {
            val updated = repository.updatePriceAlertTarget(game, targetAlertEur)
            if (_selectedGameForDetail.value?.id == updated.id) {
                _selectedGameForDetail.value = updated
            }
            if (targetAlertEur <= 0.0) {
                _snackbarMessage.value = "Preisalarm für '${updated.title}' deaktiviert."
            } else if (updated.isPriceAlertTriggered) {
                _snackbarMessage.value = "🔔 Preisalarm aktiv & bereits erreicht! Aktueller Preis (${updated.formattedPrice}) liegt bei/unter ${String.format(java.util.Locale.GERMANY, "%.2f€", targetAlertEur)}!"
            } else {
                _snackbarMessage.value = "🔔 Preisalarm für '${updated.title}' auf ${String.format(java.util.Locale.GERMANY, "%.2f€", targetAlertEur)} gesetzt!"
            }
            // Aktualisiere zusätzlich live den Preis aus dem Store
            repository.refreshGamePriceAndAlert(updated, newTargetAlertEur = targetAlertEur).onSuccess { (refreshed, info) ->
                _selectedGamePriceState.value = GamePriceState(isLoading = false, priceInfo = info)
                if (_selectedGameForDetail.value?.id == refreshed.id) {
                    _selectedGameForDetail.value = refreshed
                }
            }
        }
    }

    fun refreshAllPricesAndAlerts() {
        viewModelScope.launch {
            _snackbarMessage.value = "Prüfe Live-Preise (inkl. Free-to-Play), 100%-Runs & Preisalarme..."
            val result = repository.refreshAllPricesAndCheckAlerts()
            result.fold(
                onSuccess = { triggered ->
                    if (triggered.isNotEmpty()) {
                        _snackbarMessage.value = "🔔 Preisalarm erreicht bei: ${triggered.take(3).joinToString(", ")}!"
                    } else {
                        _snackbarMessage.value = "Alle Steam-Preise, Free-to-Play-Badges & Preisalarme wurden aktualisiert!"
                    }
                },
                onFailure = { err ->
                    _snackbarMessage.value = "Preis-Update Fehler: ${err.localizedMessage ?: "Unbekannt"}"
                }
            )
            val steamId = linkedSteamId.value.trim().ifBlank {
                themePreferences.linkedSteamIdFlow.first().trim()
            }
            repository.autoSyncMissingAchievementsAnd100Percent(steamId)
        }
    }

    fun loadAchievementsForGame(game: LocalGame) {
        viewModelScope.launch {
            val cached = SteamAchievementSerializer.fromJsonString(game.achievementsJson)
            _selectedGameAchievements.value = _selectedGameAchievements.value.copy(
                isLoading = true,
                achievements = cached,
                errorMessage = null
            )

            val steamId = linkedSteamId.value.trim().ifBlank {
                themePreferences.linkedSteamIdFlow.first().trim()
            }
            val result = repository.fetchAndCacheGameAchievements(game, steamId)
            result.fold(
                onSuccess = { freshList ->
                    _selectedGameAchievements.value = GameAchievementsState(
                        isLoading = false,
                        achievements = freshList,
                        errorMessage = null
                    )
                    if (freshList.isNotEmpty() && freshList.all { it.isUnlocked }) {
                        val currentDetail = _selectedGameForDetail.value
                        if (currentDetail?.id == game.id && !currentDetail.is100Percent) {
                            _selectedGameForDetail.value = currentDetail.copy(
                                is100Percent = true,
                                isPlayed = true
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _selectedGameAchievements.value = GameAchievementsState(
                        isLoading = false,
                        achievements = cached,
                        errorMessage = if (cached.isEmpty()) {
                            err.localizedMessage ?: "Keine Errungenschaften verfügbar."
                        } else null
                    )
                }
            )
        }
    }

    // --- STEAM INVENTAR VIEWER ---

    fun selectInventoryContext(context: SteamInventoryGameContext) {
        _inventoryState.value = _inventoryState.value.copy(
            selectedContext = context,
            errorMessage = null
        )
        loadSteamInventory(context = context)
    }

    fun updateCustomInventoryInput(appIdInput: String, contextIdInput: String) {
        _inventoryState.value = _inventoryState.value.copy(
            customAppIdInput = appIdInput,
            customContextIdInput = contextIdInput
        )
    }

    fun updateInventorySearchQuery(query: String) {
        _inventoryState.value = _inventoryState.value.copy(searchQuery = query)
    }

    fun loadSteamInventory(
        context: SteamInventoryGameContext = _inventoryState.value.selectedContext,
        customAppId: Long? = null,
        customContextId: Long? = null
    ) {
        viewModelScope.launch {
            val steamId = linkedSteamId.value.trim().ifBlank {
                themePreferences.linkedSteamIdFlow.first().trim()
            }
            if (steamId.isBlank()) {
                _inventoryState.value = _inventoryState.value.copy(
                    isLoading = false,
                    errorMessage = "Bitte verknüpfe zuerst deine Steam-ID oder melde dich per Steam Web-Login an."
                )
                return@launch
            }

            val targetAppId = customAppId ?: context.appId
            val targetContextId = customContextId ?: context.contextId

            _inventoryState.value = _inventoryState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            val result = repository.fetchSteamInventory(
                steamIdInput = steamId,
                appId = targetAppId,
                contextId = targetContextId
            )

            result.fold(
                onSuccess = { items ->
                    _inventoryState.value = _inventoryState.value.copy(
                        isLoading = false,
                        items = items,
                        errorMessage = null
                    )
                },
                onFailure = { err ->
                    _inventoryState.value = _inventoryState.value.copy(
                        isLoading = false,
                        items = emptyList(),
                        errorMessage = err.localizedMessage ?: "Steam-Inventar konnte nicht geladen werden."
                    )
                }
            )
        }
    }

    fun loadMarketPriceForInventoryItem(item: SteamInventoryItem) {
        if (item.marketHashName.isBlank()) return
        val key = "${item.appId}_${item.classId}"
        if (key in _inventoryState.value.loadingPriceKeys) return

        viewModelScope.launch {
            _inventoryState.value = _inventoryState.value.copy(
                loadingPriceKeys = _inventoryState.value.loadingPriceKeys + key
            )
            val result = repository.fetchSteamMarketItemPrice(item.appId, item.marketHashName)
            val priceText = result.getOrElse { "Kein Marktpreis" }
            _inventoryState.value = _inventoryState.value.copy(
                itemPrices = _inventoryState.value.itemPrices + (key to priceText),
                loadingPriceKeys = _inventoryState.value.loadingPriceKeys - key
            )
        }
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    // --- DETAIL-ANSICHT SPEICHERN (RANKING NUR BEI BEWERTUNG ODER EMPFEHLUNG!) ---

    fun saveGameDetailsAndSync(
        game: LocalGame,
        newRating: Int,
        newNote: String,
        newIsRecommended: Boolean,
        newIs100Percent: Boolean,
        newIsPlayed: Boolean
    ) {
        viewModelScope.launch {
            if (!repository.isGoogleSignedIn()) {
                val localOnlyUpdated = game.copy(
                    personalNote = newNote.trim(),
                    is100Percent = newIs100Percent,
                    isPlayed = newIsPlayed || newIs100Percent
                )
                repository.saveGameLocally(localOnlyUpdated)
                _selectedGameForDetail.value = localOnlyUpdated
                _snackbarMessage.value = "Notiz & Status lokal gespeichert. Bewertungen & Ranking erfordern Google-Login."
                return@launch
            }

            val sanitizedRating = newRating.coerceIn(0, 5)
            val updated = game.copy(
                personalRating = sanitizedRating,
                personalNote = newNote.trim(),
                isRecommended = newIsRecommended,
                is100Percent = newIs100Percent,
                isPlayed = newIsPlayed || newIs100Percent || sanitizedRating > 0
            )
            _selectedGameForDetail.value = updated
            val syncResult = repository.saveGameAndSyncToCloud(updated)
            if (syncResult.isSuccess) {
                _snackbarMessage.value = if (sanitizedRating > 0 || newIsRecommended) {
                    "Bewertung/Empfehlung gespeichert & ins Community-Ranking übertragen!"
                } else {
                    "Spielstatus gespeichert (Ohne Bewertung/Empfehlung nicht im Ranking gelistet)."
                }
            } else {
                _snackbarMessage.value = "Lokal gespeichert (${syncResult.exceptionOrNull()?.localizedMessage ?: "Offline"})"
            }
        }
    }

    /**
     * Schaltet den 100%-Platin-Run-Status für ein Spiel um.
     * WICHTIG: Ein 100%-Run allein trägt das Spiel NICHT in das Community-Ranking ein,
     * es sei denn, das Spiel besitzt bereits eine Bewertung (`personalRating > 0`) oder Empfehlung (`isRecommended`)!
     */
    fun toggle100PercentQuick(game: LocalGame) {
        viewModelScope.launch {
            val toggled = !game.is100Percent
            val updated = game.copy(
                is100Percent = toggled,
                isPlayed = if (toggled) true else game.isPlayed
            )
            if (_selectedGameForDetail.value?.id == game.id) {
                _selectedGameForDetail.value = updated
            }
            if (repository.isGoogleSignedIn() && (updated.personalRating > 0 || updated.isRecommended)) {
                repository.saveGameAndSyncToCloud(updated)
            } else {
                repository.saveGameLocally(updated)
            }
            _snackbarMessage.value = if (toggled) {
                "🏆 100% PLATIN RUN in deiner Bibliothek aktiviert!"
            } else {
                "100% Run Status zurückgesetzt."
            }
        }
    }

    fun moveWishlistToOwnedLibrary(game: LocalGame) {
        viewModelScope.launch {
            repository.moveWishlistGameToLibrary(game)
            if (_selectedGameForDetail.value?.id == game.id) {
                _selectedGameForDetail.value = game.copy(isOwned = true)
            }
            _snackbarMessage.value = "'${game.title}' in Meine Bibliothek verschoben!"
        }
    }

    fun deleteGame(gameId: String, title: String) {
        viewModelScope.launch {
            repository.deleteLocalGame(gameId)
            if (_selectedGameForDetail.value?.id == gameId) {
                _selectedGameForDetail.value = null
            }
            _snackbarMessage.value = "'$title' aus deiner Sammlung entfernt."
        }
    }

    /**
     * Manueller und automatischer Schutzmechanismus:
     * Bereinigt Duplikate, führt IGDB- & Steam-Einträge zusammen und entfernt alte DLC-Reste.
     */
    fun cleanupDuplicateGames() {
        viewModelScope.launch {
            _snackbarMessage.value = "Prüfe und bereinige Duplikate in deiner Bibliothek..."
            val removed = repository.cleanupDuplicateGames()
            if (removed > 0) {
                _snackbarMessage.value = "🛡️ Schutzmechanismus aktiv: $removed Duplikate & DLC-Reste erfolgreich entfernt!"
            } else {
                _snackbarMessage.value = "Deine Bibliothek ist optimal bereinigt (0 Duplikate gefunden)."
            }
        }
    }

    // --- EXTERNE SUCHE (IGDB / STEAM STORE) ---

    fun openSearchDialog(targetOwnedLibrary: Boolean) {
        _searchDialogState.value = SearchDialogState(
            isVisible = true,
            targetOwnedLibrary = targetOwnedLibrary
        )
    }

    fun dismissSearchDialog() {
        _searchDialogState.value = SearchDialogState(isVisible = false)
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchDialogState.value = _searchDialogState.value.copy(
            query = newQuery,
            errorMessage = null
        )
    }

    fun executeExternalGameSearch() {
        val currentQuery = _searchDialogState.value.query.trim()
        if (currentQuery.isBlank()) {
            _searchDialogState.value = _searchDialogState.value.copy(
                errorMessage = "Bitte gib einen Spieletitel ein (z.B. Witcher 3, Cyberpunk, Baldur's Gate)."
            )
            return
        }

        viewModelScope.launch {
            _searchDialogState.value = _searchDialogState.value.copy(
                isSearching = true,
                errorMessage = null
            )
            val result = repository.searchExternalGames(currentQuery)
            result.fold(
                onSuccess = { list ->
                    _searchDialogState.value = _searchDialogState.value.copy(
                        isSearching = false,
                        results = list,
                        errorMessage = if (list.isEmpty()) "Keine Spiele für '$currentQuery' gefunden." else null
                    )
                },
                onFailure = { err ->
                    _searchDialogState.value = _searchDialogState.value.copy(
                        isSearching = false,
                        errorMessage = "API-Fehler bei der Suche: ${err.localizedMessage ?: "Netzwerkfehler"}"
                    )
                }
            )
        }
    }

    fun addSearchResultGame(item: ExternalGameSearchResult) {
        val targetOwned = _searchDialogState.value.targetOwnedLibrary
        viewModelScope.launch {
            val saved = repository.addSearchResultToCollection(item, toOwnedLibrary = targetOwned)
            _snackbarMessage.value = if (targetOwned) {
                "'${saved.title}' zu Meine Bibliothek hinzugefügt!"
            } else {
                "'${saved.title}' zur Wunschliste hinzugefügt!"
            }
            triggerBackgroundPricesAndAchievementsSync()
        }
    }

    // --- STEAM LOGIN / ID VERKNÜPFUNG & VOLLSTÄNDIGER AUTO-IMPORT ---

    fun saveSteamId(steamId: String) {
        val cleanId = steamId.trim()
        if (cleanId.isBlank()) return
        viewModelScope.launch {
            themePreferences.setLinkedSteamId(cleanId)
            syncSteamLibraryAndWishlistAutomatically(cleanId)
        }
    }

    fun onSteamWebSessionAuthenticated(steamId64: String) {
        val cleanId = steamId64.trim()
        if (cleanId.isBlank()) return
        viewModelScope.launch {
            themePreferences.setLinkedSteamId(cleanId)
            _steamImportState.value = _steamImportState.value.copy(
                hasSteamWebSession = repository.hasActiveSteamWebSession(),
                onlyPublicShowcaseWarning = false
            )
            syncSteamLibraryAndWishlistAutomatically(cleanId)
            loadSteamInventory()
        }
    }

    fun syncSteamLibraryAndWishlistAutomatically(steamIdInput: String = linkedSteamId.value) {
        val targetId = steamIdInput.trim().ifBlank { linkedSteamId.value.trim() }
        if (targetId.isBlank()) return

        viewModelScope.launch {
            themePreferences.restoreCachedApiKeysIntoAppConfig()
            themePreferences.setLinkedSteamId(targetId)
            _steamImportState.value = _steamImportState.value.copy(
                isImportingLibrary = true,
                isImportingWishlist = true,
                statusMessage = "Lade komplette Steam-Bibliothek (IPlayerService/GetOwnedGames/v1/) & Wunschliste...",
                isError = false,
                hasSteamWebSession = repository.hasActiveSteamWebSession()
            )

            val libResult = repository.importSteamLibraryWithReport(targetId)
            val wishResult = repository.importSteamWishlistWithDiff(targetId)

            val report = libResult.getOrNull()
            val libCount = report?.games?.size ?: 0
            val wishCount = wishResult.getOrNull()?.totalCount ?: 0
            val showcaseOnly = report?.onlyPublicShowcaseFound == true

            if (libResult.isSuccess || wishResult.isSuccess) {
                val msg = buildString {
                    append("Synchronisiert: ")
                    append("$libCount Spiele in Bibliothek")
                    if (wishCount > 0) append(" • $wishCount auf Wunschliste")
                    if (showcaseOnly) {
                        val diag = report?.diagnosticInfo?.takeIf { it.isNotBlank() }
                        if (diag != null) {
                            append("\n⚠️ $diag")
                        } else {
                            append(" • ⚠️ Hinweis: Nur $libCount öffentliche Profil-Showcase-Spiele sichtbar. Prüfe deinen Steam Web API Key im Admin-Tresor und stelle 'Spieldetails' auf Öffentlich!")
                        }
                    } else if (libResult.isFailure) {
                        append(" (${libResult.exceptionOrNull()?.localizedMessage ?: "Bitte Steam-Privatsphäre prüfen"})")
                    }
                }
                _steamImportState.value = _steamImportState.value.copy(
                    isImportingLibrary = false,
                    isImportingWishlist = false,
                    lastLiveSyncTimeText = "Live synchronisiert ($wishCount Wunschliste)",
                    statusMessage = msg,
                    isError = libResult.isFailure && libCount == 0,
                    onlyPublicShowcaseWarning = showcaseOnly,
                    hasSteamWebSession = repository.hasActiveSteamWebSession()
                )
                triggerBackgroundPricesAndAchievementsSync(targetId)
            } else {
                val errMsg = libResult.exceptionOrNull()?.localizedMessage
                    ?: wishResult.exceptionOrNull()?.localizedMessage
                    ?: "Steam-Synchronisation fehlgeschlagen."
                _steamImportState.value = _steamImportState.value.copy(
                    isImportingLibrary = false,
                    isImportingWishlist = false,
                    statusMessage = errMsg,
                    isError = true,
                    hasSteamWebSession = repository.hasActiveSteamWebSession()
                )
            }
        }
    }

    fun importSteamOwnedGames(steamIdInput: String) {
        val targetId = steamIdInput.trim().ifBlank { linkedSteamId.value }
        if (targetId.isBlank()) {
            _steamImportState.value = _steamImportState.value.copy(
                statusMessage = "Bitte gib zuerst deine 17-stellige SteamID64 oder deinen Steam-Profilnamen ein.",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            themePreferences.restoreCachedApiKeysIntoAppConfig()
            themePreferences.setLinkedSteamId(targetId)
            _steamImportState.value = _steamImportState.value.copy(
                isImportingLibrary = true,
                statusMessage = "Rufe IPlayerService/GetOwnedGames/v1/ (include_appinfo=true, include_played_free_games=true) ab...",
                isError = false,
                hasSteamWebSession = repository.hasActiveSteamWebSession()
            )
            val result = repository.importSteamLibraryWithReport(targetId)
            result.fold(
                onSuccess = { report ->
                    val count = report.games.size
                    val warningText = if (report.onlyPublicShowcaseFound) {
                        val diag = report.diagnosticInfo.ifBlank {
                            "Bitte prüfe deinen 32-stelligen Steam Web API Key im Admin-Tresor und stelle auf Steam unter 'Privatsphäre -> Spieldetails' auf Öffentlich."
                        }
                        "⚠️ Nur $count Profil-Showcase-Spiele gefunden! $diag"
                    } else {
                        val srcName = if (report.usedWebApi) "IPlayerService/GetOwnedGames/v1/" else "Steam Community Sync"
                        "✅ Alle $count Spiele erfolgreich über $srcName in Meine Bibliothek geladen!"
                    }
                    _steamImportState.value = _steamImportState.value.copy(
                        isImportingLibrary = false,
                        statusMessage = warningText,
                        isError = false,
                        onlyPublicShowcaseWarning = report.onlyPublicShowcaseFound,
                        hasSteamWebSession = repository.hasActiveSteamWebSession()
                    )
                    _snackbarMessage.value = if (report.onlyPublicShowcaseFound) {
                        "Nur $count Showcase-Spiele gefunden – siehe Diagnose-Hinweis!"
                    } else {
                        "$count Spiele in Meine Bibliothek geladen!"
                    }
                    triggerBackgroundPricesAndAchievementsSync(targetId)
                },
                onFailure = { error ->
                    _steamImportState.value = _steamImportState.value.copy(
                        isImportingLibrary = false,
                        statusMessage = error.localizedMessage ?: "Steam-Bibliothek Import fehlgeschlagen.",
                        isError = true,
                        hasSteamWebSession = repository.hasActiveSteamWebSession()
                    )
                }
            )
        }
    }

    fun importSteamWishlist(steamIdInput: String) {
        val targetId = steamIdInput.trim().ifBlank { linkedSteamId.value }
        if (targetId.isBlank()) {
            _steamImportState.value = _steamImportState.value.copy(
                statusMessage = "Bitte gib zuerst deine 17-stellige SteamID64 oder deinen Steam-Profilnamen ein.",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            themePreferences.setLinkedSteamId(targetId)
            _steamImportState.value = _steamImportState.value.copy(
                isImportingWishlist = true,
                statusMessage = "Importiere komplette Steam-Wunschliste & Preise...",
                isError = false
            )
            val result = repository.importSteamWishlistWithDiff(targetId)
            result.fold(
                onSuccess = { summary ->
                    _steamImportState.value = _steamImportState.value.copy(
                        isImportingWishlist = false,
                        lastLiveSyncTimeText = "Live synchronisiert (${summary.totalCount} Titel)",
                        statusMessage = "${summary.totalCount} Spiele aus deiner Steam-Wunschliste geladen!",
                        isError = false
                    )
                    if (summary.triggeredAlertTitles.isNotEmpty()) {
                        _snackbarMessage.value = "🔔 Preisalarm erreicht: ${summary.triggeredAlertTitles.take(2).joinToString(", ")}!"
                    } else {
                        _snackbarMessage.value = "${summary.totalCount} Spiele auf deiner Wunschliste aktualisiert!"
                    }
                    triggerBackgroundPricesAndAchievementsSync(targetId)
                },
                onFailure = { error ->
                    _steamImportState.value = _steamImportState.value.copy(
                        isImportingWishlist = false,
                        statusMessage = error.localizedMessage ?: "Steam-Wunschliste Import fehlgeschlagen.",
                        isError = true
                    )
                }
            )
        }
    }

    // --- DYNAMISCHES THEMING, CUSTOM DESIGN & SPRACHE (DATASTORE) ---

    fun selectTheme(style: AppThemeStyle) {
        viewModelScope.launch {
            themePreferences.setThemeStyle(style)
        }
    }

    fun saveCustomThemeColors(colors: CustomThemeColors) {
        viewModelScope.launch {
            themePreferences.saveCustomThemeColors(colors, activateCustomTheme = true)
            _snackbarMessage.value = "🎨 Eigenes Design aktiviert und gespeichert!"
        }
    }

    fun selectLanguage(language: AppLanguage) {
        viewModelScope.launch {
            themePreferences.setAppLanguage(language)
        }
    }
}
