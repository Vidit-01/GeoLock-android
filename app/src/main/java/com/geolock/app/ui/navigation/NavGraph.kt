package com.geolock.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geolock.app.ui.diagnostics.DiagnosticsScreen
import com.geolock.app.ui.home.HomeScreen
import com.geolock.app.ui.home.HomeViewModel
import com.geolock.app.ui.lock.AppUnlockScreen
import com.geolock.app.ui.log.ActivityLogScreen
import com.geolock.app.ui.settings.ChangeKeyScreen
import com.geolock.app.ui.settings.SettingsScreen
import com.geolock.app.ui.settings.SettingsViewModel
import com.geolock.app.ui.setup.SetupViewModel
import com.geolock.app.ui.setup.SetupWizard
import com.geolock.app.ui.zone.ZoneEditScreen
import com.geolock.app.ui.zone.ZoneEditViewModel

object Routes {
    const val SETUP = "setup"
    const val APP_LOCK = "app_lock"
    const val HOME = "home"
    const val ZONE = "zone/{zoneId}"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
    const val DIAGNOSTICS = "diagnostics"
    const val CHANGE_KEY = "change_key"

    fun zone(id: String = "new") = "zone/$id"
}

@Composable
fun GeoLockNavHost() {
    val setupViewModel: SetupViewModel = hiltViewModel()
    val gateViewModel: AppGateViewModel = hiltViewModel()
    val boot by setupViewModel.boot.collectAsStateWithLifecycle()
    val gate by gateViewModel.gate.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    if (!boot.ready || !gate.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val start = when {
        !boot.onboardingComplete -> Routes.SETUP
        gate.needsAppKey -> Routes.APP_LOCK
        else -> Routes.HOME
    }

    LaunchedEffect(gate.needsAppKey, boot.onboardingComplete) {
        if (!boot.onboardingComplete) return@LaunchedEffect
        val route = navController.currentDestination?.route
        if (gate.needsAppKey && route != Routes.APP_LOCK && route != Routes.SETUP) {
            navController.navigate(Routes.APP_LOCK) {
                popUpTo(0) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.SETUP) {
            SetupWizard(
                viewModel = setupViewModel,
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.APP_LOCK) {
            AppUnlockScreen(
                onUnlocked = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.APP_LOCK) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            val vm: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = vm,
                onOpenZone = { navController.navigate(Routes.zone(it)) },
                onAddZone = { navController.navigate(Routes.zone("new")) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenLogs = { navController.navigate(Routes.LOGS) }
            )
        }
        composable(
            route = Routes.ZONE,
            arguments = listOf(navArgument("zoneId") { type = NavType.StringType })
        ) {
            val vm: ZoneEditViewModel = hiltViewModel()
            ZoneEditScreen(
                viewModel = vm,
                onDone = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onChangeKey = { navController.navigate(Routes.CHANGE_KEY) },
                onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                onLogs = { navController.navigate(Routes.LOGS) },
                onManageApps = { navController.navigate(Routes.HOME) }
            )
        }
        composable(Routes.CHANGE_KEY) {
            ChangeKeyScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.LOGS) {
            ActivityLogScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
