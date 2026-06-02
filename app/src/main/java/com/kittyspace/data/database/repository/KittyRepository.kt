package com.kittyspace.data.repository

import com.kittyspace.data.database.KittyAppDao
import com.kittyspace.data.database.KittyAppEntity
import kotlinx.coroutines.flow.Flow

class KittyRepository(private val kittyAppDao: KittyAppDao) {
    val allKittyApps: Flow<List<KittyAppEntity>> = kittyAppDao.getAllApps()

    suspend fun addAppToKittySpace(packageName: String, appName: String, sourceDir: String) {
        val entity = KittyAppEntity(
            packageName = packageName,
            appName = appName,
            sourceDir = sourceDir
        )
        kittyAppDao.insertApp(entity)
    }

    suspend fun removeAppFromKittySpace(packageName: String) {
        kittyAppDao.deleteByPackageName(packageName)
    }
}
