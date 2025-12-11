package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    context: Context,
    private val settingsInteractor: SettingsInteractor,
    saveSyncManager: SaveSyncManager,
    private val sharedPreferences: FlowSharedPreferences,
) : ViewModel() {
    class Factory(
        private val context: Context,
        private val settingsInteractor: SettingsInteractor,
        private val saveSyncManager: SaveSyncManager,
        private val sharedPreferences: FlowSharedPreferences,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                context,
                settingsInteractor,
                saveSyncManager,
                sharedPreferences,
            ) as T
        }
    }

    data class State(
        val storageDirectories: Set<String> = emptySet(),
        val isSaveSyncSupported: Boolean = false,
    )

    val indexingInProgress = PendingOperationsMonitor(context).anyLibraryOperationInProgress()

    val directoryScanInProgress = PendingOperationsMonitor(context).isDirectoryScanInProgress()

    private val legacyKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
    private val collectionKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_external_folders)

    val uiState =
        sharedPreferences.getStringSet(collectionKey, emptySet())
            .asFlow()
            .flowOn(Dispatchers.IO)
            .map { folders ->
                if (folders.isEmpty()) {
                    // Fallback to legacy key for display if set is empty
                    val legacy = sharedPreferences.sharedPreferences.getString(legacyKey, null)
                    if (legacy != null) setOf(legacy) else emptySet()
                } else {
                    folders
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())
            .map { State(it, saveSyncManager.isSupported()) }

    fun addLocalStorageFolder() {
        settingsInteractor.changeLocalStorageFolder()
    }

    fun removeLocalStorageFolder(uri: String) {
        settingsInteractor.removeLocalStorageFolder(uri)
    }
    
    // Kept for compatibility if used elsewhere, but effectively alias to add
    fun changeLocalStorageFolder() {
        addLocalStorageFolder()
    }
}
