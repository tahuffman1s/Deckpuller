package com.deckpuller

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.lifecycleScope
import com.deckpuller.data.CollectionImporter
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.toUserMessage
import com.deckpuller.ui.AppRoot
import com.deckpuller.ui.theme.DeckPullerTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImportEntryPoint {
        fun collectionImporter(): CollectionImporter
        fun collectionRepository(): CollectionRepository
    }

    @Suppress("DEPRECATION")
    private fun handleIncomingCsv(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri == null) return
        val entry = EntryPointAccessors.fromApplication(applicationContext, ImportEntryPoint::class.java)
        lifecycleScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) { entry.collectionImporter().readText(uri) }
                entry.collectionRepository().importCsv(text, System.currentTimeMillis())
            }
            val msg = result.fold(
                onSuccess = { it.toUserMessage() },
                onFailure = { "Import failed: ${it.message ?: it.javaClass.simpleName}" },
            )
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCsv(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars so the app background shows through them.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            DeckPullerTheme {
                // Some OEMs (e.g. Samsung) keep a translucent contrast scrim on the
                // 3-button nav bar; disabling contrast enforcement lets the app
                // background show through cleanly so the bar matches the screen.
                val view = LocalView.current
                if (!view.isInEditMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        window.isNavigationBarContrastEnforced = false
                        window.isStatusBarContrastEnforced = false
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
        handleIncomingCsv(intent)
    }
}
