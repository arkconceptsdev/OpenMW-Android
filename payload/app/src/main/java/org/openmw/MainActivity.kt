package org.openmw

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.InputDevice.SOURCE_GAMEPAD
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.modDownloader.ModDatabase
import org.openmw.modDownloader.ModListManager
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.navigation.RootNav
import org.openmw.ui.theme.OpenMWTheme
import org.openmw.ui.view.MoeDialog
import org.openmw.ui.view.currentDeviceRealSize
import org.openmw.ui.view.updateResolutionInConfig
import org.openmw.utils.CaptureCrash
import org.openmw.utils.ConfigFileObserver
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getScreenStayOn
import org.openmw.utils.GameFilesPreferences.getSystemBars
import org.openmw.utils.GameFilesPreferences.readCodeGroup
import org.openmw.utils.MyAlertDialog
import org.openmw.utils.PermissionHelper
import org.openmw.utils.PermissionHelper.getManageExternalStoragePermission
import org.openmw.utils.StorageRootMigration
import org.openmw.utils.StorageRootMigrationWarning
import org.openmw.utils.UserManageAssets

@InternalCoroutinesApi
@ExperimentalMaterial3Api
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val uiScope = CoroutineScope(Dispatchers.Main)

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    @OptIn(DelicateCoroutinesApi::class)
    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        CaptureCrash.initialize(this)
        Thread.setDefaultUncaughtExceptionHandler(CaptureCrash())

        ModListManager.init(this)
        ModDatabase.getDatabase(this)

        lifecycleScope.launch {
            val permissionGranted = getManageExternalStoragePermission(this@MainActivity)
            if (permissionGranted) {
                proceedWithNextSteps()
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    @OptIn(ExperimentalFoundationApi::class)
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        PermissionHelper.handlePermissionResult(requestCode, intArrayOf(resultCode)) { granted ->
            if (granted) {
                proceedWithNextSteps()
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun proceedWithNextSteps() {
        lifecycleScope.launch(Dispatchers.Main) {
            val migrationResult = withContext(Dispatchers.IO) {
                StorageRootMigration.migrate(Environment.getExternalStorageDirectory())
            }
            val storageMigrationWarning = when (migrationResult.warning) {
                StorageRootMigrationWarning.BOTH_ROOTS_EXIST ->
                    getString(R.string.storage_migration_both_roots_message)
                StorageRootMigrationWarning.LEGACY_PATH_NOT_DIRECTORY ->
                    getString(R.string.storage_migration_legacy_not_directory_message)
                StorageRootMigrationWarning.DESTINATION_PATH_NOT_DIRECTORY ->
                    getString(R.string.storage_migration_destination_not_directory_message)
                StorageRootMigrationWarning.MIGRATION_FAILED ->
                    getString(R.string.storage_migration_failed_message)
                null -> null
            }

            if (migrationResult.warning ==
                StorageRootMigrationWarning.DESTINATION_PATH_NOT_DIRECTORY
            ) {
                setContent {
                    OpenMWTheme(darkTheme = true) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            StorageMigrationWarningDialog(
                                message = storageMigrationWarning
                                    ?: getString(R.string.storage_migration_destination_not_directory_message),
                                onDismiss = { finish() }
                            )
                        }
                    }
                }
                return@launch
            }

            GameFilesPreferences.initialize(this@MainActivity)

            withContext(Dispatchers.Default) {
                UserManageAssets(applicationContext).onFirstLaunch()
            }

            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val (width, height) = windowManager.currentDeviceRealSize()

            setContent {
                OpenMWTheme(
                    darkTheme = true // have to force it bcs hardcode color is used
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val showStorageMigrationWarning =
                            remember { mutableStateOf(storageMigrationWarning != null) }
                        val scope = rememberCoroutineScope()
                        val hideSystemBars by getSystemBars(this@MainActivity).collectAsState(initial = false)
                        val screenStayOn by getScreenStayOn(this@MainActivity).collectAsState(initial = false)

                        if (hideSystemBars) {
                            hideSystemBars(this@MainActivity)
                        } else {
                            showSystemBars(this@MainActivity)
                        }

                        if (screenStayOn) {
                            enableScreenStayOn(this@MainActivity)
                        } else {
                            disableScreenStayOn(this@MainActivity)
                        }

                        LaunchedEffect(Unit) {
                            scope.launch(Dispatchers.IO) {
                                startObservingCodeGroup(this@MainActivity, uiScope)
                                val configFilePath = Constants.SETTINGS_FILE
                                val configFileObserver = ConfigFileObserver(configFilePath)
                                configFileObserver.startWatching()
                            }
                        }

                        val showDialog = remember { mutableStateOf(true) }
                        val avoidInsertion by GameFilesPreferences.readResolutionInsertion(this@MainActivity).collectAsState(initial = false)
                        val whatsNew by GameFilesPreferences.getWhatsNew(this@MainActivity).collectAsState(initial = false)

                        if (showStorageMigrationWarning.value && storageMigrationWarning != null) {
                            StorageMigrationWarningDialog(
                                message = storageMigrationWarning,
                                onDismiss = {
                                    showStorageMigrationWarning.value = false
                                }
                            )
                        }

                        if (!avoidInsertion) {
                            updateResolutionInConfig(width, height)
                        }

                        if (whatsNew && !showStorageMigrationWarning.value) {
                            MyAlertDialog(showDialog = showDialog)
                        }
                        RootNav()
                        MoeDialog()
                    }
                }
            }
        }
    }

    public override fun onDestroy() {
        finish()
        uiScope.cancel()
        super.onDestroy()
        // Process.killProcess(Process.myPid())
    }
}

@Composable
private fun StorageMigrationWarningDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.storage_migration_warning_title))
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.storage_migration_ok))
            }
        }
    )
}

fun isControllerConnected(context: Context): Boolean {
    val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    val deviceIds = inputManager.inputDeviceIds
    for (id in deviceIds) {
        val device = inputManager.getInputDevice(id)
        if (device?.sources?.and(SOURCE_GAMEPAD) == SOURCE_GAMEPAD) {
            return true
        }
    }
    return false
}

fun startObservingCodeGroup(context: Context, scope: CoroutineScope) {
    scope.launch {
        readCodeGroup(context).collect { codeGroup ->
            UIStateManager.tempCodeGroup = codeGroup
        }
    }
}

