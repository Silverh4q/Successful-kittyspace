package com.kittyspace.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [KittyAppEntity::class], version = 1, exportSchema = false)
abstract class KittyDatabase : RoomDatabase() {
    abstract fun kittyAppDao(): KittyAppDao

    companion object {
        @Volatile
        private var INSTANCE: KittyDatabase? = null

        fun getDatabase(context: Context): KittyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KittyDatabase::class.java,
                    "kitty_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
