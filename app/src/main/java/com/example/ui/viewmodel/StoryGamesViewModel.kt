package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.data.cloud.GlobalGameStats
import com.example.data.local.LocalGame
import com.example.data.preferences.AppThemeStyle
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.remote.ExternalGameSearchResult
import com.example.data.remote.SteamAchievement
import com.example.data.remote.SteamAchievementSerializer
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
    val isError: Boolean = false
)

data class GameAchievementsState(
    val isLoading: Boolean = false,
    val achievements: List<SteamAchievement> = emptyList(),
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

    // 3. DataStore Preferences Flows (Dynamisches Theme & Verknüpfte Steam-ID)
    val selectedTheme: StateFlow<AppThemeStyle> = themePreferences.selectedThemeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppThemeStyle.CYBERPUNK_NEON
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

    private val _steamImportState = MutableStateFlow(SteamImportState())
    val steamImportState: StateFlow<SteamImportState> = _steamImportState.asStateFlow()

    private val _adminVaultState = MutableStateFlow(
        AdminVaultState(isAdmin = repository.isCurrentUserAdmin())
    )
    val adminVaultState: StateFlow<AdminVaultState> = _adminVaultState.asStateFlow()

    private val _selectedGameForDetail = MutableStateFlow<LocalGame?>(null)
    val selectedGameForDetail: StateFlow<LocalGame?> = _selectedGameForDetail.asStateFlow()

    private val _selectedGameAchievements = MutableStateFlow(GameAchievementsState())
    val selectedGameAchievements: StateFlow<GameAchievementsState> = _selectedGameAchievements.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var liveUpdaterJob: Job? = null

    private var lastAutoSyncedSteamId: String = ""

    init {
        viewModelScope.launch {
            if (repository.isCurrentUserAdmin()) {
                loadAdminVaultSecretsInternal()
            } else {
                AppConfig.clearRuntimeAdminKeys()
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

            // Startet den Live-Updater für Wunschliste & Bibliothek
            startLiveWishlistAndLibraryUpdater()
        }
    }

    /**
     * Hintergrund-Live-Updater:
     * Prüft alle 20 Sekunden automatisch die verknüpfte Steam-Wunschliste (und Bibliothek),
     * sodass neu auf Steam hinzugefügte Wunschlisten-Spiele sofort live in der App erscheinen!
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
                        lastLiveSyncTimeText = "Live synchronisiert (${summary.totalCount} Titel)"
                    )
                    if (summary.newlyAddedTitles.isNotEmpty()) {
                        val preview = summary.newlyAddedTitles.take(2).joinToString(", ")
                        _snackbarMessage.value = "✨ Live-Update: '$preview' neu auf deiner Wunschliste erkannt!"
                    }
                }
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
                    lastLiveSyncTimeText = "Live synchronisiert (${summary.totalCount} Titel)"
                )
                if (summary.newlyAddedTitles.isNotEmpty()) {
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
                _adminVaultState.value = _adminVaultState.value.copy(
                    isLoading = false,
                    twitchClientId = secrets?.twitchClientId ?: "",
                    twitchClientSecret = secrets?.twitchClientSecret ?: "",
                    steamApiKey = secrets?.steamApiKey ?: "",
                    statusMessage = if (secrets != null) {
                        "API-Keys sicher aus dem Cloud-Admin-Tresor geladen (nur im RAM)."
                    } else {
                        "Cloud-Tresor ist noch leer. Du kannst deine Keys jetzt sicher speichern."
                    },
                    isError = false
                )
            },
            onFailure = { err ->
                _adminVaultState.value = _adminVaultState.value.copy(
                    isLoading = false,
                    statusMessage = "Zugriff verweigert: ${err.localizedMessage}",
                    isError = true
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
                        statusMessage = "API-Keys im geschützten Cloud-Tresor gespeichert (nur für Admin lesbar)!",
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
                        statusMessage = "Fehler beim Speichern im Tresor: ${err.localizedMessage}",
                        isError = true
                    )
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

    // --- SPIEL-DETAILANSICHT & ERRUNGENSCHAFTEN (ACHIEVEMENTS) ---

    fun selectGameForDetail(game: LocalGame?) {
        _selectedGameForDetail.value = game
        if (game == null) {
            _selectedGameAchievements.value = GameAchievementsState()
            return
        }

        val cached = SteamAchievementSerializer.fromJsonString(game.achievementsJson)
        _selectedGameAchievements.value = GameAchievementsState(
            isLoading = true,
            achievements = cached,
            errorMessage = null
        )

        loadAchievementsForGame(game)
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

    fun syncSteamLibraryAndWishlistAutomatically(steamIdInput: String = linkedSteamId.value) {
        val targetId = steamIdInput.trim().ifBlank { linkedSteamId.value.trim() }
        if (targetId.isBlank()) return

        viewModelScope.launch {
            themePreferences.setLinkedSteamId(targetId)
            _steamImportState.value = _steamImportState.value.copy(
                isImportingLibrary = true,
                isImportingWishlist = true,
                statusMessage = "Lade komplette Steam-Bibliothek & Wunschliste...",
                isError = false
            )

            val libResult = repository.importSteamLibrary(targetId)
            val wishResult = repository.importSteamWishlistWithDiff(targetId)

            val libCount = libResult.getOrDefault(0)
            val wishCount = wishResult.getOrNull()?.totalCount ?: 0

            if (libResult.isSuccess || wishResult.isSuccess) {
                val msg = buildString {
                    append("Synchronisiert: ")
                    append("$libCount Spiele in Bibliothek")
                    if (wishCount > 0) append(" • $wishCount auf Wunschliste")
                    if (libResult.isFailure) {
                        append(" (${libResult.exceptionOrNull()?.localizedMessage ?: "Bitte Steam-Privatsphäre prüfen"})")
                    }
                }
                _steamImportState.value = _steamImportState.value.copy(
                    isImportingLibrary = false,
                    isImportingWishlist = false,
                    lastLiveSyncTimeText = "Live synchronisiert ($wishCount Wunschliste)",
                    statusMessage = msg,
                    isError = libResult.isFailure && libCount == 0
                )
            } else {
                val errMsg = libResult.exceptionOrNull()?.localizedMessage
                    ?: wishResult.exceptionOrNull()?.localizedMessage
                    ?: "Steam-Synchronisation fehlgeschlagen."
                _steamImportState.value = _steamImportState.value.copy(
                    isImportingLibrary = false,
                    isImportingWishlist = false,
                    statusMessage = errMsg,
                    isError = true
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
            themePreferences.setLinkedSteamId(targetId)
            _steamImportState.value = _steamImportState.value.copy(
                isImportingLibrary = true,
                statusMessage = "Importiere alle Owned Games aus deiner Steam-Bibliothek...",
                isError = false
            )
            val result = repository.importSteamLibrary(targetId)
            result.fold(
                onSuccess = { count ->
                    _steamImportState.value = _steamImportState.value.copy(
                        isImportingLibrary = false,
                        statusMessage = "Alle $count Spiele erfolgreich in Meine Bibliothek geladen!",
                        isError = false
                    )
                    _snackbarMessage.value = "$count Spiele in Meine Bibliothek geladen!"
                },
                onFailure = { error ->
                    _steamImportState.value = _steamImportState.value.copy(
                        isImportingLibrary = false,
                        statusMessage = error.localizedMessage ?: "Steam-Bibliothek Import fehlgeschlagen.",
                        isError = true
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
                statusMessage = "Importiere komplette Steam-Wunschliste...",
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
                    _snackbarMessage.value = "${summary.totalCount} Spiele auf deiner Wunschliste aktualisiert!"
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

    // --- DYNAMISCHES THEMING (DATASTORE) ---

    fun selectTheme(style: AppThemeStyle) {
        viewModelScope.launch {
            themePreferences.setThemeStyle(style)
        }
    }
}
