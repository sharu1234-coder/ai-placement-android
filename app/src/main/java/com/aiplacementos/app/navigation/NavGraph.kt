package com.aiplacementos.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aiplacementos.app.ui.auth.LoginScreen
import com.aiplacementos.app.ui.auth.SignupScreen
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.interview.InterviewEvaluationScreen
import com.aiplacementos.app.ui.interview.TextInterviewScreen
import com.aiplacementos.app.ui.interview.VoiceInterviewScreen
import com.aiplacementos.app.ui.job.JDPasteScreen
import com.aiplacementos.app.ui.job.JobMatchScreen
import com.aiplacementos.app.ui.job.PrepareMeSummaryScreen
import com.aiplacementos.app.ui.profile.RoleSelectionScreen
import com.aiplacementos.app.ui.resume.ResumeUploadScreen
import com.aiplacementos.app.ui.roadmap.RoadmapScreen
import com.aiplacementos.app.ui.roadmap.RoadmapTaskDetailScreen
import com.aiplacementos.app.ui.settings.PrivacyScreen
import com.aiplacementos.app.ui.settings.SettingsScreen

/**
 * Root graph: Auth -> Main (bottom-nav shell). The "Prepare Me" flow is a guided
 * linear journey pushed from Home, not a tab. See PROJECT_HANDOFF.md section 8.3.
 *
 * Every forward navigation out of a screen whose ViewModel ends in a terminal "completed"
 * state (test-taking -> result, text interview -> voice interview, voice interview ->
 * evaluation) uses `popUpTo(...) { inclusive = true }` to remove that screen from the back
 * stack. Without it, pressing Back re-enters the completed screen, whose LaunchedEffect
 * re-fires on the still-Completed state and immediately re-navigates forward again --
 * silently duplicating the next screen on the stack every time Back is pressed.
 */
@Composable
fun NavGraph(navController: NavHostController) {
    // Session restore: previously every cold start went to Login regardless of whether a
    // still-valid access token was already in TokenStore, forcing a needless re-login every
    // time. NavHost needs a concrete startDestination up front, so resolve it once before
    // rendering the graph at all rather than flashing LoginScreen and redirecting away from it.
    val network = networkModule()
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        startDestination = if (network.authRepository.isLoggedIn()) Screen.Main.route else Screen.Login.route
    }
    val resolvedStart = startDestination
    if (resolvedStart == null) {
        LoadingState(modifier = Modifier.fillMaxSize())
        return
    }

    NavHost(navController = navController, startDestination = resolvedStart) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignup = { navController.navigate(Screen.Signup.route) },
            )
        }
        composable(Screen.Signup.route) {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }

        composable(Screen.Main.route) {
            MainScreen(navController = navController)
        }

        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen(onRoleSelected = { navController.popBackStack() })
        }

        // Prepare Me flow -- jdId (created by JDPasteScreen) flows through as a route arg,
        // all the way to the interview screens, so JD-specific tests/questions stay
        // possible end to end (see the class doc comment on Screen.kt's Prepare Me routes).
        composable(Screen.RoleOrJdEntry.route) {
            JDPasteScreen(onContinue = { jdId -> navController.navigate(resumeUploadRoute(jdId)) })
        }
        composable(
            Screen.ResumeUpload.route,
            arguments = listOf(navArgument("jdId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            ResumeUploadScreen(onUploaded = { navController.navigate(analysisResultRoute(jdId)) })
        }
        composable(
            Screen.AnalysisResult.route,
            arguments = listOf(navArgument("jdId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            JobMatchScreen(
                jdId = jdId,
                onContinue = { navController.navigate(prepareMeSummaryRoute(jdId)) },
            )
        }
        composable(
            Screen.PrepareMeSummary.route,
            arguments = listOf(navArgument("jdId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            PrepareMeSummaryScreen(
                jdId = jdId,
                onStartTests = { blueprintId -> navController.navigate(testFlowRoute(blueprintId, jdId)) },
            )
        }
        composable(
            Screen.TestFlow.route,
            arguments = listOf(
                navArgument("blueprintId") { type = NavType.IntType },
                navArgument("jdId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val blueprintId = backStackEntry.arguments?.getInt("blueprintId") ?: 0
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            com.aiplacementos.app.ui.tests.TestListScreen(
                blueprintId = blueprintId,
                onStartTest = { assessmentId -> navController.navigate(testTakingRoute(assessmentId, jdId)) },
            )
        }
        composable(
            Screen.TestTaking.route,
            arguments = listOf(
                navArgument("assessmentId") { type = NavType.IntType },
                navArgument("jdId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val assessmentId = backStackEntry.arguments?.getInt("assessmentId") ?: 0
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            com.aiplacementos.app.ui.tests.TestTakingScreen(
                assessmentId = assessmentId,
                onFinish = {
                    navController.navigate(testResultRoute(assessmentId, jdId)) {
                        popUpTo(Screen.TestTaking.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Screen.TestResult.route,
            arguments = listOf(
                navArgument("assessmentId") { type = NavType.IntType },
                navArgument("jdId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            com.aiplacementos.app.ui.tests.TestResultScreen(
                assessmentId = backStackEntry.arguments?.getInt("assessmentId") ?: 0,
                onContinue = { navController.navigate(textInterviewRoute(jdId)) },
            )
        }
        composable(
            Screen.TextInterview.route,
            arguments = listOf(navArgument("jdId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            TextInterviewScreen(
                jdId = jdId,
                onFinish = {
                    navController.navigate(voiceInterviewRoute(jdId)) {
                        popUpTo(Screen.TextInterview.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Screen.VoiceInterview.route,
            arguments = listOf(navArgument("jdId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            VoiceInterviewScreen(
                jdId = jdId,
                onFinish = { interviewId ->
                    navController.navigate(interviewEvaluationRoute(interviewId)) {
                        popUpTo(Screen.VoiceInterview.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Screen.InterviewEvaluation.route,
            arguments = listOf(navArgument("interviewId") { type = NavType.IntType }),
        ) { backStackEntry ->
            InterviewEvaluationScreen(
                interviewId = backStackEntry.arguments?.getInt("interviewId") ?: 0,
                onContinue = { navController.navigate(Screen.RoadmapUpdated.route) },
            )
        }
        composable(Screen.RoadmapUpdated.route) {
            RoadmapScreen(onOpenTask = { navController.navigate(Screen.RoadmapTaskDetail.route) })
        }

        composable(Screen.RoadmapTaskDetail.route) { RoadmapTaskDetailScreen() }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onOpenPrivacy = { navController.navigate(Screen.Privacy.route) },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Privacy.route) { PrivacyScreen() }
    }
}
