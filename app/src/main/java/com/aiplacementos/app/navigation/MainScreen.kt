package com.aiplacementos.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.home.HomeScreen
import com.aiplacementos.app.ui.placementpath.PlacementPathScreen
import com.aiplacementos.app.ui.progress.ProgressDashboardScreen
import com.aiplacementos.app.ui.roadmap.RoadmapScreen
import com.aiplacementos.app.ui.tests.TestCenterScreen
import com.aiplacementos.app.ui.theme.Spacing
import kotlinx.coroutines.launch

private data class BottomTab(val screen: Screen, val label: String)

// Roadmap -> PlacementPathScreen (the real staged/topic roadmap) and Practice ->
// RoadmapScreen (the day-task practice schedule) -- a deliberate swap fixing a pre-existing
// mislabeling where each tab rendered the other's content (see the redesign plan's Phase B2).
private val bottomTabs = listOf(
    BottomTab(Screen.Home, "Home"),
    BottomTab(Screen.Roadmap, "Roadmap"),
    BottomTab(Screen.Practice, "Practice"),
    BottomTab(Screen.Tests, "Tests"),
    BottomTab(Screen.Progress, "Progress"),
)

private data class DrawerItem(
    val label: String,
    val icon: ImageVector,
    val badge: String? = null,
    val onClick: () -> Unit,
)
private data class DrawerSection(val header: String?, val items: List<DrawerItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavHostController) {
    var selectedRoute by remember { mutableStateOf(Screen.Home.route) }
    val network = networkModule()
    val viewModel: MainScreenViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MainScreenViewModel(network.profileRepository, network.placementPathRepository) }
        },
    )
    val headerData by viewModel.headerData.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Roadmap/Practice/Progress are bottom-nav tabs now -- listing them here too would
    // duplicate navigation the spec explicitly calls out to avoid. Profile moved here from
    // the bottom nav; it's reached as a real pushed route (Screen.Profile is also registered
    // directly in NavGraph for Settings' "Edit Profile" shortcut) so both entry points behave
    // identically (a normal back button), rather than the bottom tabs' local-state swap.
    val drawerSections = listOf(
        DrawerSection(null, listOf(DrawerItem("Home", Icons.Filled.Home) { selectedRoute = Screen.Home.route })),
        DrawerSection(
            "CAREER",
            listOf(
                DrawerItem("Profile", Icons.Filled.Person) { navController.navigate(Screen.Profile.route) },
                DrawerItem("Resume", Icons.Filled.Description) { navController.navigate(Screen.ProfileResumeUpload.route) },
                DrawerItem("Resume ATS Check", Icons.Filled.FindInPage, badge = "New") {
                    navController.navigate(Screen.AtsCheckLanding.route)
                },
                DrawerItem("Career Path", Icons.AutoMirrored.Filled.TrendingUp, badge = "New") {
                    navController.navigate(Screen.CareerPathHome.route)
                },
                DrawerItem("Job Description Analyzer", Icons.Filled.WorkOutline) {
                    navController.navigate(Screen.JdAnalyzer.route)
                },
                DrawerItem("Mock Interviews", Icons.Filled.Mic) { navController.navigate(Screen.MockInterviewCategory.route) },
                DrawerItem("Placement Alarms", Icons.Filled.Alarm) { navController.navigate(Screen.AlarmList.route) },
                DrawerItem("Saved Roadmaps", Icons.Filled.History) { navController.navigate(Screen.SavedRoadmaps.route) },
            ),
        ),
        DrawerSection(
            "ACCOUNT",
            listOf(
                DrawerItem("Notifications", Icons.Filled.Notifications) {
                    navController.navigate(Screen.NotificationCenter.route)
                },
                DrawerItem("Settings", Icons.Filled.Settings) { navController.navigate(Screen.Settings.route) },
                DrawerItem("Subscriptions", Icons.Filled.WorkspacePremium) {
                    navController.navigate(Screen.Subscriptions.route)
                },
            ),
        ),
        DrawerSection(
            "SUPPORT",
            listOf(
                DrawerItem("Help & Support", Icons.AutoMirrored.Filled.HelpOutline) {
                    navController.navigate(Screen.HelpSupport.route)
                },
                DrawerItem("About Us", Icons.Filled.Info) { navController.navigate(Screen.AboutUs.route) },
            ),
        ),
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(headerData)
                drawerSections.forEach { section ->
                    HorizontalDivider()
                    if (section.header != null) {
                        Text(
                            text = section.header,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        )
                    }
                    section.items.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            badge = item.badge?.let { badgeText ->
                                {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                                        )
                                    }
                                }
                            },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                item.onClick()
                            },
                            modifier = Modifier.padding(horizontal = Spacing.sm),
                        )
                    }
                }
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Log out") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            network.authRepository.logout()
                            navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                        }
                    },
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AI Placement OS") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.NotificationCenter.route) }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                        }
                    },
                )
            },
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
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedRoute) {
                    Screen.Home.route -> HomeScreen(
                        onOpenPlacementAlarm = { navController.navigate(Screen.AlarmList.route) },
                        onOpenPlacementPath = { selectedRoute = Screen.Roadmap.route },
                        onOpenJdAnalyzer = { navController.navigate(Screen.JdAnalyzer.route) },
                        onOpenCareerPath = { pathId -> navController.navigate(careerPathDetailRoute(pathId)) },
                        onOpenCareerPathHome = { navController.navigate(Screen.CareerPathHome.route) },
                        onOpenAtsCheck = { checkId ->
                            navController.navigate(
                                if (checkId != null) atsCheckResultRoute(checkId) else Screen.AtsCheckPreCheck.route,
                            )
                        },
                        onOpenPractice = { selectedRoute = Screen.Practice.route },
                        onOpenTests = { selectedRoute = Screen.Tests.route },
                    )
                    // Swapped from the pre-fix mapping (see the bottomTabs comment above):
                    // Roadmap = the staged/topic roadmap, Practice = the day-task schedule.
                    Screen.Roadmap.route -> PlacementPathScreen(
                        onOpenTopic = { topicId -> navController.navigate(placementPathTopicDetailRoute(topicId)) },
                    )
                    Screen.Practice.route -> RoadmapScreen(
                        jdId = 0,
                        onOpenTask = { taskId -> navController.navigate(roadmapTaskDetailRoute(taskId, jdId = 0)) },
                    )
                    Screen.Tests.route -> TestCenterScreen(
                        onStartTest = { blueprintId -> navController.navigate(testFlowRoute(blueprintId, jdId = 0)) },
                    )
                    Screen.Progress.route -> ProgressDashboardScreen()
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(data: DrawerHeaderData?) {
    Column(modifier = Modifier.padding(Spacing.md)) {
        InitialsAvatar(name = data?.fullName)
        Text(
            text = data?.fullName ?: "Your profile",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        if (data?.email != null) {
            Text(
                text = data.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (data?.targetRoleName != null) {
            Text(
                text = data.targetRoleName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (data?.roadmapProgressPercent != null) {
            Text(
                text = "Roadmap progress: ${data.roadmapProgressPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** No avatar-photo concept exists anywhere (no backend column, no upload endpoint) -- a
 * deterministic initials circle is a real, always-correct fallback rather than a
 * non-functional camera-badge placeholder implying upload works when it doesn't. */
@Composable
private fun InitialsAvatar(name: String?) {
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (initial != null) {
                Text(
                    text = initial.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private fun BottomTab.icon() = when (screen) {
    Screen.Home -> Icons.Filled.Home
    Screen.Roadmap -> Icons.Filled.Timeline
    Screen.Practice -> Icons.Filled.Insights
    Screen.Tests -> Icons.AutoMirrored.Filled.Assignment
    Screen.Progress -> Icons.Filled.CheckCircle
    else -> Icons.Filled.Home
}
