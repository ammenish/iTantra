package com.mirage.itantra.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for iTantra local storage.
 *
 * Version 1: Initial schema with messages table.
 */
@Database(
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ITantraDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        const val DATABASE_NAME = "itantra_db"
    }
}
