package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.streammaster.tv.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin Hilt bridge so AppNavHost can pass the app's [BackupManager] into
 * DriveScreen (which takes plain `backupExport` / `backupImport` lambdas,
 * not a ViewModel). BackupManager itself is @Inject-constructible, so no
 * provider was added to AppModule for it.
 */
@HiltViewModel
class DriveBackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {
    suspend fun exportBackup(): String = backupManager.export()
    suspend fun importBackup(text: String): Boolean = backupManager.import(text)
}
