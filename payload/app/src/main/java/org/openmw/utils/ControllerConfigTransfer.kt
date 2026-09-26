package org.openmw.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import org.openmw.Constants
import org.openmw.ui.controls.UIStateManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ControllerConfigExportException(
    val destinationMayRemain: Boolean,
    val destinationWriteStarted: Boolean,
    cause: Exception
) : IOException(
    buildString {
        append("Export failed: ")
        append(cause.localizedMessage?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName)
        if (destinationMayRemain && destinationWriteStarted) {
            append(" The selected location may contain a partial archive; delete it before retrying.")
        } else if (destinationMayRemain) {
            append(" An empty export document may remain at the selected location; remove it before retrying.")
        } else if (destinationWriteStarted) {
            append(" The incomplete destination was removed.")
        } else {
            append(" No archive was saved.")
        }
    },
    cause
)

object ControllerConfigTransfer {
    private const val TAG = "ControllerConfigTransfer"

    suspend fun export(context: Context, destination: Uri) = withContext(Dispatchers.IO) {
        try {
            UIStateManager.awaitPendingButtonSave()
            exportToDestination(
                cacheDirectory = { context.cacheDir },
                sourceDirectories = ::controllerDirectories,
                openDestination = {
                    context.contentResolver.openOutputStream(destination, "w")
                },
                deleteDestination = {
                    context.contentResolver.delete(destination, null, null) > 0
                }
            )
        } catch (failure: ControllerConfigExportException) {
            Log.e(
                TAG,
                "Controller configuration export failed; " +
                    "destinationMayRemain=${failure.destinationMayRemain}, " +
                    "destinationWriteStarted=${failure.destinationWriteStarted}",
                failure
            )
            throw failure
        }
    }

    /**
     * Build and validate the archive locally before opening the document-provider stream.
     * CreateDocument has already created the destination when this runs, so every failure
     * still attempts to remove that initially empty document.
     */
    internal fun exportToDestination(
        cacheDirectory: () -> File,
        sourceDirectories: () -> Pair<File, File>,
        openDestination: () -> OutputStream?,
        deleteDestination: () -> Boolean,
        mappingValidator: (File) -> Unit = ControllerConfigArchive::validateInputMapping
    ) {
        var stagedArchive: File? = null
        var destinationWriteStarted = false
        try {
            val cacheDir = cacheDirectory()
            if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
                throw IOException("Could not prepare temporary controller archive storage.")
            }

            val (configDir, uiDir) = sourceDirectories()
            val archiveFile = File.createTempFile("controller-config-export-", ".zip", cacheDir)
            stagedArchive = archiveFile
            FileOutputStream(archiveFile).use { output ->
                ControllerConfigArchive.writeArchive(output, configDir, uiDir, mappingValidator)
            }
            if (archiveFile.length() == 0L) {
                throw IOException("The controller archive was empty.")
            }

            val output = openDestination()
                ?: throw IOException("Could not open the selected export destination.")
            output.use { destinationOutput ->
                FileInputStream(archiveFile).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        destinationWriteStarted = true
                        destinationOutput.write(buffer, 0, count)
                    }
                }
            }
        } catch (failure: Throwable) {
            val destinationRemoved = try {
                deleteDestination()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
                false
            }
            if (failure is CancellationException) throw failure
            if (failure !is Exception) throw failure
            throw ControllerConfigExportException(
                destinationMayRemain = !destinationRemoved,
                destinationWriteStarted = destinationWriteStarted,
                cause = failure
            )
        } finally {
            stagedArchive?.delete()
        }
    }

    suspend fun importConfig(context: Context, source: Uri) = withContext(Dispatchers.IO) {
        val (configDir, uiDir) = controllerDirectories()
        val stagingDir = File(context.cacheDir, "controller-config-import-${UUID.randomUUID()}")
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException("Could not open the selected controller archive.")
            val archive = ControllerConfigArchive.extractAndValidate(input, stagingDir)
            ControllerConfigArchive.install(archive, configDir, uiDir)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun controllerDirectories(): Pair<File, File> {
        if (Constants.USER_CONFIG.isBlank() || Constants.USER_FILE_STORAGE.isBlank()) {
            throw IOException("The OpenMW configuration directory is not ready.")
        }
        val configDir = File(Constants.USER_CONFIG)
        val uiDir = File(Constants.USER_FILE_STORAGE, "OpenMW/ui")
        if (!configDir.isAbsolute || !uiDir.isAbsolute) {
            throw IOException("The OpenMW configuration directory is invalid.")
        }
        return configDir to uiDir
    }
}