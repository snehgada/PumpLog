package com.workouttracker.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.workouttracker.AppContainer
import com.workouttracker.R
import com.workouttracker.ui.history.HistoryRoute
import com.workouttracker.ui.progress.ExerciseProgressRoute
import com.workouttracker.ui.progress.ProgressRoute
import com.workouttracker.ui.routines.RoutineEditorRoute
import com.workouttracker.ui.routines.RoutinesRoute
import com.workouttracker.ui.today.TodayRoute
import com.workouttracker.ui.welcome.WelcomeRoute
import com.workouttracker.ui.workout.WorkoutRoute

private object Routes {
    const val WELCOME = "welcome"
    const val TODAY = "today"
    const val ROUTINES = "routines"
    const val ROUTINE_ARG = "routineId"
    const val EDIT_ROUTINE = "routine/{$ROUTINE_ARG}"
    const val NEW_ROUTINE = "routine_new"
    const val SESSION_ARG = "sessionId"
    const val WORKOUT = "workout/{$SESSION_ARG}"
    const val HISTORY = "history"
    const val PROGRESS = "progress"
    const val EXERCISE_ARG = "exerciseId"
    const val EXERCISE = "exercise/{$EXERCISE_ARG}"

    fun editRoutine(id: Long) = "routine/$id"
    fun workout(sessionId: Long) = "workout/$sessionId"
    fun exercise(id: Long) = "exercise/$id"
}

private enum class Tab(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(Routes.TODAY, R.string.tab_today, R.drawable.ic_tab_today),
    HISTORY(Routes.HISTORY, R.string.tab_history, R.drawable.ic_tab_history),
    PROGRESS(Routes.PROGRESS, R.string.tab_progress, R.drawable.ic_tab_progress),
    ROUTINES(Routes.ROUTINES, R.string.tab_routines, R.drawable.ic_tab_routines),
}

@Composable
fun AppNavigation(container: AppContainer, openWorkoutRequests: Int = 0) {
    // Seed first, then decide where to start: First launch until it has been dismissed once.
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        container.workoutRepository.ensureSeeded()
        start = if (container.settings.isWelcomeDone()) Routes.TODAY else Routes.WELCOME
    }
    val startRoute = start
    if (startRoute == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()

    // Opened from the rest-timer notification: go to the workout in progress, if there still is one.
    LaunchedEffect(openWorkoutRequests) {
        if (openWorkoutRequests == 0) return@LaunchedEffect
        val session = container.sessionRepository.inProgress() ?: return@LaunchedEffect
        val route = Routes.workout(session.id)
        if (navController.currentBackStackEntry?.destination?.route != Routes.WORKOUT) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }
    val currentRoute = backStack?.destination?.route
    val showTabs = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showTabs) BottomTabs(currentRoute) { tab -> navController.switchTab(tab.route) }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.WELCOME) {
                WelcomeRoute(
                    container = container,
                    onContinue = { navController.leaveWelcome(Routes.TODAY) },
                    onReviewRoutines = { navController.leaveWelcome(Routes.ROUTINES) },
                )
            }
            composable(Routes.TODAY) {
                TodayRoute(
                    container = container,
                    onEditRoutines = { navController.switchTab(Routes.ROUTINES) },
                    onOpenRoutine = { id -> navController.navigate(Routes.editRoutine(id)) },
                    onOpenWorkout = { id -> navController.navigate(Routes.workout(id)) { launchSingleTop = true } },
                )
            }
            composable(
                Routes.WORKOUT,
                arguments = listOf(navArgument(Routes.SESSION_ARG) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.SESSION_ARG) ?: return@composable
                WorkoutRoute(container = container, sessionId = id, onClose = { navController.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryRoute(
                    container = container,
                    onOpenWorkout = { id -> navController.navigate(Routes.workout(id)) { launchSingleTop = true } },
                )
            }
            composable(Routes.PROGRESS) {
                ProgressRoute(container = container, onOpenExercise = { id -> navController.navigate(Routes.exercise(id)) })
            }
            composable(
                Routes.EXERCISE,
                arguments = listOf(navArgument(Routes.EXERCISE_ARG) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.EXERCISE_ARG) ?: return@composable
                ExerciseProgressRoute(container = container, exerciseId = id, onBack = { navController.popBackStack() })
            }
            composable(Routes.ROUTINES) {
                RoutinesRoute(
                    container = container,
                    onOpenRoutine = { id -> navController.navigate(Routes.editRoutine(id)) },
                    onNewRoutine = { navController.navigate(Routes.NEW_ROUTINE) },
                )
            }
            composable(Routes.NEW_ROUTINE) {
                RoutineEditorRoute(container = container, routineId = null, onDone = { navController.popBackStack() })
            }
            composable(
                Routes.EDIT_ROUTINE,
                arguments = listOf(navArgument(Routes.ROUTINE_ARG) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ROUTINE_ARG)
                RoutineEditorRoute(container = container, routineId = id, onDone = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomTabs(currentRoute: String?, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        // Pop to the graph root rather than the start destination: after First launch the start (welcome) is gone.
        popUpTo(graph.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.leaveWelcome(route: String) {
    navigate(route) {
        popUpTo(Routes.WELCOME) { inclusive = true }
        launchSingleTop = true
    }
}
