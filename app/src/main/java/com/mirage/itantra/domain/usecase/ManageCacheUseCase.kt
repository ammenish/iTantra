package com.mirage.itantra.domain.usecase

import android.content.Context
import android.util.Log
import com.mirage.itantra.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * UseCase responsible for maintaining a tiny storage footprint.
 * It deletes old messages from the database and clears the application's temporary cache directory.
 */
class ManageCacheUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository
) {
    /**
     * Executes the cache cleanup process.
     * @param maxMessagesToKeep The maximum number of recent messages to retain in the database.
     */
    suspend operator fun invoke(maxMessagesToKeep: Int = 50) = withContext(Dispatchers.IO) {
        try {
            Log.d("ManageCacheUseCase", "Starting cache cleanup...")

            // 1. Prune the database to keep only the latest messages
            messageRepository.pruneOldMessages(maxMessagesToKeep)
            Log.d("ManageCacheUseCase", "Pruned database to retain latest $maxMessagesToKeep messages.")

            // 2. Clean the physical cache directory
            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.isDirectory) {
                val deletedFilesCount = deleteDirectoryRecursively(cacheDir, deleteRoot = false)
                Log.d("ManageCacheUseCase", "Cleared $deletedFilesCount temporary files/folders from cacheDir.")
            }
            
            // 3. Clean external cache dir if available (often used by TTS or other SDKs)
            val extCacheDir = context.externalCacheDir
            if (extCacheDir != null && extCacheDir.isDirectory) {
                val deletedExtCount = deleteDirectoryRecursively(extCacheDir, deleteRoot = false)
                Log.d("ManageCacheUseCase", "Cleared $deletedExtCount temporary files from externalCacheDir.")
            }

            Log.d("ManageCacheUseCase", "Cache cleanup completed successfully.")
        } catch (e: Exception) {
            Log.e("ManageCacheUseCase", "Error during cache cleanup", e)
        }
    }

    /**
     * Recursively deletes files and directories.
     * @param root The directory to start deleting from.
     * @param deleteRoot If true, the root directory itself is also deleted.
     * @return The number of files/directories deleted.
     */
    private fun deleteDirectoryRecursively(root: File, deleteRoot: Boolean = true): Int {
        var count = 0
        if (root.isDirectory) {
            val children = root.listFiles()
            if (children != null) {
                for (child in children) {
                    count += deleteDirectoryRecursively(child, true)
                }
            }
        }
        if (deleteRoot) {
            if (root.delete()) {
                count++
            }
        }
        return count
    }
}
