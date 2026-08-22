package com.aiplacementos.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aiplacementos.app.ui.auth.LoginScreen
import com.aiplacementos.app.ui.auth.SignupScreen
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
 */
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Login.route) {
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

        // Prepare Me flow -- jdId (created by JDPasteScreen) flows through as a route arg.
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
                onStartTests = { navController.navigate(Screen.TestFlow.route) },
            )
        }
        composable(Screen.TestFlow.route) {
            com.aiplacementos.app.ui.tests.TestListScreen(
                onStartTest = { navController.navigate(Screen.TextInterview.route) },
            )
        }
        composable(Screen.TextInterview.route) {
            TextInterviewScreen(onFinish = { navController.navigate(Screen.VoiceInterview.route) })
        }
        composable(Screen.VoiceInterview.route) {
            VoiceInterviewScreen(onFinish = { navController.navigate(Screen.InterviewEvaluation.route) })
        }
        composable(Screen.InterviewEvaluation.route) {
            InterviewEvaluationScreen(onContinue = { navController.navigate(Screen.RoadmapUpdated.route) })
        }
        composable(Screen.RoadmapUpdated.route) {
            RoadmapScreen(onOpenTask = { navController.navigate(Screen.RoadmapTaskDetail.route) })
        }

        composable(Screen.RoadmapTaskDetail.route) { RoadmapTaskDetailScreen() }

        composable(Screen.Settings.route) {
            SettingsScreen(onOpenPrivacy = { navController.navigate(Screen.Privacy.route) })
        }
        composable(Screen.Privacy.route) { PrivacyScreen() }
    }
}
