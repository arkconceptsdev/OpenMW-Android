package org.openmw.ui.controls

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.buttonsGroup
import org.openmw.ui.controls.UIStateManager.containerGlobalHeight
import org.openmw.ui.controls.UIStateManager.containerGlobalWidth
import org.openmw.ui.controls.UIStateManager.enableRightThumb
import org.openmw.ui.controls.UIStateManager.menuAlpha
import org.openmw.ui.controls.UIStateManager.menuColor
import org.openmw.utils.GameFilesPreferences
import org.openmw.ui.view.addCustomLog
import org.openmw.utils.stringRes
import java.io.BufferedWriter
import java.io.File

data class ButtonState(
    val id: Int,
    val size: Float,
    val offsetX: Float,
    val offsetY: Float,
    val isLocked: Boolean,
    val blockMouse: Boolean,
    val keyCode: Int,
    val color: String,
    val alpha: Float,
    val uri: Uri?,
    val group: Int,
    val action: String = ControlActions.KEY
)

object ControlActions {
    const val KEY = "key"
    const val ATTACK_MODE = "attack_mode"
}

const val ATTACK_MODE_KEYCODE = 1000

val DeletedButtonState = ButtonState(
    id = -1, // Placeholder ID
    size = 0f,
    offsetX = 0f,
    offsetY = 0f,
    isLocked = false,
    blockMouse = false,
    keyCode = -1,
    color = "",
    alpha = 0f,
    uri = null,
    group = -1,
    action = ControlActions.KEY
)

object UIStateManager {
    var useNavmesh by mutableStateOf(false)
    var buttonsGroup by mutableIntStateOf(1)
    var globalColorChange by mutableStateOf(false)
    var globalColor by mutableStateOf("#FFFFFF") // Default color
    var globalAlpha by mutableFloatStateOf(1.0f) // Default alpha

    // This is needed for now in Activities
    var tempCodeGroup by mutableStateOf("OpenMW")
    val gameList = arrayOf("OpenMW", "Dethrace", "UQM")
    var uqmVersion by mutableStateOf("0.8.4")
    var uqmJNI by mutableStateOf(false)
    val userUI by derivedStateOf {
        "${Constants.USER_FILE_STORAGE}/${tempCodeGroup}/ui"
    }

    val memoryInfoFlow = MutableStateFlow("")
    val cpuUsageFlow = MutableStateFlow(0)
    var cpuUsageText by mutableStateOf("${stringRes(R.string.cpu_usage)}: 0%")
    var languageSet by mutableStateOf("en")
    val logMessagesFlow = MutableStateFlow("")
    var logMessagesText by mutableStateOf("")
    var isAppLoggingEnabled by mutableStateOf(false)
    var customCFG by mutableStateOf(false)
    val offsetXFlow = MutableStateFlow(0f)
    val offsetYFlow = MutableStateFlow(0f)

    // Odd bug where dragging either thumbstick and a button at the same time removes one from the UI so lets
    // just lock them for now.
    var isThumbDragging by mutableStateOf(false)
    // Is the Right thumb visible?
    var enableRightThumb by mutableStateOf(false)
    // Is the Left thumb visible?
    var enableLeftThumb by mutableStateOf(false)
    // Whether viewport taps should perform an attack instead of activation.
    var attackMode by mutableStateOf(false)
    // isMouseShown() toggles this on and off at EngineActivity line 372.
    var isCursorVisible by mutableIntStateOf(0)

    // webview hook, overlay.kt line 444
    var showWebView by mutableStateOf(false)

    // Colors
    val customColor = Color(0xFF1f1e23)
    val transparentBlack = Color(alpha = 0.6f, red = 0f, green = 0f, blue = 0f)
    val transparent = Color(alpha = 0.0f, red = 0f, green = 0f, blue = 0f)
    val darkGray = Color(alpha = 0.6f, red = 0f, green = 0f, blue = 0f)
    val lightGray = Color(alpha = 0.4f, red = 0f, green = 0f, blue = 0f)
    val gold = Color(202, 165, 96)

    var containerGlobalHeight = mutableFloatStateOf(1.0f)
    var containerGlobalWidth = mutableFloatStateOf(1.0f)

    // Add the shared states
    var memoryInfoText by mutableStateOf("")
    var batteryStatus by mutableStateOf("")
    var isMemoryInfoEnabled by mutableStateOf(false)
    var isBatteryStatusEnabled by mutableStateOf(false)
    var isLoggingEnabled by mutableStateOf(false)
    // this is for MainPage.kt
    var showLogCat by mutableStateOf(false)
    var isTabExpanded by mutableStateOf(false)

    var isLogcatEnabled by mutableStateOf(false)
    var editMode by mutableStateOf(false)
    val gridSize = mutableIntStateOf(50)
    val gridVisible = mutableStateOf(false)
    val gridAlpha = mutableFloatStateOf(0.25f)
    var configureControls by mutableStateOf(false)
    var menuAlpha by mutableFloatStateOf(1f)
    var menuColor by mutableStateOf(Color.Blue)
    var launchedActivity by mutableStateOf(false)
    var isRadialMenuExpanded by mutableStateOf(false)
    private val _buttonStates = MutableStateFlow<Map<Int, ButtonState>>(emptyMap())
    val buttonStates: StateFlow<Map<Int, ButtonState>> get() = _buttonStates

    // Function to change all button colors and alpha
    fun changeAllButtonColorsAndAlpha(newColor: String, newAlpha: Float) {
        val updatedStates = _buttonStates.value.mapValues { (_, buttonState) ->
            buttonState.copy(color = newColor, alpha = newAlpha)
        }
        _buttonStates.value = updatedStates
        globalColor = newColor
        globalAlpha = newAlpha
    }

    fun saveImageUri(id: Int, uri: Uri) {
        _buttonStates.value = _buttonStates.value.toMutableMap().apply {
            this[id]?.let { existingState ->
                put(id, existingState.copy(uri = uri))
            }
        }
    }

    fun updateButtonState(buttonId: Int, buttonState: ButtonState) {
        _buttonStates.value = _buttonStates.value.toMutableMap().apply {
            this[buttonId] = buttonState
        }
    }

    fun removeButtonState(buttonId: Int, context: Context, containerWidth: Float, containerHeight: Float) {
        // Remove the state entirely. Deleted controls must not render, occupy an ID,
        // or prevent a new independent control from being created.
        _buttonStates.value = _buttonStates.value.toMutableMap().apply { remove(buttonId) }
        when (buttonId) {
            98 -> enableRightThumb = false
            99 -> enableLeftThumb = false
        }

        //Log.d("RemoveButtonState", "Button with ID $buttonId removed from state.")

        // Delete the associated image file
        val imageExtensions = listOf("png", "gif")
        imageExtensions.forEach { extension ->
            val imageFile = File(userUI, "$buttonId.$extension")
            if (imageFile.exists()) {
                if (imageFile.delete()) {
                    addCustomLog("Image file deleted: ${imageFile.absolutePath}", textSize = 10, textColor = Color.Cyan)
                    println("Image file deleted: ${imageFile.absolutePath} from $tempCodeGroup")

                } else {
                    addCustomLog("Failed to delete image file: ${imageFile.absolutePath}", textSize = 10, textColor = Color.Red)
                    println("Failed to delete image file: ${imageFile.absolutePath} from $tempCodeGroup")
                }
            }
        }

        // Save the updated state
        saveButtonState(containerWidth, containerHeight)
        //Log.d("RemoveButtonState", "Button state saved.")
        addCustomLog("Button with ID $buttonId removed from state.", textSize = 10, textColor = Color.Cyan)
    }

    fun addButtonState(buttonState: ButtonState) {
        _buttonStates.value += (buttonState.id to buttonState)
    }

    private val buttonSaveLock = Any()
    @Volatile
    private var pendingButtonSave: kotlinx.coroutines.Job? = null

    fun saveButtonState(containerWidth: Float, containerHeight: Float): kotlinx.coroutines.Job =
        synchronized(buttonSaveLock) {
            val previousSave = pendingButtonSave
            CoroutineScope(Dispatchers.IO).launch {
                previousSave?.join()
            val file = File("${userUI}/UI.cfg")
            if (!file.exists()) {
                file.createNewFile()
            }

            val buttonStateMap = _buttonStates.value
            file.printWriter().use { out ->
                buttonStateMap.values
                    .filter { it.id != -1 }
                    .forEach { button ->
                        // Calculate relative offsets
                        val relativeOffsetX = button.offsetX / containerWidth
                        val relativeOffsetY = button.offsetY / containerHeight

                        // Ensure relative offsets are within the bounds of 0 to 1
                        val validRelativeOffsetX = relativeOffsetX.coerceIn(0f, 1f)
                        val validRelativeOffsetY = relativeOffsetY.coerceIn(0f, 1f)

                        val uriString = button.uri?.let { uri ->
                            // Properly format the URI string
                            val uriPath = uri.path
                            val extension = uriPath?.substringAfterLast(".")
                            "${userUI}/${button.id}.$extension"
                        } ?: "null"
                        out.println("ButtonID_${button.id}(${button.size};${validRelativeOffsetX};${validRelativeOffsetY};${button.isLocked};${button.blockMouse};${button.keyCode};#${button.color};${button.alpha};${uriString};${button.group};${button.action})")
                        addCustomLog("ButtonID_${button.id}(${button.size};${validRelativeOffsetX};${validRelativeOffsetY};${button.isLocked};${button.blockMouse};${button.keyCode};#${button.color};${button.alpha};${uriString};${button.group};${button.action})", textSize = 10, textColor = Color.Cyan)
                    }
            }
            }.also { pendingButtonSave = it }
        }

    suspend fun awaitPendingButtonSave() {
        val latestSave = synchronized(buttonSaveLock) { pendingButtonSave }
        latestSave?.join()
    }

    fun loadButtonState(context: Context, containerWidth: Float, containerHeight: Float) {
        val file = File("${userUI}/UI.cfg")
        if (!file.exists()) {
            println("File does not exist: ${file.absolutePath}")
            return
        }

        val lines = file.readLines()
        println("File content: $lines")
        if (lines.isEmpty()) {
            println("File is empty")
            return
        }

        val buttonStateMap = mutableMapOf<Int, ButtonState>()
        var foundButton98 = false
        var foundButton99 = false

        lines.forEach { line ->
            val regex = """ButtonID_(\d+)\(([\d.]+);([\d.]+);([\d.]+);(true|false);(true|false);(\d+);#([A-Fa-f0-9]+);([\d.]+);([^;]+);(\d+)(?:;([a-z_]+))?\)""".toRegex()
            val matchResult = regex.find(line)
            println("Processing line: $line")
            if (matchResult == null) {
                println("No match for line: $line")
                return@forEach
            }

            val buttonId = matchResult.groupValues[1].toInt()
            val uriString = matchResult.groupValues[10]
            val uri = if (uriString == "null") null else Uri.parse("file://${userUI}/${buttonId}.${uriString.substringAfterLast(".")}")
            val group = matchResult.groupValues.getOrNull(11)?.toInt() ?: 1 // Default group to 1 if not specified
            val keyCode = matchResult.groupValues[7].toInt()
            val action = matchResult.groupValues.getOrNull(12)
                ?.takeIf { it.isNotBlank() }
                ?: if (keyCode == ATTACK_MODE_KEYCODE) ControlActions.ATTACK_MODE else ControlActions.KEY

            if (buttonId == 98) {
                foundButton98 = true
            }
            if (buttonId == 99) {
                foundButton99 = true
            }

            // Calculate absolute offsets based on the container dimensions
            val absoluteOffsetX = matchResult.groupValues[3].toFloat() * containerWidth
            val absoluteOffsetY = matchResult.groupValues[4].toFloat() * containerHeight

            val buttonState = ButtonState(
                id = buttonId,
                size = matchResult.groupValues[2].toFloat(),
                offsetX = absoluteOffsetX,
                offsetY = absoluteOffsetY,
                isLocked = matchResult.groupValues[5].toBoolean(),
                blockMouse = matchResult.groupValues[6].toBoolean(),
                keyCode = keyCode,
                color = matchResult.groupValues[8],
                alpha = matchResult.groupValues[9].toFloat(),
                uri = uri,
                group = group, // Load group information
                action = action
            )

            if (uri != null) {
                println("Loaded image URI for button ID $buttonId: ${uri.path}")
            }

            println("Loaded button state: $buttonState")
            // Update the map with the loaded button state
            buttonStateMap[buttonId] = buttonState
        }
        _buttonStates.value = buttonStateMap
        enableRightThumb = foundButton98
        enableLeftThumb = foundButton99
    }

    fun restoreThumbstick(id: Int, containerWidth: Float, containerHeight: Float) {
        val state = when (id) {
            98 -> ButtonState(
                id = 98,
                size = 160f,
                offsetX = containerWidth * 0.71470284f,
                offsetY = containerHeight * 0.5663806f,
                isLocked = false,
                blockMouse = false,
                keyCode = 98,
                color = "93888a89",
                alpha = 0.5764706f,
                uri = null,
                group = 1
            )
            99 -> ButtonState(
                id = 99,
                size = 160f,
                offsetX = containerWidth * 0.048932366f,
                offsetY = containerHeight * 0.5294487f,
                isLocked = false,
                blockMouse = false,
                keyCode = 98,
                color = "97afb1b0",
                alpha = 0.5921569f,
                uri = null,
                group = 1
            )
            else -> return
        }
        updateButtonState(id, state)
        if (id == 98) enableRightThumb = true else enableLeftThumb = true
        saveButtonState(containerWidth, containerHeight)
    }
}

@Composable
fun KeySelectionMenu(context: Context, onKeySelected: (Int) -> Unit, usedKeys: List<Int>, containerWidth: Float, containerHeight: Float) {
    // Bindings are intentionally not filtered by usedKeys. Each layout object is
    // independent, so multiple controls may share any key.
    val currentButtonStates by UIStateManager.buttonStates.collectAsState()
    val letterKeys = ('A'..'Z').toList()
    val numericKeys = ('0'..'9').toList()

    val fKeys = listOf(
        KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3,
        KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6,
        KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9,
        KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12
    )

    val additionalKeys = listOf(
        KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_GRAVE,
        KeyEvent.KEYCODE_TAB,
        KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_FORWARD_DEL,
        KeyEvent.KEYCODE_INSERT,
        KeyEvent.KEYCODE_MOVE_HOME,
        KeyEvent.KEYCODE_MOVE_END,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_COMMA,
        KeyEvent.KEYCODE_PERIOD,
        KeyEvent.KEYCODE_SLASH,
        KeyEvent.KEYCODE_SEMICOLON,
        KeyEvent.KEYCODE_APOSTROPHE,
        KeyEvent.KEYCODE_LEFT_BRACKET,
        KeyEvent.KEYCODE_RIGHT_BRACKET,
        KeyEvent.KEYCODE_BACKSLASH,
        KeyEvent.KEYCODE_MINUS,
        KeyEvent.KEYCODE_EQUALS,
        KeyEvent.KEYCODE_PLUS,
        KeyEvent.KEYCODE_AT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_SEARCH,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_ESCAPE
    ).distinct()

    val numpadKeys = listOf(
        // ----- Numpad list -----
        KeyEvent.KEYCODE_NUMPAD_0,
        KeyEvent.KEYCODE_NUMPAD_1,
        KeyEvent.KEYCODE_NUMPAD_2,
        KeyEvent.KEYCODE_NUMPAD_3,
        KeyEvent.KEYCODE_NUMPAD_4,
        KeyEvent.KEYCODE_NUMPAD_5,
        KeyEvent.KEYCODE_NUMPAD_6,
        KeyEvent.KEYCODE_NUMPAD_7,
        KeyEvent.KEYCODE_NUMPAD_8,
        KeyEvent.KEYCODE_NUMPAD_9
    )

    val virtualController = listOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,

        // ----- Game-controller face buttons -----
        KeyEvent.KEYCODE_BUTTON_A,      // A (bottom)
        KeyEvent.KEYCODE_BUTTON_B,      // B (right)
        KeyEvent.KEYCODE_BUTTON_X,      // X (left)
        KeyEvent.KEYCODE_BUTTON_Y,      // Y (top)

        // ----- Bumpers & Triggers -----
        KeyEvent.KEYCODE_BUTTON_L1,     // Left bumper
        KeyEvent.KEYCODE_BUTTON_R1,     // Right bumper
        KeyEvent.KEYCODE_BUTTON_L2,     // Left trigger (analog)
        KeyEvent.KEYCODE_BUTTON_R2,     // Right trigger (analog)

        // ----- System / menu buttons -----
        KeyEvent.KEYCODE_BUTTON_START,  // Start / Menu
        KeyEvent.KEYCODE_BUTTON_SELECT, // Select / Back
        KeyEvent.KEYCODE_BUTTON_MODE,   // “Home” / “Guide” on some controllers

        // ----- Thumb-stick buttons (press) -----
        KeyEvent.KEYCODE_BUTTON_THUMBL, // Left stick press
        KeyEvent.KEYCODE_BUTTON_THUMBR, // Right stick press

        // ----- Extra generic gamepad buttons (often used for “C”, “Z”, etc.) -----
        KeyEvent.KEYCODE_BUTTON_C,
        KeyEvent.KEYCODE_BUTTON_Z,

        // ----- Numeric gamepad buttons (rarely used but part of the API) -----
        KeyEvent.KEYCODE_BUTTON_1,
        KeyEvent.KEYCODE_BUTTON_2,
        KeyEvent.KEYCODE_BUTTON_3,
        KeyEvent.KEYCODE_BUTTON_4,
        KeyEvent.KEYCODE_BUTTON_5,
        KeyEvent.KEYCODE_BUTTON_6,
        KeyEvent.KEYCODE_BUTTON_7,
        KeyEvent.KEYCODE_BUTTON_8,
        KeyEvent.KEYCODE_BUTTON_9,
        KeyEvent.KEYCODE_BUTTON_10,
        KeyEvent.KEYCODE_BUTTON_11,
        KeyEvent.KEYCODE_BUTTON_12,
        KeyEvent.KEYCODE_BUTTON_13,
        KeyEvent.KEYCODE_BUTTON_14,
        KeyEvent.KEYCODE_BUTTON_15,
        KeyEvent.KEYCODE_BUTTON_16
    )

    var showDialog by remember { mutableStateOf(false) }
    IconButton(onClick = {
        showDialog = true
    }) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Add Button",
            modifier = Modifier.size(36.dp), // Adjust the icon size here
            tint = Color.Red // Change the color here
        )
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .widthIn(min = 300.dp, max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.select_a_numeric_key),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp, top = 16.dp)
                    )
                    numericKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { key ->
                                val keyCode = KeyEvent.KEYCODE_0 + key.minus('0')
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_key),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    letterKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { key ->
                                val keyCode = KeyEvent.KEYCODE_A + key.minus('A')
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_function_key),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    fKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { keyCode ->
                                val key = "F${keyCode - KeyEvent.KEYCODE_F1 + 1}"
                                Log.d("DisplayKey", "Processing key: $key with keyCode: $keyCode") // Log each key
                                addCustomLog("Processing key: $key with keyCode: $keyCode", textSize = 10, textColor = Color.Green)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            Log.d(
                                                "FKeyClick",
                                                "Clicked on key: $key with keyCode: $keyCode"
                                            )
                                            addCustomLog(
                                                "FKeyClick\", \"Clicked on key: $key with keyCode: $keyCode",
                                                textSize = 10,
                                                textColor = Color.Green
                                            )
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_unique_key_the_shift_and_alt_keys_toggle),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    additionalKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { keyCode ->
                                val key = when (keyCode) {
                                    KeyEvent.KEYCODE_SHIFT_LEFT -> "Shift-L"
                                    KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift-R"
                                    KeyEvent.KEYCODE_CTRL_LEFT -> "Ctrl-L"
                                    KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl-R"
                                    KeyEvent.KEYCODE_ALT_LEFT -> "Alt-L"
                                    KeyEvent.KEYCODE_ALT_RIGHT -> "Alt-R"
                                    KeyEvent.KEYCODE_SPACE -> "Space"
                                    KeyEvent.KEYCODE_ESCAPE -> "Escape"
                                    KeyEvent.KEYCODE_ENTER -> "Enter"
                                    KeyEvent.KEYCODE_GRAVE -> "`"
                                    KeyEvent.KEYCODE_TAB -> "Tab"
                                    else -> keyCode.toString()
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_numpad_button),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    numpadKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { keyCode ->
                                val key = when (keyCode) {
                                    KeyEvent.KEYCODE_NUMPAD_0 -> "n0"
                                    KeyEvent.KEYCODE_NUMPAD_1 -> "n1"
                                    KeyEvent.KEYCODE_NUMPAD_2 -> "n2"
                                    KeyEvent.KEYCODE_NUMPAD_3 -> "n3"
                                    KeyEvent.KEYCODE_NUMPAD_4 -> "n4"
                                    KeyEvent.KEYCODE_NUMPAD_5 -> "n5"
                                    KeyEvent.KEYCODE_NUMPAD_6 -> "n6"
                                    KeyEvent.KEYCODE_NUMPAD_7 -> "n7"
                                    KeyEvent.KEYCODE_NUMPAD_8 -> "n8"
                                    KeyEvent.KEYCODE_NUMPAD_9 -> "n9"
                                    else -> keyCode.toString()
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_controller_button),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    virtualController.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { keyCode ->
                                val key = when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> "\u2191"
                                    KeyEvent.KEYCODE_DPAD_DOWN -> "\u2193"
                                    KeyEvent.KEYCODE_DPAD_LEFT -> "\u2190"
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> "\u2192"
                                    // ────── Gamepad face buttons ──────
                                    KeyEvent.KEYCODE_BUTTON_A -> "A"
                                    KeyEvent.KEYCODE_BUTTON_B -> "B"
                                    KeyEvent.KEYCODE_BUTTON_X -> "X"
                                    KeyEvent.KEYCODE_BUTTON_Y -> "Y"

                                    // ────── Bumpers ──────
                                    KeyEvent.KEYCODE_BUTTON_L1 -> "LB"
                                    KeyEvent.KEYCODE_BUTTON_R1 -> "RB"

                                    // ────── Triggers (digital press) ──────
                                    KeyEvent.KEYCODE_BUTTON_L2 -> "LT"
                                    KeyEvent.KEYCODE_BUTTON_R2 -> "RT"

                                    // ────── System / menu ──────
                                    KeyEvent.KEYCODE_BUTTON_START  -> "Start"
                                    KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
                                    KeyEvent.KEYCODE_BUTTON_MODE   -> "Guide"

                                    // ────── Thumb-stick clicks ──────
                                    KeyEvent.KEYCODE_BUTTON_THUMBL -> "LS"
                                    KeyEvent.KEYCODE_BUTTON_THUMBR -> "RS"

                                    // ────── Extra generic buttons (C, Z, numeric) ──────
                                    KeyEvent.KEYCODE_BUTTON_C -> "C"
                                    KeyEvent.KEYCODE_BUTTON_Z -> "Z"

                                    KeyEvent.KEYCODE_BUTTON_1  -> "Btn1"
                                    KeyEvent.KEYCODE_BUTTON_2  -> "Btn2"
                                    KeyEvent.KEYCODE_BUTTON_3  -> "Btn3"
                                    KeyEvent.KEYCODE_BUTTON_4  -> "Btn4"
                                    KeyEvent.KEYCODE_BUTTON_5  -> "Btn5"
                                    KeyEvent.KEYCODE_BUTTON_6  -> "Btn6"
                                    KeyEvent.KEYCODE_BUTTON_7  -> "Btn7"
                                    KeyEvent.KEYCODE_BUTTON_8  -> "Btn8"
                                    KeyEvent.KEYCODE_BUTTON_9  -> "Btn9"
                                    KeyEvent.KEYCODE_BUTTON_10 -> "Btn10"
                                    KeyEvent.KEYCODE_BUTTON_11 -> "Btn11"
                                    KeyEvent.KEYCODE_BUTTON_12 -> "Btn12"
                                    KeyEvent.KEYCODE_BUTTON_13 -> "Btn13"
                                    KeyEvent.KEYCODE_BUTTON_14 -> "Btn14"
                                    KeyEvent.KEYCODE_BUTTON_15 -> "Btn15"
                                    KeyEvent.KEYCODE_BUTTON_16 -> "Btn16"
                                    else -> keyCode.toString()
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.enable_right_thumbstick),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Switch(
                        checked = currentButtonStates.containsKey(98),
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                UIStateManager.restoreThumbstick(98, containerWidth, containerHeight)
                            } else {
                                UIStateManager.removeButtonState(98, context, containerWidth, containerHeight)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Green,
                            uncheckedThumbColor = Color.Gray
                        )
                    )

                    Text(
                        text = "Enable left thumbstick",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp, top = 16.dp)
                    )
                    Switch(
                        checked = currentButtonStates.containsKey(99),
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                UIStateManager.restoreThumbstick(99, containerWidth, containerHeight)
                            } else {
                                UIStateManager.removeButtonState(99, context, containerWidth, containerHeight)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Green,
                            uncheckedThumbColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showDialog = false
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicButtonManager(
    context: Context,
    onNewButtonAdded: (ButtonState) -> Unit,
    blurRadius: Dp = 6.dp,
    shadowColor: Color = Color.White.copy(alpha = 0.6f),
    scaleFactor:Float = 1.2f,
) {
    val buttonStates by UIStateManager.buttonStates.collectAsState()
    val matchIconColorChecked by GameFilesPreferences.loadMatchIconColorState(context).collectAsState(initial = false)
    val iconGlowChecked by GameFilesPreferences.loadIconGlow(context).collectAsState(initial = true)
    val offsetX by animateDpAsState(
        targetValue = if (UIStateManager.editMode) 2.dp else 0.dp,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        finishedListener = {
            if (!UIStateManager.editMode) 0.dp else it
        }, label = ""
    )

    val iconTint = if (matchIconColorChecked) {
        menuColor.copy(alpha = menuAlpha)
    } else {
        Color.Black
    }


    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                UIStateManager.editMode = !UIStateManager.editMode
            }) {
                if (iconGlowChecked) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Button Menu",
                        modifier = Modifier
                            .size(30.dp)
                            .scale(scaleFactor)
                            .blur(blurRadius),
                        tint = shadowColor,
                    )
                }
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Button Menu",
                    modifier = Modifier
                            .offset(x = if (UIStateManager.editMode) offsetX else 0.dp),
                    tint = iconTint
                )
            }
        }
        if (UIStateManager.editMode) {
            KeySelectionMenu(
                onKeySelected = { keyCode ->
                    // Generate a new ID for the button
                    val existingIds = UIStateManager.buttonStates.value.values
                        .map { it.id }
                        .filter { it !in listOf(98, 99, -1) }
                    val newId = (1..Int.MAX_VALUE).first { it !in existingIds }

                    // Create a new ButtonState
                    val newButtonState = ButtonState(
                        id = newId,
                        size = 60f,
                        offsetX = 200f,
                        offsetY = 200f,
                        isLocked = false,
                        blockMouse = false,
                        keyCode = keyCode,
                        color = "000000FF",
                        alpha = 0.25f,
                        uri = null,
                        group = buttonsGroup,
                        action = if (keyCode == ATTACK_MODE_KEYCODE) {
                            ControlActions.ATTACK_MODE
                        } else {
                            ControlActions.KEY
                        }
                    )

                    onNewButtonAdded(newButtonState)

                    // Update the UIStateManager with the new button state
                    UIStateManager.updateButtonState(newButtonState.id, newButtonState)
                    addCustomLog("Saving with containerWidth=${containerGlobalWidth.value}, containerHeight=${containerGlobalHeight.value}", textSize = 10, textColor = Color.Yellow)

                    UIStateManager.saveButtonState(containerGlobalWidth.value, containerGlobalHeight.value)

                },
                usedKeys = buttonStates.values.map { it.keyCode },
                context = context,
                containerWidth = containerGlobalWidth.value,
                containerHeight = containerGlobalHeight.value
            )
        }
    }
}
