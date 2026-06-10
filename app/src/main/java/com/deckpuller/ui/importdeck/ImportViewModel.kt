package com.deckpuller.ui.importdeck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.repository.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: DeckRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun import(url: String) {
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                repository.importDeck(url)
                ImportUiState.Idle
            } catch (e: InvalidDeckUrlException) {
                ImportUiState.Error("That doesn't look like an Archidekt deck URL.")
            } catch (e: Exception) {
                ImportUiState.Error("Couldn't import the deck. Check your connection and try again.")
            }
        }
    }

    fun dismissError() {
        _state.value = ImportUiState.Idle
    }
}
