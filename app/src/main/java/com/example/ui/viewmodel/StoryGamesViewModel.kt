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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val statusMessage: String? = null,
    val isError: Boolean = false
)

/**
 * Zustand für die Steam-Errungenschaften (Achievements) in der Spiel-Detailansicht.
 */
data class GameAchievementsState(
    val isLoading: Boolean = false,
    val achievements: List<SteamAchievement> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Zustand für den geschützten Admin-API-Tresor (`/admin_vault/api_secrets`).
 * Strikt nur für den verifizierten Admin (`nico.schaefer.hn@gmail.com`) sichtbar und lesbar.
 */
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
 * Gekapselt pro aktiver Session (`key = currentUserId`).
 */
class StoryGamesViewModel(
    private val repository: StoryGamesRepository,
    private val themePreferences: ThemePreferencesRepository,
    val currentUserId: String
) : ViewModel() {

    val isGoogleSignedIn: Boolean
        get() = repository.isGoogleSignedIn()

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

    // 2. Cloud Community Ranking Flow (`GlobalGameStats` mit 2-Tier Error-Handling)
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

    // 4. UI-Zustände für IGDB/Steam-Suche, Steam-Import, Admin-Vault, Detailansicht & Achievements
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

    init {
        viewModelScope.launch {
            // Schritt 1: Falls Admin (`nico.schaefer.hn@gmail.com`) angemeldet ist, zuerst API-Keys in den RAM laden,
            // damit der anschließende automatische Bibliotheks-Import sofort den Steam-API-Key nutzen kann!
            if (repository.isCurrentUserAdmin()) {
                loadAdminVaultSecretsInternal()
            } else {
                AppConfig.clearRuntimeAdminKeys()
            }

            // Schritt 2: Cloud-Backup wiederherstellen (falls mit Google angemeldet)
            if (repository.isGoogleSignedIn()) {
                repository.restoreUserCloudBackupIfNeeded()
            }

            // Schritt 3: Automatischer Import von Steam-Bibliothek & Wunschliste, sobald eine Steam-ID verknüpft ist
            val storedSteamId = themePreferences.linkedSteamIdFlow.first().trim()
                .ifBlank {
                    if (currentUserId.startsWith("steam_")) {
                        currentUserId.removePrefix("steam_").trim()
                    } else ""
                }

            if (storedSteamId.isNotBlank() && repository.getOwnedLibraryGamesCount() == 0) {
                syncSteamLibraryAndWishlistAutomatically(storedSteamId)
            }
        }
    }

    // --- ADMIN CLOUD VAULT (STRIKT EXKLUSIV FÜR nico.schaefer.hn@gmail.com) ---

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
                    // Falls bereits eine Steam-ID verknüpft ist, direkt Bibliothek mit dem neuen Key nachladen
                    val currentSteam = linkedSteamId.value.trim()
                    if (currentSteam.isNotBlank() && repository.getOwnedLibraryGamesCount() == 0) {
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

    // --- SPIEL-DETAILANSICHT & ERRUNGENSCHAFTEN (ACHIEVEMENTS) ---

    fun selectGameForDetail(game: LocalGame?) {
        _selectedGameForDetail.value = game
        if (game == null) {
            _selectedGameAchievements.value = GameAchievementsState()
            return
        }

        // Sofort lokal gecachte Errungenschaften anzeigen (funktioniert 100% offline!)
        val cached = SteamAchievementSerializer.fromJsonString(game.achievementsJson)
        _selectedGameAchievements.value = GameAchievementsState(
            isLoading = true,
            achievements = cached,
            errorMessage = null
        )

        // Im Hintergrund aktuelle Errungenschaften von Steam abrufen & lokal cachen
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
                    // Falls alle Errungenschaften freigeschaltet sind, auch Detail-State aktualisieren
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

    // --- DETAIL-ANSICHT SPEICHERN (BEWERTUNGEN NUR MIT GOOGLE, NOTIZEN & 100% AUCH OFFLINE) ---

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
                // Im Offline- / Nur-Steam-Modus: Notizen & lokaler Status werden gespeichert,
                // aber Bewertungen (Sterne & Weiterempfehlung) sind exklusiv für Google-Anmeldung!
                val localOnlyUpdated = game.copy(
                    personalNote = newNote.trim(),
                    is100Percent = newIs100Percent,
                    isPlayed = newIsPlayed || newIs100Percent
                )
                repository.saveGameLocally(localOnlyUpdated)
                _selectedGameForDetail.value = localOnlyUpdated
                _snackbarMessage.value = "Notiz & Status lokal (offline) gespeichert. Für Bewertungen & Community-Sync bitte mit Google anmelden."
                return@launch
            }

            val updated = game.copy(
                personalRating = newRating.coerceIn(0, 5),
                personalNote = newNote.trim(),
                isRecommended = newIsRecommended,
                is100Percent = newIs100Percent,
                isPlayed = newIsPlayed || newIs100Percent || newRating > 0
            )
            _selectedGameForDetail.value = updated
            val syncResult = repository.saveGameAndSyncToCloud(updated)
            if (syncResult.isSuccess) {
                _snackbarMessage.value = "Bewertung gespeichert & mit Community-Cloud synchronisiert!"
            } else {
                _snackbarMessage.value = "Lokal gespeichert (Cloud-Sync Fehler: ${syncResult.exceptionOrNull()?.localizedMessage ?: "Offline"})"
            }
        }
    }

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
            if (repository.isGoogleSignedIn()) {
                repository.saveGameAndSyncToCloud(updated)
                _snackbarMessage.value = if (toggled) {
                    "🏆 100% PLATIN RUN aktiviert & in die Cloud übertragen!"
                } else {
                    "100% Run Status zurückgesetzt."
                }
            } else {
                repository.saveGameLocally(updated)
                _snackbarMessage.value = if (toggled) {
                    "🏆 100% PLATIN RUN lokal (offline) aktiviert!"
                } else {
                    "100% Run Status lokal zurückgesetzt."
                }
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
            _snackbarMessage.value = "'$title' aus lokaler Datenbank entfernt."
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

    // --- STEAM LOGIN / ID VERKNÜPFUNG & AUTOMATISCHER IMPORT ---

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
            _steamImportState.value = SteamImportState(
                isImportingLibrary = true,
                isImportingWishlist = true,
                statusMessage = "Synchronisiere Steam-Bibliothek & Wunschliste automatisch...",
                isError = false
            )

            val libResult = repository.importSteamLibrary(targetId)
            val wishResult = repository.importSteamWishlist(targetId)

            val libCount = libResult.getOrDefault(0)
            val wishCount = wishResult.getOrDefault(0)

            if (libResult.isSuccess || wishResult.isSuccess) {
                val msg = buildString {
                    append("Steam-Sync abgeschlossen: ")
                    append("$libCount Spiele in Bibliothek")
                    if (wishCount > 0) append(", $wishCount auf Wunschliste")
                    if (libResult.isFailure) {
                        append(" (Hinweis Bibliothek: ${libResult.exceptionOrNull()?.localizedMessage ?: "Prüfe Steam-Privatsphäre"})")
                    }
                }
                _steamImportState.value = SteamImportState(
                    isImportingLibrary = false,
                    isImportingWishlist = false,
                    statusMessage = msg,
                    isError = libResult.isFailure && libCount == 0
                )
                if (libCount > 0) {
                    _snackbarMessage.value = "$libCount Steam-Spiele automatisch in deine Bibliothek geladen!"
                }
            } else {
                val errMsg = libResult.exceptionOrNull()?.localizedMessage
                    ?: wishResult.exceptionOrNull()?.localizedMessage
                    ?: "Steam-Synchronisation fehlgeschlagen."
                _steamImportState.value = SteamImportState(
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
            _steamImportState.value = SteamImportState(
                statusMessage = "Bitte gib zuerst deine 17-stellige SteamID64 oder deinen Steam-Profilnamen ein.",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            themePreferences.setLinkedSteamId(targetId)
            _steamImportState.value = SteamImportState(
                isImportingLibrary = true,
                statusMessage = "Importiere Owned Games aus Steam...",
                isError = false
            )
            val result = repository.importSteamLibrary(targetId)
            result.fold(
                onSuccess = { count ->
                    _steamImportState.value = SteamImportState(
                        isImportingLibrary = false,
                        statusMessage = "$count Spiele erfolgreich in Meine Bibliothek importiert!",
                        isError = false
                    )
                    _snackbarMessage.value = "$count Spiele in Meine Bibliothek geladen!"
                },
                onFailure = { error ->
                    _steamImportState.value = SteamImportState(
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
            _steamImportState.value = SteamImportState(
                statusMessage = "Bitte gib zuerst deine 17-stellige SteamID64 oder deinen Steam-Profilnamen ein.",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            themePreferences.setLinkedSteamId(targetId)
            _steamImportState.value = SteamImportState(
                isImportingWishlist = true,
                statusMessage = "Importiere Steam-Wunschliste & löse Cover-Bilder auf...",
                isError = false
            )
            val result = repository.importSteamWishlist(targetId)
            result.fold(
                onSuccess = { count ->
                    _steamImportState.value = SteamImportState(
                        isImportingWishlist = false,
                        statusMessage = "$count Spiele aus deiner Steam-Wunschliste importiert!",
                        isError = false
                    )
                },
                onFailure = { error ->
                    _steamImportState.value = SteamImportState(
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
