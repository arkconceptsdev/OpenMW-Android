package org.openmw.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.InternalCoroutinesApi
import org.openmw.ui.launcher.LauncherScreen
import org.openmw.ui.page.setting.SettingPage

enum class RootDesc(
    val route: String
) {
    Launcher("launcher"),
    Settings("settings"),
}

val LocalRootNav = staticCompositionLocalOf<NavHostController> {
    error("LocalRootNav Not Provide")
}

@Composable
fun RootNav() {
    val navController = rememberNavController()
    CompositionLocalProvider(
        LocalRootNav provides navController,
    ) {
        RootNavHost(navController)
    }
}

@OptIn(InternalCoroutinesApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RootNavHost(
    rootNavController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = rootNavController,
        startDestination = RootDesc.Launcher.route,
        modifier = modifier,
        enterTransition = {
            fadeIn()
        },
        exitTransition = {
            fadeOut()
        },
        popEnterTransition = {
            fadeIn()
        },
        popExitTransition = {
            fadeOut()
        }
    ) {
        composable(route = RootDesc.Launcher.route) {
            LauncherScreen(
                onSettings = {
                    rootNavController.navigate(RootDesc.Settings.route)
                }
            )
        }

        composable(route = RootDesc.Settings.route) {
            SettingPage(
                navigateToHome = {
                    rootNavController.popBackStack()
                }
            )
        }
    }
}
