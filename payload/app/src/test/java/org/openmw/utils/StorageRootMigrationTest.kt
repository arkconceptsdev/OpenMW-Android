package org.openmw.utils

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageRootMigrationTest {
    @Test
    fun newInstallSelectsNewRootWithoutCreatingIt() {
        val externalRoot = Files.createTempDirectory("storage-root-new-install").toFile()
        try {
            val result = StorageRootMigration.migrate(externalRoot)
            val newRoot = File(externalRoot, StorageRootMigration.NEW_ROOT_NAME)

            assertEquals(newRoot, result.storageRoot)
            assertEquals(null, result.warning)
            assertFalse(newRoot.exists())
        } finally {
            externalRoot.deleteRecursively()
        }
    }

    @Test
    fun legacyRootIsRenamedAndAllExistingFilesArePreserved() {
        val externalRoot = Files.createTempDirectory("storage-root-legacy").toFile()
        try {
            val legacyRoot = File(externalRoot, StorageRootMigration.LEGACY_ROOT_NAME)
            val legacyConfig = File(legacyRoot, "config/input_v3.xml").apply {
                parentFile.mkdirs()
                writeText("<Controller />")
            }
            val expectedContents = legacyConfig.readText()

            val result = StorageRootMigration.migrate(externalRoot)
            val migratedConfig =
                File(result.storageRoot, "config/input_v3.xml")

            assertEquals(null, result.warning)
            assertFalse(legacyRoot.exists())
            assertTrue(migratedConfig.isFile)
            assertEquals(expectedContents, migratedConfig.readText())
        } finally {
            externalRoot.deleteRecursively()
        }
    }

    @Test
    fun whenBothRootsExistNeitherIsOverwrittenOrDeleted() {
        val externalRoot = Files.createTempDirectory("storage-root-conflict").toFile()
        try {
            val legacySentinel = File(
                File(externalRoot, StorageRootMigration.LEGACY_ROOT_NAME),
                "legacy.txt"
            ).apply {
                parentFile.mkdirs()
                writeText("legacy")
            }
            val currentSentinel = File(
                File(externalRoot, StorageRootMigration.NEW_ROOT_NAME),
                "current.txt"
            ).apply {
                parentFile.mkdirs()
                writeText("current")
            }

            val result = StorageRootMigration.migrate(externalRoot)

            assertEquals(StorageRootMigrationWarning.BOTH_ROOTS_EXIST, result.warning)
            assertEquals("legacy", legacySentinel.readText())
            assertEquals("current", currentSentinel.readText())
        } finally {
            externalRoot.deleteRecursively()
        }
    }

    @Test
    fun failedRenameLeavesLegacyRootUntouched() {
        val externalRoot = Files.createTempDirectory("storage-root-rename-failure").toFile()
        try {
            val legacySentinel = File(
                File(externalRoot, StorageRootMigration.LEGACY_ROOT_NAME),
                "keep.txt"
            ).apply {
                parentFile.mkdirs()
                writeText("keep")
            }

            val result = StorageRootMigration.migrate(externalRoot) { _, _ -> false }

            assertEquals(StorageRootMigrationWarning.MIGRATION_FAILED, result.warning)
            assertTrue(legacySentinel.isFile)
            assertEquals("keep", legacySentinel.readText())
            assertFalse(result.storageRoot.exists())
        } finally {
            externalRoot.deleteRecursively()
        }
    }
}