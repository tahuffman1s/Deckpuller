package com.deckpuller.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collection_cards", indices = [Index("nameKey")])
data class CollectionCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameKey: String,
    val name: String,
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val scryfallId: String?,
    val finish: String,
    val condition: String,
    val language: String,
    val binderName: String,
    val quantity: Int,
)

/** Projection for owned-by-name aggregation. */
data class OwnedTotal(val nameKey: String, val qty: Int)
