package org.openmw.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal class InvalidControllerConfigException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

internal data class ExtractedControllerConfig(
    val root: File,
    val entryNames: Set<String>
) {
    fun file(entryName: String): File? =
        if (entryName in entryNames) File(root, entryName) else null

    val imageNames: Set<String>
        get() = entryNames
            .filter { it.startsWith(IMAGE_ENTRY_PREFIX) }
            .map { it.removePrefix(IMAGE_ENTRY_PREFIX) }
            .toSet()

    private companion object {
        const val IMAGE_ENTRY_PREFIX = "ui/images/"
    }
}

/**
 * Reads and writes the portable OpenMW controller bundle. The archive contains the
 * engine's input mapping, the Android touch layout, its button preset file, and only
 * the PNG/GIF files referenced by those layouts.
 */
internal object ControllerConfigArchive {
    private const val MANIFEST_NAME = "manifest.txt"
    private const val MANIFEST_CONTENT =
        "format=openmw-android-controller-config\nversion=1\n"
    private const val INPUT_MAPPING = "config/input_v3.xml"
    private const val UI_LAYOUT = "ui/UI.cfg"
    private const val BUTTON_CONFIGS = "ui/button_configs.json"
    private const val IMAGE_PREFIX = "ui/images/"
    private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
    private const val MAX_FILE_COUNT = 128

    private val uiLinePattern = Regex(
        """ButtonID_(\d+)\(([\d.]+);([\d.]+);([\d.]+);(true|false);(true|false);(\d+);#([A-Fa-f0-9]+);([\d.]+);([^;]+);(\d+)(?:;([a-z_]+))?\)"""
    )
    private val imageReferencePattern =
        Regex("""(?i)([A-Za-z0-9][A-Za-z0-9._-]{0,127}\.(?:png|gif))""")
    private val safeImageNamePattern =
        Regex("""(?i)[A-Za-z0-9][A-Za-z0-9._-]{0,127}\.(?:png|gif)""")

    fun writeArchive(
        output: OutputStream,
        userConfigDir: File,
        userUiDir: File,
        mappingValidator: (File) -> Unit = ::validateInputMapping
    ) {
        val inputMapping = File(userConfigDir, "input_v3.xml")
        if (!inputMapping.isFile) {
            throw InvalidControllerConfigException("The OpenMW input mapping file is missing.")
        }
        mappingValidator(inputMapping)

        val uiLayout = File(userUiDir, "UI.cfg")
        val buttonConfigs = File(userUiDir, "button_configs.json")
        if (uiLayout.isFile) validateUiLayout(uiLayout.readText(Charsets.UTF_8))
        if (buttonConfigs.isFile) validateButtonConfigs(buttonConfigs.readText(Charsets.UTF_8))

        val imageNames = referencedImages(
            uiLayout.takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty(),
            buttonConfigs.takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty()
        )
        val images = imageNames.map { imageName ->
            requireSafeImageName(imageName)
            val image = File(userUiDir, imageName)
            if (image.canonicalFile.parentFile != userUiDir.canonicalFile || !image.isFile) {
                throw InvalidControllerConfigException(
                    "A controller image referenced by the layout is missing."
                )
            }
            imageName to image
        }

        val files = buildList {
            add(INPUT_MAPPING to inputMapping)
            if (uiLayout.isFile) add(UI_LAYOUT to uiLayout)
            if (buttonConfigs.isFile) add(BUTTON_CONFIGS to buttonConfigs)
            images.forEach { (imageName, image) -> add("$IMAGE_PREFIX$imageName" to image) }
        }
        val totalBytes = MANIFEST_CONTENT.toByteArray(Charsets.UTF_8).size +
            files.sumOf { (_, file) -> file.length() }
        if (files.size + 1 > MAX_FILE_COUNT || totalBytes > MAX_TOTAL_BYTES) {
            throw InvalidControllerConfigException(
                "The controller configuration is larger than the supported archive limit."
            )
        }

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            writeBytes(zip, MANIFEST_NAME, MANIFEST_CONTENT.toByteArray(Charsets.UTF_8))
            files.forEach { (entryName, file) -> writeFile(zip, entryName, file) }
        }
    }

    fun extractAndValidate(
        input: InputStream,
        stagingDir: File,
        mappingValidator: (File) -> Unit = ::validateInputMapping
    ): ExtractedControllerConfig {
        if (stagingDir.exists() && !stagingDir.deleteRecursively()) {
            throw IOException("Could not prepare controller import staging.")
        }
        if (!stagingDir.mkdirs() && !stagingDir.isDirectory) {
            throw IOException("Could not prepare controller import staging.")
        }

        val root = stagingDir.canonicalFile
        val seen = mutableSetOf<String>()
        var totalBytes = 0L
        var fileCount = 0

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                validateEntryName(name, entry.isDirectory)
                if (!seen.add(name)) {
                    throw InvalidControllerConfigException("The controller archive contains duplicate files.")
                }
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                fileCount += 1
                if (fileCount > MAX_FILE_COUNT) {
                    throw InvalidControllerConfigException("The controller archive contains too many files.")
                }

                val target = File(root, name).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) {
                    throw InvalidControllerConfigException("The controller archive contains an unsafe path.")
                }
                if (!target.parentFile.mkdirs() && !target.parentFile.isDirectory) {
                    throw IOException("Could not prepare controller import staging.")
                }

                var entryBytes = 0L
                BufferedOutputStream(FileOutputStream(target)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        totalBytes += read
                        if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                            throw InvalidControllerConfigException(
                                "The controller archive is larger than the supported limit."
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }

        val manifest = File(root, MANIFEST_NAME)
        if (!manifest.isFile || manifest.readText(Charsets.UTF_8) != MANIFEST_CONTENT) {
            throw InvalidControllerConfigException("This is not a supported controller configuration archive.")
        }

        val inputMapping = File(root, INPUT_MAPPING)
        if (INPUT_MAPPING !in seen || !inputMapping.isFile) {
            throw InvalidControllerConfigException("The controller archive has no OpenMW input mapping.")
        }
        mappingValidator(inputMapping)

        val uiLayout = File(root, UI_LAYOUT)
        val buttonConfigs = File(root, BUTTON_CONFIGS)
        if (UI_LAYOUT in seen) validateUiLayout(uiLayout.readText(Charsets.UTF_8))
        if (BUTTON_CONFIGS in seen) {
            validateButtonConfigs(buttonConfigs.readText(Charsets.UTF_8))
        }

        val referencedImages = referencedImages(
            uiLayout.takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty(),
            buttonConfigs.takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty()
        )
        val archivedImages = seen
            .filter { it.startsWith(IMAGE_PREFIX) }
            .map { it.removePrefix(IMAGE_PREFIX) }
            .toSet()
        if (!archivedImages.containsAll(referencedImages)) {
            throw InvalidControllerConfigException(
                "The controller archive is missing an image referenced by its layout."
            )
        }

        return ExtractedControllerConfig(root, seen)
    }

    /**
     * Applies a completely validated archive. Replacements and removals are staged beside
     * their destinations, then committed with same-filesystem renames. If any rename fails,
     * already-applied changes are rolled back from sibling backups.
     */
    fun install(
        archive: ExtractedControllerConfig,
        userConfigDir: File,
        userUiDir: File
    ) {
        val inputMapping = archive.file(INPUT_MAPPING)
            ?: throw InvalidControllerConfigException("The controller archive has no OpenMW input mapping.")
        val oldImages = referencedImages(
            readIfFile(File(userUiDir, "UI.cfg")),
            readIfFile(File(userUiDir, "button_configs.json"))
        )
        val newImages = archive.imageNames
        val buttonConfigReplacement = archive.file(BUTTON_CONFIGS)?.let { buttonConfigFile ->
            localizeButtonConfigUris(buttonConfigFile, userUiDir, newImages)
        }

        val changes = linkedMapOf<File, File?>()
        changes[File(userConfigDir, "input_v3.xml").canonicalFile] = inputMapping
        changes[File(userUiDir, "UI.cfg").canonicalFile] = archive.file(UI_LAYOUT)
        changes[File(userUiDir, "button_configs.json").canonicalFile] = buttonConfigReplacement

        newImages.forEach { imageName ->
            requireSafeImageName(imageName)
            changes[File(userUiDir, imageName).canonicalFile] =
                archive.file("$IMAGE_PREFIX$imageName")
        }
        (oldImages - newImages).forEach { imageName ->
            requireSafeImageName(imageName)
            changes.putIfAbsent(File(userUiDir, imageName).canonicalFile, null)
        }

        val stagedReplacements = mutableMapOf<File, File>()
        val temporaryFiles = mutableListOf<File>()
        val appliedChanges = mutableListOf<AppliedChange>()

        try {
            changes.forEach { (target, replacement) ->
                val parent = target.parentFile
                    ?: throw IOException("Controller configuration destination has no parent.")
                if (!parent.mkdirs() && !parent.isDirectory) {
                    throw IOException("Could not prepare the controller configuration directory.")
                }
                if (target.exists() && !target.isFile) {
                    throw IOException("A controller configuration destination is not a file.")
                }
                if (replacement != null) {
                    val stagedFile = uniqueSibling(target, "import")
                    copyFile(replacement, stagedFile)
                    stagedReplacements[target] = stagedFile
                    temporaryFiles += stagedFile
                }
            }

            changes.forEach { (target, _) ->
                var backup: File? = null
                if (target.exists()) {
                    backup = uniqueSibling(target, "backup")
                    if (!target.renameTo(backup)) {
                        throw IOException("Could not preserve the existing controller configuration.")
                    }
                }

                try {
                    val stagedFile = stagedReplacements[target]
                    if (stagedFile != null && !stagedFile.renameTo(target)) {
                        throw IOException("Could not install the imported controller configuration.")
                    }
                    appliedChanges += AppliedChange(
                        target = target,
                        backup = backup,
                        replacementInstalled = stagedFile != null
                    )
                } catch (failure: Throwable) {
                    if (backup != null && backup.exists()) {
                        restoreBackup(backup, target)
                    }
                    throw failure
                }
            }

            appliedChanges.forEach { change -> change.backup?.delete() }
        } catch (failure: Throwable) {
            appliedChanges.asReversed().forEach { change ->
                try {
                    if (change.replacementInstalled && change.target.exists() && !change.target.delete()) {
                        throw IOException("Could not remove a partially imported configuration.")
                    }
                    change.backup?.takeIf { it.exists() }?.let { restoreBackup(it, change.target) }
                } catch (rollbackFailure: Throwable) {
                    failure.addSuppressed(rollbackFailure)
                }
            }
            throw failure
        } finally {
            temporaryFiles.forEach { it.delete() }
        }
    }

    private fun validateEntryName(name: String, isDirectory: Boolean) {
        val checkedName = if (isDirectory) name.removeSuffix("/") else name
        if (checkedName.isBlank() || name.startsWith("/") || '\\' in name ||
            name.any { it.code < 0x20 } ||
            checkedName.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) {
            throw InvalidControllerConfigException("The controller archive contains an unsafe path.")
        }

        if (isDirectory) {
            if (!name.endsWith("/") || name !in setOf("config/", "ui/", "ui/images/")) {
                throw InvalidControllerConfigException("The controller archive contains an unsupported folder.")
            }
            return
        }

        val allowed = name == MANIFEST_NAME ||
            name == INPUT_MAPPING ||
            name == UI_LAYOUT ||
            name == BUTTON_CONFIGS ||
            (name.startsWith(IMAGE_PREFIX) &&
                name.removePrefix(IMAGE_PREFIX).let(::isSafeImageName))
        if (!allowed) {
            throw InvalidControllerConfigException("The controller archive contains an unsupported file.")
        }
    }

    internal fun validateInputMapping(file: File) {
        val parser = try {
            Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            }
        } catch (failure: Exception) {
            throw InvalidControllerConfigException(
                "Android could not configure safe XML validation for the OpenMW input mapping.",
                failure
            )
        }

        try {
            FileInputStream(file).use { input ->
                parser.setInput(input, null)
                var depth = 0
                var rootFound = false
                while (true) {
                    when (parser.nextToken()) {
                        XmlPullParser.DOCDECL -> throw InvalidControllerConfigException(
                            "DOCTYPE declarations are not allowed in the OpenMW input mapping."
                        )
                        XmlPullParser.START_TAG -> {
                            if (depth == 0) {
                                if (rootFound || parser.name != "Controller") {
                                    throw InvalidControllerConfigException(
                                        "The OpenMW input mapping has an unexpected format."
                                    )
                                }
                                rootFound = true
                            }
                            depth++
                        }
                        XmlPullParser.END_TAG -> depth--
                        XmlPullParser.END_DOCUMENT -> break
                    }
                }
                if (!rootFound) {
                    throw InvalidControllerConfigException(
                        "The OpenMW input mapping has an unexpected format."
                    )
                }
            }
        } catch (invalid: InvalidControllerConfigException) {
            throw invalid
        } catch (failure: Exception) {
            val location = (failure as? XmlPullParserException)
                ?.takeIf { it.lineNumber > 0 }
                ?.let { " at line ${it.lineNumber}, column ${it.columnNumber}" }
                .orEmpty()
            throw InvalidControllerConfigException(
                "The OpenMW input mapping XML is invalid$location.",
                failure
            )
        }
    }

    private fun validateUiLayout(content: String) {
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            if (!uiLinePattern.matches(line)) {
                throw InvalidControllerConfigException("The on-screen control layout is invalid.")
            }
        }
    }

    private fun validateButtonConfigs(content: String) {
        try {
            val buttons = Json.parseToJsonElement(content) as? JsonArray
                ?: throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
            val allowedKeys = setOf(
                "type", "keyCode", "label", "position", "id", "size", "offsetX",
                "offsetY", "isLocked", "color", "alpha", "uri", "group"
            )
            buttons.forEach { element ->
                val button = element as? JsonObject
                    ?: throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
                if (button.keys.any { it !in allowedKeys }) {
                    throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
                }
                requireString(button, "type")
                requireNumber(button, "keyCode", integer = true)
                requireString(button, "label")
                requireNumber(button, "position", integer = true)
                requireString(button, "color")
                requireNumber(button, "alpha")
                requireNullableString(button, "uri")
                requireOptionalNumber(button, "id", integer = true)
                requireOptionalNumber(button, "size")
                requireOptionalNumber(button, "offsetX")
                requireOptionalNumber(button, "offsetY")
                requireOptionalBoolean(button, "isLocked")
                requireOptionalNumber(button, "group", integer = true)
            }
        } catch (invalid: InvalidControllerConfigException) {
            throw invalid
        } catch (failure: Exception) {
            throw InvalidControllerConfigException("The on-screen button configuration is invalid.", failure)
        }
    }

    private fun requireString(button: JsonObject, name: String) {
        if ((button[name] as? JsonPrimitive)?.isString != true) {
            throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
        }
    }

    private fun requireNullableString(button: JsonObject, name: String) {
        val value = button[name]
        if (value == null || (value != JsonNull && (value as? JsonPrimitive)?.isString != true)) {
            throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
        }
    }

    private fun requireNumber(button: JsonObject, name: String, integer: Boolean = false) {
        val value = button[name] as? JsonPrimitive
        val valid = if (integer) value?.intOrNull != null else value?.floatOrNull != null
        if (!valid) {
            throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
        }
    }

    private fun requireOptionalNumber(button: JsonObject, name: String, integer: Boolean = false) {
        val value = button[name] ?: return
        if (value == JsonNull) return
        val primitive = value as? JsonPrimitive
        val valid = if (integer) primitive?.intOrNull != null else primitive?.floatOrNull != null
        if (!valid) {
            throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
        }
    }

    private fun requireOptionalBoolean(button: JsonObject, name: String) {
        val value = button[name] ?: return
        if (value == JsonNull) return
        if ((value as? JsonPrimitive)?.booleanOrNull == null) {
            throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
        }
    }

    private fun localizeButtonConfigUris(
        source: File,
        userUiDir: File,
        imageNames: Set<String>
    ): File {
        val buttons = Json.parseToJsonElement(source.readText(Charsets.UTF_8)) as? JsonArray
            ?: throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
        val localUiPath = userUiDir.canonicalPath
        val localizedButtons = JsonArray(buttons.map { element ->
            val button = element as? JsonObject
                ?: throw InvalidControllerConfigException("The on-screen button configuration is invalid.")
            val uri = button["uri"] as? JsonPrimitive
            val imageName = uri
                ?.takeIf { it.isString }
                ?.content
                ?.let { imageReferencePattern.find(it)?.value }
            if (imageName != null && imageName in imageNames) {
                JsonObject(button.toMutableMap().apply {
                    put("uri", JsonPrimitive("file://$localUiPath/$imageName"))
                })
            } else {
                button
            }
        })
        return File(source.parentFile, "button_configs.localized.json").apply {
            writeText(localizedButtons.toString(), Charsets.UTF_8)
        }
    }

    private fun referencedImages(uiLayout: String, buttonConfigs: String): Set<String> =
        imageReferencePattern.findAll("$uiLayout\n$buttonConfigs")
            .map { it.value }
            .onEach(::requireSafeImageName)
            .toSet()

    private fun requireSafeImageName(name: String) {
        if (!isSafeImageName(name)) {
            throw InvalidControllerConfigException("A controller image has an unsupported filename.")
        }
    }

    private fun isSafeImageName(name: String): Boolean =
        safeImageNamePattern.matches(name) && name != "." && name != ".."

    private fun readIfFile(file: File): String =
        if (file.isFile) runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("") else ""

    private fun writeBytes(zip: ZipOutputStream, entryName: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeFile(zip: ZipOutputStream, entryName: String, file: File) {
        if (file.length() > MAX_ENTRY_BYTES) {
            throw InvalidControllerConfigException("A controller configuration file is larger than supported.")
        }
        zip.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(FileInputStream(file)).use { input ->
            input.copyTo(zip)
        }
        zip.closeEntry()
    }

    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun uniqueSibling(target: File, purpose: String): File {
        val parent = target.parentFile
            ?: throw IOException("Controller configuration destination has no parent.")
        return File(parent, ".${target.name}.$purpose-${UUID.randomUUID()}")
    }

    private fun restoreBackup(backup: File, target: File) {
        if (target.exists() && !target.delete()) {
            throw IOException("Could not restore the previous controller configuration.")
        }
        if (!backup.renameTo(target)) {
            copyFile(backup, target)
            if (!backup.delete()) {
                throw IOException("Could not clean up a controller configuration backup.")
            }
        }
    }

    private data class AppliedChange(
        val target: File,
        val backup: File?,
        val replacementInstalled: Boolean
    )
}