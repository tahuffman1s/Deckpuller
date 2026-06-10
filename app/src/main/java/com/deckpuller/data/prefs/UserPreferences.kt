package com.deckpuller.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")
private val ARCHIDEKT_USERNAME = stringPreferencesKey("archidekt_username")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val username: Flow<String?> = context.dataStore.data.map { it[ARCHIDEKT_USERNAME] }

    suspend fun setUsername(value: String) {
        context.dataStore.edit { it[ARCHIDEKT_USERNAME] = value }
    }
}
