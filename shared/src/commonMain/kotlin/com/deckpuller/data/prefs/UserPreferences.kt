package com.deckpuller.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferences(private val dataStore: DataStore<Preferences>) {
    private val ARCHIDEKT_USERNAME = stringPreferencesKey("archidekt_username")
    private val COLLECTION_IMPORTED_AT = longPreferencesKey("collection_imported_at")
    private val COLLECTION_COUNT = intPreferencesKey("collection_count")

    val username: Flow<String?> = dataStore.data.map { it[ARCHIDEKT_USERNAME] }

    suspend fun setUsername(value: String) {
        dataStore.edit { it[ARCHIDEKT_USERNAME] = value }
    }

    val collectionImportedAt: Flow<Long?> = dataStore.data.map { it[COLLECTION_IMPORTED_AT] }

    val collectionCount: Flow<Int> = dataStore.data.map { it[COLLECTION_COUNT] ?: 0 }

    suspend fun setCollectionImported(timestamp: Long, count: Int) {
        dataStore.edit {
            it[COLLECTION_IMPORTED_AT] = timestamp
            it[COLLECTION_COUNT] = count
        }
    }
}
