package org.openmw.utils

import java.io.File

internal enum class StorageRootMigrationWarning {
    BOTH_ROOTS_EXIST,
    LEGACY_PATH_NOT_DIRECTORY,
    DESTINATION_PATH_NOT_DIRECTORY,
    MIGRATION_FAILED
}

internal data class StorageRootMigrationResult(
    val storageRoot: File,
    val warning: StorageRootMigrationWarning? = null
)

internal object StorageRootMigration {
    const val LEGACY_ROOT_NAME = "Alpha3"
    const val NEW_ROOT_NAME = "OpenMW_Android"

    fun migrate(
        externalStorageRoot: File,
        rename: (File, File) -> Boolean = { source, destination -> source.renameTo(destination) }
    ): StorageRootMigrationResult {
        val legacyRoot = File(externalStorageRoot, LEGACY_ROOT_NAME)
        val newRoot = File(externalStorageRoot, NEW_ROOT_NAME)

        if (newRoot.exists() && !newRoot.isDirectory) {
            return StorageRootMigrationResult(
                newRoot,
                StorageRootMigrationWarning.DESTINATION_PATH_NOT_DIRECTORY
            )
        }
        if (!legacyRoot.exists()) {
            return StorageRootMigrationResult(newRoot)
        }
        if (!legacyRoot.isDirectory) {
            return StorageRootMigrationResult(
                newRoot,
                StorageRootMigrationWarning.LEGACY_PATH_NOT_DIRECTORY
            )
        }
        if (newRoot.exists()) {
            return StorageRootMigrationResult(newRoot, StorageRootMigrationWarning.BOTH_ROOTS_EXIST)
        }
        if (!rename(legacyRoot, newRoot)) {
            return StorageRootMigrationResult(newRoot, StorageRootMigrationWarning.MIGRATION_FAILED)
        }

        return StorageRootMigrationResult(newRoot)
    }
}