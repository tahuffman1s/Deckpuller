package com.deckpuller.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Only one deck exists at a time, so the row id is fixed.
const val CURRENT_DECK_ID = 1L

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: Long = CURRENT_DECK_ID,
    val name: String,
    val importedAt: Long,
)
