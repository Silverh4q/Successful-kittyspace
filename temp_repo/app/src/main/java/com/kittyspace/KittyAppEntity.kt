package com.kittyspace

data class KittyAppEntity(
    val packageName: String,
    val appName: String,
    val sourceDir: String = "",
    val isUnreal: Boolean = false
)
