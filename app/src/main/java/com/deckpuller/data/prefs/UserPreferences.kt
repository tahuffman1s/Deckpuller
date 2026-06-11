package com.deckpuller.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")
private val ARCHIDEKT_USERNAME = stringPreferencesKey("archidekt_username")
private val COLLECTION_IMPORTED_AT = longPreferencesKey("collection_imported_at")
private val COLLECTION_COUNT = intPreferencesKey("collection_count")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val username: Flow<String?> = context.dataStore.data.map { it[ARCHIDEKT_USERNAME] }

    suspend fun setUsername(value: String) {
        context.dataStore.edit { it[ARCHIDEKT_USERNAME] = value }
    }

    val collectionImportedAt: Flow<Long?> =
        context.dataStore.data.map { it[COLLECTION_IMPORTED_AT] }

    val collectionCount: Flow<Int> =
        context.dataStore.data.map { it[COLLECTION_COUNT] ?: 0 }

    suspend fun setCollectionImported(timestamp: Long, count: Int) {
        context.dataStore.edit {
            it[COLLECTION_IMPORTED_AT] = timestamp
            it[COLLECTION_COUNT] = count
        }
    }
}
