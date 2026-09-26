package org.openmw.ui.launcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.R
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.controls.UIStateManager.editMode
import org.openmw.ui.controls.UIStateManager.isAppLoggingEnabled
import org.openmw.ui.controls.UIStateManager.launchedActivity
import org.openmw.utils.GameFilesPreferences.getGameFilesUriState
import org.openmw.utils.GameFilesValidator
import org.openmw.utils.UserManageAssets
import org.openmw.utils.startGame

@Composable
fun LauncherScreen(
    onSettings: () -> Unit,
    viewModel: LauncherViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val savedPath by getGameFilesUriState(context).collectAsState(initial = null)
    val hasValidGameFiles by produceState(initialValue = false, key1 = savedPath) {
        value = withContext(Dispatchers.IO) {
            GameFilesValidator.validatePath(savedPath) != null
        }
    }
    val isSelecting = viewModel.isSelecting
    val scope = rememberCoroutineScope()
    var isLaunching by remember { androidx.compose.runtime.mutableStateOf(false) }

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.selectGameFiles(context, it) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val isWide = maxWidth > maxHeight * 0.9f
        val actionOffset = if (isWide) maxHeight * 0.08f else maxHeight * 0.16f

        Image(
            painter = painterResource(R.drawable.openmw_launcher_art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, if (isWide) -0.35f else -0.1f),
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0x660B0704)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = actionOffset)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                isSelecting -> {
                    CircularProgressIndicator(
                        color = Color(0xFFD1A65A),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "CHECKING GAME FILES",
                        color = Color(0xFFF1D7A0),
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        letterSpacing = 1.5.sp
                    )
                }

                !hasValidGameFiles -> {
                    LauncherActionButton(
                        label = "SELECT GAME FILES",
                        onClick = {
                            viewModel.clearError()
                            directoryPicker.launch(null)
                        }
                    )
                    viewModel.errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = Color(0xFFFFD4C4),
                            fontFamily = FontFamily.Serif,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                else -> {
                    LauncherActionButton(
                        label = if (isLaunching) "PREPARING OPENMW" else "PLAY",
                        enabled = !isLaunching,
                        onClick = {
                            if (!isLaunching) {
                                scope.launch {
                                    isLaunching = true
                                    UIStateManager.configureControls = false
                                    launchedActivity = true
                                    editMode = false
                                    isAppLoggingEnabled = false
                                    withContext(Dispatchers.Default) {
                                        UserManageAssets(context).resourcePrepare()
                                    }
                                    context.startGame()
                                }
                            }
                        }
                    )
                    LauncherActionButton(
                        label = "SETTINGS",
                        onClick = onSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xB51B1008))
            .border(1.dp, Color(0xFFD1A65A), RoundedCornerShape(6.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) Color(0xFFF1D7A0) else Color(0xFF9D8051),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            letterSpacing = 2.sp
        )
    }
}