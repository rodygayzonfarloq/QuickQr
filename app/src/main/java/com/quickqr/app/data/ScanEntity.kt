package com.quickqr.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One scanned (or generated-and-later-rescanned) code kept in local history. */
@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val format: Int,
    val timestamp: Long,
    val isFavorite: Boolean = false
)
