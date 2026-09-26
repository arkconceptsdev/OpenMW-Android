package org.openmw.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Validates the game-data root that OpenMW receives as a filesystem path.
 *
 * The native engine cannot consume a content:// URI directly, so the Android
 * picker is accepted only when its tree resolves to shared storage that can
 * be represented as a real File path.
 */
object GameFilesValidator {
    private const val REQUIRED_INI = "Morrowind.ini"
    private const val REQUIRED_DATA_DIRECTORY = "Data Files"
    private const val REQUIRED_ESM = "Morrowind.esm"

    fun validatePath(path: String?): File? {
        if (path.isNullOrBlank()) return null

        val root = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val dataDirectory = childFile(root, REQUIRED_DATA_DIRECTORY) ?: return null
        val iniFile = childFile(root, REQUIRED_INI) ?: return null
        val esmFile = childFile(dataDirectory, REQUIRED_ESM) ?: return null

        return root.takeIf {
            it.isDirectory && iniFile.isFile && dataDirectory.isDirectory && esmFile.isFile
        }
    }

    fun validateTree(context: Context, treeUri: Uri): Result<File> {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: return Result.failure(IllegalArgumentException("The selected folder is not accessible."))

        val ini = childDocument(tree, REQUIRED_INI)
        val dataDirectory = childDocument(tree, REQUIRED_DATA_DIRECTORY)
        val esm = dataDirectory?.let { childDocument(it, REQUIRED_ESM) }
        if (ini == null || dataDirectory == null || !dataDirectory.isDirectory || esm == null || !esm.isFile) {
            return Result.failure(
                IllegalArgumentException(
                    "Select a folder containing Morrowind.ini and Data Files/Morrowind.esm."
                )
            )
        }

        val path = resolveExternalStorageTree(treeUri)
            ?: return Result.failure(
                IllegalArgumentException(
                    "Select Morrowind from shared device storage so OpenMW can access it."
                )
            )

        val root = validatePath(path.absolutePath)
            ?: return Result.failure(
                IllegalArgumentException(
                    "The selected folder could not be opened as game data."
                )
            )

        return Result.success(root)
    }

    fun persistTreePermission(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
    }

    private fun childFile(parent: File, name: String): File? {
        return parent.listFiles()?.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }
    }

    private fun childDocument(parent: DocumentFile, name: String): DocumentFile? {
        return parent.listFiles().firstOrNull {
            it.name?.equals(name, ignoreCase = true) == true
        }
    }

    private fun resolveExternalStorageTree(treeUri: Uri): File? {
        if (!DocumentsContract.isTreeUri(treeUri)) return null

        val documentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return null
        val parts = documentId.split(":", limit = 2)
        val volume = parts.firstOrNull() ?: return null
        val relativePath = parts.getOrNull(1).orEmpty()
        val volumeRoot = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage/$volume")
        }

        return runCatching {
            File(volumeRoot, relativePath).canonicalFile
        }.getOrNull()
    }
}