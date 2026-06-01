package com.kittyspace.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KittyAppDao {
    @Query("SELECT * FROM kitty_apps ORDER BY dateAdded DESC")
    fun getAllApps(): Flow<List<KittyAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: KittyAppEntity)

    @Delete
    suspend fun deleteApp(app: KittyAppEntity)

    @Query("DELETE FROM kitty_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
