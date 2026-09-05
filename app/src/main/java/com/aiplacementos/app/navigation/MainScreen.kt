package com.aiplacementos.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.aiplacementos.app.ui.premium.PremiumStatusUiState
import com.aiplacementos.app.ui.premium.PremiumStatusViewModel
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
    var selectedRoute by rememberSaveable { mutableStateOf(Screen.Home.route) }
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
    // duplicate navigation the spec explicitly calls out to avoid, so they're deliberately
    // absent from every section below despite appearing in the reference image's drawer.
    // Grouping mirrors the reference image's CAREER/PREPARE/BUILD PROOF/ACCOUNT split (Phase
    // "UI matching" pass) rather than the earlier single oversized CAREER bucket. Placement
    // Alarms sits inside PREPARE exactly where the reference image places it; Saved Roadmaps
    // and Notifications aren't in the reference image's drawer at all (it isn't exhaustive) so
    // they're homed under the nearest thematic group instead of inventing an extra section.
    val drawerSections = listOf(
        DrawerSection(null, listOf(DrawerItem("Home", Icons.Filled.Home) { selectedRoute = Screen.Home.route })),
        DrawerSection(
            "CAREER",
            listOf(
                DrawerItem("Career Twin", Icons.Filled.Psychology, badge = "New") {
                    navController.navigate(Screen.CareerTwin.route)
                },
                DrawerItem("Career Path", Icons.AutoMirrored.Filled.TrendingUp, badge = "New") {
                    navController.navigate(Screen.CareerPathHome.route)
                },
                DrawerItem("Career Decision Lab", Icons.AutoMirrored.Filled.CompareArrows, badge = "New") {
                    navController.navigate(Screen.CareerDecisionLab.route)
                },
            ),
        ),
        DrawerSection(
            "PREPARE",
            listOf(
                DrawerItem("Resume", Icons.Filled.Description) { navController.navigate(Screen.ProfileResumeUpload.route) },
                DrawerItem("Resume ATS Check", Icons.Filled.FindInPage, badge = "New") {
                    navController.navigate(Screen.AtsCheckLanding.route)
                },
                DrawerItem("Job Description Analyzer", Icons.Filled.WorkOutline) {
                    navController.navigate(Screen.JdAnalyzer.route)
                },
                // Real-device finding: this entire flow (paste a role/JD -> resume check ->
                // match analysis -> prep plan -> tests -> interviews -> roadmap update) was
                // fully built and registered in NavGraph but had no entry point anywhere in
                // the app -- Screen.RoleOrJdEntry was never the target of a single
                // navController.navigate() call. Distinct from JD Analyzer above (a quick
                // skill-match check): this is the longer guided preparation flow.
                DrawerItem("Job Preparation", Icons.Filled.School) {
                    navController.navigate(Screen.RoleOrJdEntry.route)
                },
                DrawerItem("Mock Interviews", Icons.Filled.Mic) { navController.navigate(Screen.MockInterviewCategory.route) },
                DrawerItem("Placement Alarms", Icons.Filled.Alarm) { navController.navigate(Screen.AlarmList.route) },
                DrawerItem("Saved Roadmaps", Icons.Filled.History) { navController.navigate(Screen.SavedRoadmaps.route) },
            ),
        ),
        // Split out of CAREER (Phase 12): these four are the "build and prove it" verticals --
        // distinct from the planning/analysis items above -- matching the reference image's
        // dedicated Home/navigation grouping.
        DrawerSection(
            "BUILD PROOF",
            listOf(
                DrawerItem("Project Builder", Icons.Filled.Construction, badge = "New") {
                    navController.navigate(Screen.ProjectBuilderList.route)
                },
                DrawerItem("Skill Passport", Icons.Filled.Badge, badge = "New") {
                    navController.navigate(Screen.SkillPassport.route)
                },
                DrawerItem("Recruiter Attack", Icons.Filled.Shield, badge = "New") {
                    navController.navigate(Screen.RecruiterAttackList.route)
                },
                DrawerItem("Placement Arena", Icons.Filled.EmojiEvents, badge = "New") {
                    navController.navigate(Screen.ArenaList.route)
                },
            ),
        ),
        DrawerSection(
            "ACCOUNT",
            listOf(
                DrawerItem("Notifications", Icons.Filled.Notifications) {
                    navController.navigate(Screen.NotificationCenter.route)
                },
                DrawerItem("Profile", Icons.Filled.Person) { navController.navigate(Screen.Profile.route) },
                DrawerItem("Subscriptions", Icons.Filled.WorkspacePremium) {
                    navController.navigate(Screen.Subscriptions.route)
                },
                DrawerItem("Settings", Icons.Filled.Settings) { navController.navigate(Screen.Settings.route) },
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
                // Root cause of the drawer's unscrollable content (real-device finding): this
                // Column previously had no scroll modifier at all -- ModalDrawerSheet itself
                // doesn't scroll its content, so once the header + 3 sections + ~13 items +
                // logout exceeded the screen height (routine on smaller phones or with a
                // larger system font), everything past the fold was simply unreachable, not
                // merely hidden. Header stays outside the scrollable region (stays visually
                // stable per spec) while the section list + logout scroll in the remaining
                // space via Modifier.weight(1f).verticalScroll(...).
                Column(modifier = Modifier.fillMaxHeight()) {
                    DrawerHeader(headerData)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
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
                }
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
                        onOpenCareerTwin = { navController.navigate(Screen.CareerTwin.route) },
                        onOpenSkillPassport = { navController.navigate(Screen.SkillPassport.route) },
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            Text(
                text = data?.fullName?.let { "Hi, $it 👋" } ?: "Your profile",
                style = MaterialTheme.typography.titleMedium,
            )
            PremiumBadgeSection()
        }
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

/** Self-contained widget, same "own small ViewModel, renders nothing while loading/not
 * premium" convention as HomeScreen's TodaysMissionSection -- the drawer header shouldn't
 * block on or be disrupted by this one fetch. Reads the same fail-closed, server-authoritative
 * PremiumStatusViewModel every PaywallGate call site already shares. */
@Composable
private fun PremiumBadgeSection() {
    val network = networkModule()
    val viewModel: PremiumStatusViewModel = viewModel(
        factory = viewModelFactory { initializer { PremiumStatusViewModel(network.subscriptionRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state
    if (current is PremiumStatusUiState.Loaded && current.isPremium) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(start = Spacing.sm),
        ) {
            Text(
                text = "Premium",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
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
