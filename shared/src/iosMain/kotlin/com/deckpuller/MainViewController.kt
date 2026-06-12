package com.deckpuller

import androidx.compose.ui.window.ComposeUIViewController

/** Hosts the shared [App] in a UIViewController for the SwiftUI app shell to embed. */
fun MainViewController() = ComposeUIViewController { App() }
