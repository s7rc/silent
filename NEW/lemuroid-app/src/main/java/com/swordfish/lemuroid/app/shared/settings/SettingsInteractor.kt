package com.swordfish.lemuroid.app.shared.settings

import android.content.Context
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.storage.cache.CacheCleanerWork
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager

class SettingsInteractor(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    fun changeLocalStorageFolder() {
        StorageFrameworkPickerLauncher.pickFolder(context)
    }

    fun removeLocalStorageFolder(uriString: String) {
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val key = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_external_folders)
        val currentSet = prefs.getStringSet(key, mutableSetOf())?.toMutableSet()

        if (currentSet != null && currentSet.remove(uriString)) {
            prefs.edit().putStringSet(key, currentSet).apply()

            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    android.net.Uri.parse(uriString),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
        }
    }

    fun resetAllSettings() {
        SharedPreferencesHelper.getLegacySharedPreferences(context).edit().clear().apply()
        SharedPreferencesHelper.getSharedPreferences(context).edit().clear().apply()
        LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
        CacheCleanerWork.enqueueCleanCacheAll(context.applicationContext)
        deleteDownloadedCores()
    }

    private fun deleteDownloadedCores() {
        directoriesManager.getCoresDirectory()
            .listFiles()
            ?.forEach { runCatching { it.deleteRecursively() } }
    }
}
