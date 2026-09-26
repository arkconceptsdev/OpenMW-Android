package org.openmw.ui.controls

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import org.libsdl.app.SDLActivity
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.tempCodeGroup
import org.openmw.ui.view.currentDeviceRealSize
import org.openmw.utils.GameFilesPreferences.getForceSdlMouseEmulation
import org.openmw.utils.GameFilesPreferences.getTapToActivate
import org.openmw.utils.GameFilesPreferences.getSensitivityMouse
import org.openmw.utils.GameFilesPreferences.getSensitivityRT
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val DRAG_THRESHOLD_PX = 24f
private const val SDL_BUTTON_LEFT = 1

/**
 * The transparent gameplay surface owns only touches that are not consumed by
 * a HUD control. It is placed below the HUD in EngineActivity, so movement and
 * viewport gestures can coexist on separate pointers.
 */
@Composable
fun ViewportTouchSurface() {
    val context = LocalContext.current
    val sensitivityRT by getSensitivityRT(context).collectAsState(initial = 5000f)
    val sensitivityMouse by getSensitivityMouse(context).collectAsState(initial = 5000f)
    val tapToActivate by getTapToActivate(context).collectAsState(initial = true)
    val forceSdlMouseEmulation by getForceSdlMouseEmulation(context).collectAsState(initial = false)
    val enabled = tempCodeGroup == "OpenMW" &&
        !configureControls &&
        (tapToActivate || forceSdlMouseEmulation)
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val (screenWidth, _) = windowManager.currentDeviceRealSize()

    // Do not install a full-size pointer-input node when this setting is off.
    // The SDL surface must receive the touch stream exactly as it did before
    // the custom viewport controls were added.
    if (!enabled) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(sensitivityRT, sensitivityMouse) {
                awaitPointerEventScope {
                    var activePointer: PointerId? = null
                    var start = Offset.Zero
                    var last = Offset.Zero
                    var dragging = false

                    fun sendCameraDelta(delta: Offset) {
                        val sensitivity = if (UIStateManager.isCursorVisible != 0) {
                            sensitivityMouse ?: 5000f
                        } else {
                            sensitivityRT ?: 5000f
                        }
                        SDLActivity.sendRelativeMouseMotion(
                            (delta.x * sensitivity / screenWidth).roundToInt(),
                            (delta.y * sensitivity / screenWidth).roundToInt()
                        )
                    }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)

                        if (activePointer == null) {
                            val first = event.changes.firstOrNull { it.pressed && !it.isConsumed }
                                ?: continue
                            activePointer = first.id
                            start = first.position
                            last = first.position
                            dragging = false
                            first.consume()
                            continue
                        }

                        val change = event.changes.firstOrNull { it.id == activePointer }
                        if (change == null) {
                            activePointer = null
                            dragging = false
                            continue
                        }

                        if (change.pressed) {
                            val position = change.position
                            val distance = hypot(position.x - start.x, position.y - start.y)
                            if (!dragging && distance >= DRAG_THRESHOLD_PX) {
                                dragging = true
                            }

                            // Tap to Activate is also the continuous viewport
                            // mouse mode: every movement updates SDL's mouse.
                            // The threshold only distinguishes a tap from a
                            // drag so a drag cannot also click on release.
                            sendCameraDelta(position - last)
                            last = position
                            change.consume()
                        } else {
                            if (!dragging && event.type == PointerEventType.Release) {
                                // A short viewport tap is one standard left
                                // click. OpenMW's own ready-attack and
                                // power-attack handling remains authoritative.
                                SDLActivity.sendMouseButton(1, SDL_BUTTON_LEFT)
                                SDLActivity.sendMouseButton(0, SDL_BUTTON_LEFT)
                            }
                            change.consume()
                            activePointer = null
                        }
                    }
                }
            }
    )
}