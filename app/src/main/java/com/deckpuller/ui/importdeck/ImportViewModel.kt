package com.deckpuller.ui.importdeck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.prefs.UserPreferences
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.DeckSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Error(val message: String) : ImportUiState
    data class Imported(val deckId: Long) : ImportUiState
}

class ImportViewModel(
    private val repository: DeckRepository,
    userPreferences: UserPreferences,
) : ViewModel() {

    private val prefs = userPreferences

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<DeckSummary>>(emptyList())
    val results: StateFlow<List<DeckSummary>> = _results.asStateFlow()

    val savedUsername: StateFlow<String?> =
        userPreferences.username.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun import(url: String) {
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                ImportUiState.Imported(repository.importDeck(url))
            } catch (e: InvalidDeckUrlException) {
                ImportUiState.Error("That doesn't look like an Archidekt deck URL.")
            } catch (e: Exception) {
                ImportUiState.Error("Couldn't import the deck. Check your connection and try again.")
            }
        }
    }

    fun importSummary(summary: DeckSummary) {
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                ImportUiState.Imported(
                    repository.importDeckById(
                        summary.archidektId,
                        "https://archidekt.com/decks/${summary.archidektId}",
                    ),
                )
            } catch (e: Exception) {
                ImportUiState.Error("Couldn't import that deck. Try again.")
            }
        }
    }

    fun findMyDecks(username: String) {
        viewModelScope.launch {
            prefs.setUsername(username)
            _state.value = ImportUiState.Loading
            try {
                _results.value = repository.searchDecks(username)
                _state.value = ImportUiState.Idle
            } catch (e: Exception) {
                _state.value = ImportUiState.Error("Couldn't reach Archidekt. Check the username and your connection.")
            }
        }
    }

    fun dismissError() { _state.value = ImportUiState.Idle }
}
