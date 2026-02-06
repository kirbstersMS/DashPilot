package com.example.dashpilot.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.dashpilot.ui.overlay.AppSwitcherController
import com.example.dashpilot.ui.overlay.OverlayController
import com.example.dashpilot.ui.screens.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object CherryPicker : Screen("picker", "Picker", Icons.AutoMirrored.Filled.FactCheck)
    object Earnings : Screen("earnings", "Earnings", Icons.Default.AttachMoney)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    // Hidden Routes
    object OrderLog : Screen("order_log", "Logs", Icons.AutoMirrored.Filled.List)
    object WeeklyRecap : Screen("weekly_recap/{startDate}", "Recap", Icons.Default.AttachMoney)
    object YearlyRecap : Screen("yearly_recap/{year}", "Yearly", Icons.Default.AttachMoney) {
        fun createRoute(year: Int) = "yearly_recap/$year"
    }
    object AllYears : Screen("all_years", "All Years", Icons.Default.DateRange)
    object ByPurpose : Screen("by_purpose", "By Purpose", Icons.AutoMirrored.Filled.List)
    object ServiceDetail : Screen("service_detail/{name}", "Detail", Icons.AutoMirrored.Filled.List) {
        fun createRoute(name: String) = "service_detail/$name"
    }
}

@Composable
fun MainScreen(
    overlayController: OverlayController,
    appSwitcherController: AppSwitcherController,
    isAppDark: Boolean,
    onAppThemeChange: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    // REMOVED INSIGHTS FROM BOTTOM BAR
    val items = listOf(Screen.CherryPicker, Screen.Earnings, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Screen.CherryPicker.route,
            Modifier.padding(innerPadding)
        ) {
            // 1. CHERRY PICKER
            composable(Screen.CherryPicker.route) {
                CherryPickerScreen(overlayController)
            }

            // 2. SETTINGS (Now wires up the Log View)
            composable(Screen.Settings.route) {
                SettingsScreen(
                    isDarkTheme = isAppDark,
                    onThemeChange = onAppThemeChange,
                    onViewLogs = { navController.navigate(Screen.OrderLog.route) }
                )
            }

            // 3. EARNINGS
            composable(Screen.Earnings.route) {
                EarningsScreen(appSwitcherController, navController)
            }

            // 4. NEW ORDER LOG (Hidden Route)
            composable(Screen.OrderLog.route) {
                OrderLogScreen(navController)
            }

            // --- DEPRECATED INSIGHTS ROUTE REMOVED ---

            // 5. WEEKLY RECAP
            composable(
                route = Screen.WeeklyRecap.route,
                arguments = listOf(navArgument("startDate") { type = NavType.LongType })
            ) { backStackEntry ->
                val date = backStackEntry.arguments?.getLong("startDate") ?: System.currentTimeMillis()
                WeeklyRecapScreen(navController, date)
            }

            // 6. YEARLY RECAP
            composable(
                route = Screen.YearlyRecap.route,
                arguments = listOf(navArgument("year") { type = NavType.IntType })
            ) { backStackEntry ->
                val year = backStackEntry.arguments?.getInt("year") ?: 2025
                YearlyRecapScreen(navController, year)
            }

            // 7. ALL YEARS
            composable(Screen.AllYears.route) {
                AllYearsScreen(navController)
            }

            // 8. BY PURPOSE
            composable(Screen.ByPurpose.route) {
                ByPurposeScreen(navController)
            }

            // 9. SERVICE DETAIL
            composable(
                route = Screen.ServiceDetail.route,
                arguments = listOf(navArgument("name") { type = NavType.StringType })
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("name") ?: "Service"
                ServiceDetailScreen(navController, name)
            }
        }
    }
}