package com.kittyspace.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kitty_apps")
data class KittyAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val sourceDir: String,
    val dateAdded: Long = System.currentTimeMillis()
)
