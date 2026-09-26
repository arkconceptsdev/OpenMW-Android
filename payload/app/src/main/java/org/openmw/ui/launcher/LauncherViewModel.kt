package org.openmw.ui.launcher

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.fragments.processSelectedFolder
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesValidator
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor() : ViewModel() {
    var isSelecting by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        errorMessage = null
    }

    fun selectGameFiles(context: Context, treeUri: Uri) {
        clearError()
        isSelecting = true

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                GameFilesValidator.persistTreePermission(context, treeUri)
                GameFilesValidator.validateTree(context, treeUri)
            }

            result.fold(
                onSuccess = { root ->
                    withContext(Dispatchers.IO) {
                        GameFilesPreferences.storeGameFilesPath(context, root.absolutePath)
                        processSelectedFolder(
                            context = context,
                            folder = root,
                            onUriPersisted = {}
                        )
                    }
                    isSelecting = false
                },
                onFailure = { error ->
                    errorMessage = error.message
                    isSelecting = false
                }
            )
        }
    }

    fun hasValidSavedGameFiles(path: String?): Boolean {
        return GameFilesValidator.validatePath(path) != null
    }
}