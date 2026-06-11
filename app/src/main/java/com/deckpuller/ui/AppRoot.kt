package com.deckpuller.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.deckpuller.ui.decklist.DeckListScreen
import com.deckpuller.ui.decklist.DeckListViewModel
import com.deckpuller.ui.importdeck.AddDeckScreen
import com.deckpuller.ui.importdeck.ImportUiState
import com.deckpuller.ui.importdeck.ImportViewModel
import com.deckpuller.ui.collection.CollectionRoute
import com.deckpuller.ui.pull.PullRoute
import com.deckpuller.ui.settings.SettingsRoute
import com.deckpuller.ui.shopping.ShoppingListRoute
import com.deckpuller.ui.update.UpdateGate

private const val DECK_LIST = "deckList"
private const val ADD_DECK = "addDeck"
private const val PULL = "pull"
private const val SETTINGS = "settings"
private const val COLLECTION = "collection"
private const val SHOPPING = "shopping"

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = DECK_LIST) {
        composable(DECK_LIST) {
            val viewModel: DeckListViewModel = hiltViewModel()
            val items by viewModel.items.collectAsStateWithLifecycle()
            DeckListScreen(
                decks = items,
                onDeckClick = { id: Long -> navController.navigate("$PULL/$id") },
                onAddDeck = { navController.navigate(ADD_DECK) },
                onDeleteDeck = viewModel::delete,
                onSettings = { navController.navigate(SETTINGS) },
                onCollection = { navController.navigate(COLLECTION) },
            )
        }

        composable(COLLECTION) {
            CollectionRoute(onBack = { navController.popBackStack() })
        }

        composable(SETTINGS) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }

        composable(ADD_DECK) {
            val viewModel: ImportViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val results by viewModel.results.collectAsStateWithLifecycle()
            val savedUsername by viewModel.savedUsername.collectAsStateWithLifecycle()

            LaunchedEffect(state) {
                val s = state
                if (s is ImportUiState.Imported) {
                    navController.navigate("$PULL/${s.deckId}") {
                        popUpTo(DECK_LIST)
                    }
                    viewModel.dismissError()
                }
            }

            AddDeckScreen(
                state = state,
                results = results,
                savedUsername = savedUsername,
                onImportUrl = viewModel::import,
                onFindMyDecks = viewModel::findMyDecks,
                onPickDeck = viewModel::importSummary,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "$PULL/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: 0L
            PullRoute(
                onBack = { navController.popBackStack() },
                onShoppingList = { navController.navigate("$SHOPPING/$deckId") },
            )
        }

        composable(
            route = "$SHOPPING/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
        ) {
            ShoppingListRoute(onBack = { navController.popBackStack() })
        }
    }

    // Checks GitHub for a newer release and prompts to install; no-op when current.
    UpdateGate()
}
