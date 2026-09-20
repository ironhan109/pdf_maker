package com.ebookmaker.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ebookmaker.app.ui.screens.export.ExportScreen
import com.ebookmaker.app.ui.screens.home.HomeScreen
import com.ebookmaker.app.ui.screens.manage.PageManagementScreen
import com.ebookmaker.app.ui.screens.scan.ScanScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scan : Screen("scan/{sessionId}") {
        fun createRoute(sessionId: Long) = "scan/$sessionId"
    }
    object Manage : Screen("manage/{sessionId}") {
        fun createRoute(sessionId: Long) = "manage/$sessionId"
    }
    object Export : Screen("export/{sessionId}") {
        fun createRoute(sessionId: Long) = "export/$sessionId"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToScan = { sessionId ->
                    navController.navigate(Screen.Scan.createRoute(sessionId))
                },
                onNavigateToManage = { sessionId ->
                    navController.navigate(Screen.Manage.createRoute(sessionId))
                },
                onNavigateToExport = { sessionId ->
                    navController.navigate(Screen.Export.createRoute(sessionId))
                }
            )
        }

        composable(
            route = Screen.Scan.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            ScanScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToManage = {
                    navController.navigate(Screen.Manage.createRoute(sessionId))
                }
            )
        }

        composable(
            route = Screen.Manage.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            PageManagementScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToScan = {
                    navController.navigate(Screen.Scan.createRoute(sessionId)) {
                        popUpTo(Screen.Manage.createRoute(sessionId)) { inclusive = true }
                    }
                },
                onNavigateToExport = {
                    navController.navigate(Screen.Export.createRoute(sessionId))
                }
            )
        }

        composable(
            route = Screen.Export.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
            ExportScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
