package org.openmw.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ControllerConfigArchiveTest {
    private val validControllerMapping = """
        <?xml version="1.0" encoding="utf-8"?>
        <Controller>
          <Control name="move-forward">
            <KeyBinder key="W" />
            <JoystickButtonBinder button="0" />
          </Control>
        </Controller>
    """.trimIndent()

    @Test
    fun exportStagesAndValidatesArchiveBeforeOpeningDestination() {
        val root = Files.createTempDirectory("controller-config-export-test").toFile()
        try {
            val cache = File(root, "cache")
            val config = File(root, "config").apply { mkdirs() }
            val ui = File(root, "ui").apply { mkdirs() }
            File(config, "input_v3.xml").writeText(validControllerMapping)

            var destination: ByteArrayOutputStream? = null
            var deleteAttempted = false
            ControllerConfigTransfer.exportToDestination(
                cacheDirectory = { cache },
                sourceDirectories = { config to ui },
                openDestination = { ByteArrayOutputStream().also { destination = it } },
                deleteDestination = {
                    deleteAttempted = true
                    true
                },
                mappingValidator = ::validateMappingForUnitTests
            )

            assertFalse(deleteAttempted)
            val bytes = destination?.toByteArray() ?: throw AssertionError("Export destination was not opened.")
            val entries = mutableListOf<String>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries += entry.name
                    zip.closeEntry()
                }
            }
            assertTrue("manifest.txt" in entries)
            assertTrue("config/input_v3.xml" in entries)
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidSourceDoesNotOpenDestinationAndDeletesPickerCreatedDocument() {
        val root = Files.createTempDirectory("controller-config-export-invalid-test").toFile()
        try {
            var selectedDocumentExists = true
            var destinationOpened = false
            val failure = runCatching {
                ControllerConfigTransfer.exportToDestination(
                    cacheDirectory = { File(root, "cache") },
                    sourceDirectories = { File(root, "missing-config") to File(root, "ui") },
                    openDestination = {
                        destinationOpened = true
                        ByteArrayOutputStream()
                    },
                    deleteDestination = {
                        selectedDocumentExists = false
                        true
                    }
                )
            }.exceptionOrNull()

            assertTrue(failure is ControllerConfigExportException)
            assertFalse(destinationOpened)
            assertFalse(selectedDocumentExists)
            assertFalse((failure as ControllerConfigExportException).destinationMayRemain)
            assertTrue(failure.message.orEmpty().contains("input mapping"))
            assertTrue(File(root, "cache").listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun preflightFailureReportsBlankDocumentWhenProviderCannotDeleteIt() {
        val root = Files.createTempDirectory("controller-config-export-empty-test").toFile()
        try {
            var destinationOpened = false
            var cleanupAttempted = false
            val failure = runCatching {
                ControllerConfigTransfer.exportToDestination(
                    cacheDirectory = { File(root, "cache") },
                    sourceDirectories = { File(root, "missing-config") to File(root, "ui") },
                    openDestination = {
                        destinationOpened = true
                        ByteArrayOutputStream()
                    },
                    deleteDestination = {
                        cleanupAttempted = true
                        throw UnsupportedOperationException("Delete not supported")
                    }
                )
            }.exceptionOrNull()

            assertTrue(failure is ControllerConfigExportException)
            val exportFailure = failure as ControllerConfigExportException
            assertFalse(destinationOpened)
            assertTrue(cleanupAttempted)
            assertTrue(exportFailure.destinationMayRemain)
            assertFalse(exportFailure.destinationWriteStarted)
            assertTrue(exportFailure.message.orEmpty().contains("empty export document"))
            assertFalse(exportFailure.message.orEmpty().contains("partial archive"))
            assertEquals(1, exportFailure.cause?.suppressed?.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedDestinationWriteWarnsWhenProviderCannotDeletePartialArchive() {
        val root = Files.createTempDirectory("controller-config-export-partial-test").toFile()
        try {
            val cache = File(root, "cache")
            val config = File(root, "config").apply { mkdirs() }
            val ui = File(root, "ui").apply { mkdirs() }
            File(config, "input_v3.xml").writeText(validControllerMapping)
            val partialBytes = ByteArrayOutputStream()
            var remainingBytes = 12
            var destinationClosed = false
            var deleteAttempted = false

            val failure = runCatching {
                ControllerConfigTransfer.exportToDestination(
                    cacheDirectory = { cache },
                    sourceDirectories = { config to ui },
                    openDestination = {
                        object : OutputStream() {
                            override fun write(value: Int) {
                                if (remainingBytes == 0) throw IOException("Storage is full.")
                                partialBytes.write(value)
                                remainingBytes--
                            }

                            override fun close() {
                                destinationClosed = true
                            }
                        }
                    },
                    deleteDestination = {
                        deleteAttempted = true
                        false
                    },
                    mappingValidator = ::validateMappingForUnitTests
                )
            }.exceptionOrNull()

            assertTrue(failure is ControllerConfigExportException)
            assertTrue(destinationClosed)
            assertTrue(deleteAttempted)
            assertTrue(partialBytes.size() > 0)
            val exportFailure = failure as ControllerConfigExportException
            assertTrue(exportFailure.destinationMayRemain)
            assertTrue(exportFailure.destinationWriteStarted)
            assertTrue(failure.message.orEmpty().contains("partial archive"))
            assertTrue(failure.message.orEmpty().contains("delete it before retrying"))
            assertTrue(cache.listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun exportImportRoundTripIncludesReferencedImagesAndReplacesOldConfig() {
        val root = Files.createTempDirectory("controller-config-test").toFile()
        try {
            val sourceConfig = File(root, "source/config").apply { mkdirs() }
            val sourceUi = File(root, "source/OpenMW/ui").apply { mkdirs() }
            val mappingXml = validControllerMapping
            File(sourceConfig, "input_v3.xml").writeText(mappingXml)
            File(sourceUi, "UI.cfg").writeText(uiLine(1, "custom.png"))
            File(sourceUi, "button_configs.json").writeText(buttonConfigJson("custom.png"))
            val imageBytes = byteArrayOf(1, 2, 3, 4)
            File(sourceUi, "custom.png").writeBytes(imageBytes)

            val archiveBytes = ByteArrayOutputStream()
            ControllerConfigArchive.writeArchive(
                archiveBytes,
                sourceConfig,
                sourceUi,
                ::validateMappingForUnitTests
            )
            val staging = File(root, "staging")
            val archive = ControllerConfigArchive.extractAndValidate(
                ByteArrayInputStream(archiveBytes.toByteArray()),
                staging,
                ::validateMappingForUnitTests
            )

            val targetConfig = File(root, "target/config").apply { mkdirs() }
            val targetUi = File(root, "target/OpenMW/ui").apply { mkdirs() }
            File(targetConfig, "input_v3.xml").writeText("<Controller><Old /></Controller>")
            File(targetUi, "UI.cfg").writeText(uiLine(2, "old.png"))
            File(targetUi, "button_configs.json").writeText("[]")
            File(targetUi, "old.png").writeBytes(byteArrayOf(9, 9))

            ControllerConfigArchive.install(archive, targetConfig, targetUi)

            assertEquals(mappingXml, File(targetConfig, "input_v3.xml").readText())
            assertEquals(uiLine(1, "custom.png"), File(targetUi, "UI.cfg").readText())
            assertArrayEquals(imageBytes, File(targetUi, "custom.png").readBytes())
            assertTrue(
                File(targetUi, "button_configs.json").readText()
                    .contains("file://${targetUi.canonicalPath}/custom.png")
            )
            assertFalse(File(targetUi, "old.png").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidArchiveIsRejectedBeforeExistingFilesCanBeApplied() {
        val root = Files.createTempDirectory("controller-config-invalid-test").toFile()
        try {
            val existingConfig = File(root, "config/input_v3.xml").apply {
                parentFile.mkdirs()
                writeText("<Controller><Existing /></Controller>")
            }
            val archiveBytes = zipOf(
                "manifest.txt" to "format=openmw-android-controller-config\nversion=1\n",
                "config/input_v3.xml" to "<Controller>"
            )

            val failure = runCatching {
                ControllerConfigArchive.extractAndValidate(
                    ByteArrayInputStream(archiveBytes),
                    File(root, "staging"),
                    ::validateMappingForUnitTests
                )
            }.exceptionOrNull()

            assertTrue(failure is InvalidControllerConfigException)
            assertEquals("<Controller><Existing /></Controller>", existingConfig.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsZipPathTraversal() {
        val root = Files.createTempDirectory("controller-config-traversal").toFile()
        try {
            val archiveBytes = zipOf(
                "manifest.txt" to "format=openmw-android-controller-config\nversion=1\n",
                "../escape.txt" to "not allowed"
            )

            val failure = runCatching {
                ControllerConfigArchive.extractAndValidate(
                    ByteArrayInputStream(archiveBytes),
                    File(root, "staging")
                )
            }.exceptionOrNull()

            assertTrue(failure is InvalidControllerConfigException)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun uiLine(id: Int, imageName: String): String =
        "ButtonID_$id(60.0;0.2;0.3;false;false;111;#FFFFFF;0.8;/storage/emulated/0/OpenMW_Android/OpenMW/ui/$imageName;1)"

    private fun buttonConfigJson(imageName: String): String =
        """[{"type":"utility","keyCode":0,"label":"Menu","position":0,"id":1,"size":60.0,"offsetX":100.0,"offsetY":200.0,"isLocked":true,"color":"#FFFFFF","alpha":0.8,"uri":"file:///storage/emulated/0/OpenMW_Android/OpenMW/ui/$imageName","group":1}]"""

    private fun validateMappingForUnitTests(file: File) {
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isExpandEntityReferences = false
            }
            val document = factory.newDocumentBuilder().parse(file)
            if (document.documentElement?.tagName != "Controller") {
                throw InvalidControllerConfigException(
                    "The OpenMW input mapping has an unexpected format."
                )
            }
        } catch (invalid: InvalidControllerConfigException) {
            throw invalid
        } catch (failure: Exception) {
            throw InvalidControllerConfigException(
                "The OpenMW input mapping XML is invalid.",
                failure
            )
        }
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}