package com.kittyspace

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val sourceDir: String,
    val splitSourceDirs: List<String>?,
    val isSystem: Boolean
)
