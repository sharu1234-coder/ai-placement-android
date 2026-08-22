package com.aiplacementos.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.aiplacementos.app.ui.home.HomeScreen
import com.aiplacementos.app.ui.profile.ProfileScreen
import com.aiplacementos.app.ui.progress.ProgressDashboardScreen
import com.aiplacementos.app.ui.roadmap.RoadmapScreen

private data class BottomTab(val screen: Screen, val label: String)

private val bottomTabs = listOf(
    BottomTab(Screen.Home, "Home"),
    BottomTab(Screen.Roadmap, "Roadmap"),
    BottomTab(Screen.Progress, "Progress"),
    BottomTab(Screen.Profile, "Profile"),
)

@Composable
fun MainScreen(navController: NavHostController) {
    var selectedRoute by remember { mutableStateOf(Screen.Home.route) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedRoute == tab.screen.route,
                        onClick = { selectedRoute = tab.screen.route },
                        icon = { Icon(tab.icon(), contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = androidx.compose.ui.Modifier.padding(innerPadding)) {
            when (selectedRoute) {
                Screen.Home.route -> HomeScreen(
                    onStartPrepareMe = { navController.navigate(Screen.RoleOrJdEntry.route) },
                )
                Screen.Roadmap.route -> RoadmapScreen(
                    jdId = 0,
                    onOpenTask = { taskId -> navController.navigate(roadmapTaskDetailRoute(taskId, jdId = 0)) },
                )
                Screen.Progress.route -> ProgressDashboardScreen()
                Screen.Profile.route -> ProfileScreen(
                    onSelectRole = { navController.navigate(Screen.RoleSelection.route) },
                )
            }
        }
    }
}

private fun BottomTab.icon() = when (screen) {
    Screen.Home -> Icons.Filled.Home
    Screen.Roadmap -> Icons.Filled.Timeline
    Screen.Progress -> Icons.Filled.CheckCircle
    else -> Icons.Filled.AccountCircle
}
