package com.habitramp.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.habitramp.AppContainer
import com.habitramp.presentation.habit_create.CreateHabitScreen
import com.habitramp.presentation.habit_create.CreateHabitViewModel
import com.habitramp.presentation.habit_settings.HabitSettingsScreen
import com.habitramp.presentation.habit_settings.HabitSettingsViewModel
import com.habitramp.presentation.history.HistoryScreen
import com.habitramp.presentation.history.HistoryViewModel
import com.habitramp.presentation.home.HomeScreen
import com.habitramp.presentation.home.HomeViewModel
import com.habitramp.presentation.manage.ManageHabitsScreen
import com.habitramp.presentation.manage.ManageHabitsViewModel
import com.habitramp.presentation.statistics.StatisticsScreen
import com.habitramp.presentation.statistics.StatisticsViewModel
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val HABITS = "habits"
    const val CREATE = "create"
    const val SETTINGS = "settings/{habitId}"
    const val STATISTICS = "statistics/{habitId}"
    const val HISTORY = "history/{habitId}"

    fun settings(id: Long) = "settings/$id"
    fun statistics(id: Long? = null) = "statistics/${id ?: -1L}"
    fun history(id: Long? = null) = "history/${id ?: -1L}"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun HabitRampNavHost(container: AppContainer, initialHabitId: Long? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomItems = listOf(
        BottomItem(Routes.HOME, "Сегодня", Icons.Outlined.Home),
        BottomItem(Routes.HABITS, "Привычки", Icons.Outlined.ViewList),
        BottomItem(Routes.STATISTICS, "Статистика", Icons.Outlined.BarChart),
        BottomItem(Routes.HISTORY, "История", Icons.Outlined.CalendarMonth)
    )
    val showBottomBar = currentRoute == Routes.HOME || currentRoute == Routes.HABITS ||
        currentRoute?.startsWith("statistics/") == true ||
        currentRoute?.startsWith("history/") == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = when (item.route) {
                            Routes.HOME -> currentRoute == Routes.HOME
                            Routes.HABITS -> currentRoute == Routes.HABITS
                            Routes.STATISTICS -> currentRoute?.startsWith("statistics/") == true
                            Routes.HISTORY -> currentRoute?.startsWith("history/") == true
                            else -> false
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val target = when (item.route) {
                                    Routes.STATISTICS -> Routes.statistics()
                                    Routes.HISTORY -> Routes.history()
                                    else -> item.route
                                }
                                navController.navigate(target) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(
                    key = "home-${initialHabitId ?: 0}",
                    factory = HomeViewModel.factory(container, initialHabitId)
                )
                HomeScreen(
                    viewModel = vm,
                    onOpenSettings = { navController.navigate(Routes.settings(it)) },
                    onOpenHabits = {
                        navController.navigate(Routes.HABITS) {
                            launchSingleTop = true
                        }
                    },
                    onCreateHabit = { navController.navigate(Routes.CREATE) }
                )
            }

            composable(Routes.HABITS) {
                val vm: ManageHabitsViewModel = viewModel(factory = ManageHabitsViewModel.factory(container))
                ManageHabitsScreen(
                    viewModel = vm,
                    onCreateHabit = { navController.navigate(Routes.CREATE) },
                    onOpenHabit = { navController.navigate(Routes.settings(it)) }
                )
            }

            composable(Routes.CREATE) {
                val vm: CreateHabitViewModel = viewModel(factory = CreateHabitViewModel.factory(container))
                CreateHabitScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onCreated = { id ->
                        container.applicationScope.launch {
                            container.preferences.setLastSelectedHabitId(id)
                        }
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Routes.SETTINGS,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType })
            ) { entry ->
                val habitId = entry.arguments?.getLong("habitId") ?: return@composable
                val vm: HabitSettingsViewModel = viewModel(
                    key = "settings-$habitId",
                    factory = HabitSettingsViewModel.factory(container, habitId)
                )
                HabitSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenStatistics = { navController.navigate(Routes.statistics(it)) },
                    onOpenHistory = { navController.navigate(Routes.history(it)) }
                )
            }

            composable(
                route = Routes.STATISTICS,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType })
            ) { entry ->
                val habitId = entry.arguments?.getLong("habitId")?.takeIf { it > 0 }
                val vm: StatisticsViewModel = viewModel(
                    key = "statistics-${habitId ?: 0}",
                    factory = StatisticsViewModel.factory(container, habitId)
                )
                StatisticsScreen(viewModel = vm)
            }

            composable(
                route = Routes.HISTORY,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType })
            ) { entry ->
                val habitId = entry.arguments?.getLong("habitId")?.takeIf { it > 0 }
                val vm: HistoryViewModel = viewModel(
                    key = "history-${habitId ?: 0}",
                    factory = HistoryViewModel.factory(container, habitId)
                )
                HistoryScreen(viewModel = vm)
            }
        }
    }
}
