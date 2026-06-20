package com.reminderapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.reminderapp.ui.addreminder.AddReminderScreen
import com.reminderapp.ui.home.HomeScreen
import com.reminderapp.ui.reminderdetail.ReminderDetailScreen
import com.reminderapp.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AddReminder : Screen("add_reminder?reminderId={reminderId}") {
        fun createRoute(reminderId: Long? = null) =
            if (reminderId != null) "add_reminder?reminderId=$reminderId" else "add_reminder"
    }
    object ReminderDetail : Screen("reminder_detail/{reminderId}") {
        fun createRoute(reminderId: Long) = "reminder_detail/$reminderId"
    }
    object Settings : Screen("settings")
}

@Composable
fun ReminderNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAddReminder = { navController.navigate(Screen.AddReminder.createRoute()) },
                onNavigateToDetail = { id -> navController.navigate(Screen.ReminderDetail.createRoute(id)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.AddReminder.route,
            arguments = listOf(
                navArgument("reminderId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val reminderId = backStackEntry.arguments?.getLong("reminderId")?.takeIf { it != -1L }
            AddReminderScreen(
                reminderId = reminderId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ReminderDetail.route,
            arguments = listOf(navArgument("reminderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val reminderId = backStackEntry.arguments?.getLong("reminderId") ?: return@composable
            ReminderDetailScreen(
                reminderId = reminderId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> navController.navigate(Screen.AddReminder.createRoute(id)) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
