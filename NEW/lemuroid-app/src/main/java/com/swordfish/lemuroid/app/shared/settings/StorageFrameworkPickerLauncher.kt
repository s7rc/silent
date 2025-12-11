package com.swordfish.lemuroid.app.shared.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.utils.android.displayErrorDialog
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import javax.inject.Inject

class StorageFrameworkPickerLauncher : RetrogradeActivity() {
    @Inject
    lateinit var directoriesManager: DirectoriesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    this.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    this.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    this.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                }
            try {
                startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)
            } catch (e: Exception) {
                showStorageAccessFrameworkNotSupportedDialog()
            }
        }
    }

    private fun showStorageAccessFrameworkNotSupportedDialog() {
        val message = getString(R.string.dialog_saf_not_found, directoriesManager.getInternalRomsDirectory())
        val actionLabel = getString(R.string.ok)
        displayErrorDialog(message, actionLabel) { finish() }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        resultData: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK) {
            val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
            val legacyKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
            val collectionKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_external_folders)

            val newValue = resultData?.data

            if (newValue != null) {
                // 1. Grant Permission (Non-Destructive)
                updatePersistableUris(newValue)

                // 2. Update Set (Accumulate URIs)
                val existingSet = sharedPreferences.getStringSet(collectionKey, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                
                // Migration: If set is empty, check legacy logic to preserve existing folder
                if (existingSet.isEmpty()) {
                    val legacyValue = sharedPreferences.getString(legacyKey, null)
                    if (legacyValue != null) {
                        existingSet.add(legacyValue)
                    }
                }
                
                // Add new selection
                existingSet.add(newValue.toString())

                sharedPreferences.edit().apply {
                    this.putStringSet(collectionKey, existingSet)
                    // Keep legacy key updated for safety/fallback
                    this.putString(legacyKey, newValue.toString())
                    this.apply()
                }
            }

            startLibraryIndexWork()
        }
        finish()
    }

    private fun updatePersistableUris(uri: Uri) {
        // We now support multiple directories, so we do NOT revoke old permissions.
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun startLibraryIndexWork() {
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 1

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, StorageFrameworkPickerLauncher::class.java))
        }
    }
}
