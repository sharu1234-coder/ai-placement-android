package com.aiplacementos.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aiplacementos.app.alarm.PlacementAlarmService
import com.aiplacementos.app.ui.alarm.AlarmCreateScreen
import com.aiplacementos.app.ui.alarm.AlarmListScreen
import com.aiplacementos.app.ui.alarm.PlacementAlarmChallengeScreen
import com.aiplacementos.app.ui.arena.ArenaListScreen
import com.aiplacementos.app.ui.arena.ArenaSessionScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckAnalyzingScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckCategoryDetailScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckHistoryScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckImprovementPlanScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckIssuesScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckLandingScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckParsedViewScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckPreCheckScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckResultScreen
import com.aiplacementos.app.ui.atscheck.AtsCheckSectionAnalysisScreen
import com.aiplacementos.app.ui.careerdecision.CareerDecisionLabScreen
import com.aiplacementos.app.ui.careerpath.CareerPathDetailScreen
import com.aiplacementos.app.ui.careerpath.CareerPathGapAnalysisScreen
import com.aiplacementos.app.ui.careerpath.CareerPathHomeScreen
import com.aiplacementos.app.ui.careerpath.CareerPathInterviewPrepScreen
import com.aiplacementos.app.ui.careerpath.CareerPathProjectDetailScreen
import com.aiplacementos.app.ui.careerpath.CareerPathProjectsScreen
import com.aiplacementos.app.ui.careerpath.CareerPathReadinessScreen
import com.aiplacementos.app.ui.careerpath.CareerPathResumeConnectionScreen
import com.aiplacementos.app.ui.careerpath.CareerPathRoleSelectionScreen
import com.aiplacementos.app.ui.careerpath.CareerPathSavedRolesScreen
import com.aiplacementos.app.ui.careerpath.CareerPathSkillDetailScreen
import com.aiplacementos.app.ui.careergoal.CareerGoalDetailScreen
import com.aiplacementos.app.ui.careergoal.CareerGoalListScreen
import com.aiplacementos.app.ui.careerstrategy.CareerStrategyScreen
import com.aiplacementos.app.ui.careertwin.CareerTwinScreen
import com.aiplacementos.app.ui.projectbuilder.ProjectBuilderCreateScreen
import com.aiplacementos.app.ui.projectbuilder.ProjectBuilderDetailScreen
import com.aiplacementos.app.ui.projectbuilder.ProjectBuilderListScreen
import com.aiplacementos.app.ui.projectbuilder.ProjectProofScreen
import com.aiplacementos.app.ui.recruiterattack.RecruiterAttackCreateScreen
import com.aiplacementos.app.ui.recruiterattack.RecruiterAttackListScreen
import com.aiplacementos.app.ui.recruiterattack.RecruiterAttackResultsScreen
import com.aiplacementos.app.ui.recruiterattack.RecruiterAttackSessionScreen
import com.aiplacementos.app.ui.jobprep.JobMatchPrepResultScreen
import com.aiplacementos.app.ui.jobprep.JobMatchPrepScreen
import com.aiplacementos.app.ui.jobhunt.JobDetailScreen
import com.aiplacementos.app.ui.jobhunt.JobHuntHomeScreen
import com.aiplacementos.app.ui.jobhunt.JobHuntResultsScreen
import com.aiplacementos.app.ui.jobhunt.ApplicationReadinessScreen
import com.aiplacementos.app.ui.jobhunt.JobAlertsScreen
import com.aiplacementos.app.ui.jobhunt.JobFilters
import com.aiplacementos.app.ui.jobhunt.ReadinessGapDestination
import com.aiplacementos.app.ui.jobhunt.SkillMatchAnalysisScreen
import com.aiplacementos.app.ui.jobhunt.JobHuntSetupScreen
import com.aiplacementos.app.ui.jobhunt.SavedSearchesScreen
import com.aiplacementos.app.ui.jobhunt.SavedJobsScreen
import com.aiplacementos.app.ui.skillpassport.SkillPassportDetailScreen
import com.aiplacementos.app.ui.skillpassport.SkillPassportScreen
import com.aiplacementos.app.ui.auth.ForgotPasswordScreen
import com.aiplacementos.app.ui.auth.LoginScreen
import com.aiplacementos.app.ui.auth.SignupScreen
import com.aiplacementos.app.ui.auth.WelcomeScreen
import com.aiplacementos.app.ui.common.ErrorState
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.networkModule
import com.aiplacementos.app.ui.interview.InterviewEvaluationScreen
import com.aiplacementos.app.ui.interview.MockInterviewCategoryScreen
import com.aiplacementos.app.ui.interview.SimulationSetupScreen
import com.aiplacementos.app.ui.interview.TextInterviewScreen
import com.aiplacementos.app.ui.interview.VoiceInterviewScreen
import com.aiplacementos.app.ui.onboarding.OnboardingConfirmationScreen
import com.aiplacementos.app.ui.onboarding.OnboardingEducationScreen
import com.aiplacementos.app.ui.onboarding.OnboardingExperienceLevelScreen
import com.aiplacementos.app.ui.onboarding.OnboardingGoalsScreen
import com.aiplacementos.app.ui.onboarding.OnboardingNameScreen
import com.aiplacementos.app.ui.onboarding.OnboardingProfessionalPresenceScreen
import com.aiplacementos.app.ui.onboarding.OnboardingRoleScreen
import com.aiplacementos.app.ui.onboarding.OnboardingSkillsScreen
import com.aiplacementos.app.ui.onboarding.OnboardingSuccessScreen
import com.aiplacementos.app.ui.placementpath.PlacementPathTopicDetailScreen
import com.aiplacementos.app.ui.placementpath.SavedRoadmapsScreen
import com.aiplacementos.app.ui.presence.ProfessionalPresenceScreen
import com.aiplacementos.app.ui.profile.ProfileScreen
import com.aiplacementos.app.ui.profile.RoleSelectionScreen
import com.aiplacementos.app.ui.resume.ResumeScreen
import com.aiplacementos.app.ui.resume.ResumeUploadScreen
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.ui.about.AboutUsScreen
import com.aiplacementos.app.ui.notifications.NotificationCenterScreen
import com.aiplacementos.app.ui.roadmap.RoadmapScreen
import com.aiplacementos.app.ui.roadmap.RoadmapTaskDetailScreen
import com.aiplacementos.app.ui.settings.PrivacyScreen
import com.aiplacementos.app.ui.settings.SettingsScreen
import com.aiplacementos.app.ui.settings.TermsScreen
import com.aiplacementos.app.ui.subscriptions.SubscriptionsScreen
import com.aiplacementos.app.ui.support.HelpSupportScreen

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
import kotlinx.serialization.json.Json

@Composable
fun NavGraph(navController: NavHostController) {
    // Session restore: previously every cold start went to Login regardless of whether a
    // still-valid access token was already in TokenStore, forcing a needless re-login every
    // time. NavHost needs a concrete startDestination up front, so resolve it once before
    // rendering the graph at all rather than flashing LoginScreen and redirecting away from it.
    val network = networkModule()
    var startDestination by remember { mutableStateOf<String?>(null) }
    // A failed getMyProfile() call here (network down, server unreachable) used to crash the
    // app outright -- resolvePostAuthDestination() throws, and nothing caught it inside this
    // LaunchedEffect. A returning, already-logged-in user must always be able to reach
    // *something* on cold start, even offline, so a failure now surfaces a retryable error
    // screen instead of an uncaught exception.
    var sessionRestoreFailed by remember { mutableStateOf(false) }
    var retryToken by remember { mutableStateOf(0) }
    LaunchedEffect(retryToken) {
        sessionRestoreFailed = false
        startDestination = if (!network.authRepository.isLoggedIn()) {
            Screen.Welcome.route
        } else {
            try {
                resolvePostAuthDestination(network.profileRepository)
            } catch (e: Exception) {
                sessionRestoreFailed = true
                null
            }
        }
    }
    if (sessionRestoreFailed) {
        ErrorState(
            message = "Couldn't restore your session. Check your connection and try again.",
            onRetry = { retryToken++ },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val resolvedStart = startDestination
    if (resolvedStart == null) {
        LoadingState(modifier = Modifier.fillMaxSize())
        return
    }

    NavHost(navController = navController, startDestination = resolvedStart) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(Screen.Signup.route) },
                onNavigateToLogin = { navController.navigate(Screen.Login.route) },
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    val dest = resolvePostAuthDestinationOrMain(network.profileRepository)
                    navController.navigate(dest) { popUpTo(0) { inclusive = true } }
                },
                onNavigateToSignup = { navController.navigate(Screen.Signup.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
            )
        }
        composable(Screen.Signup.route) {
            SignupScreen(
                onSignupSuccess = {
                    val dest = resolvePostAuthDestinationOrMain(network.profileRepository)
                    navController.navigate(dest) { popUpTo(0) { inclusive = true } }
                },
                // Explicit navigate rather than popBackStack() -- Signup can be reached either
                // from Welcome or from Login, and this must always land on Login regardless.
                onNavigateToLogin = { navController.navigate(Screen.Login.route) },
            )
        }
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onResetSuccess = { navController.popBackStack() },
                onBackToLogin = { navController.popBackStack() },
            )
        }

        composable(Screen.OnboardingName.route) {
            OnboardingNameScreen(onNext = { navController.navigate(Screen.OnboardingEducation.route) })
        }
        composable(Screen.OnboardingEducation.route) {
            OnboardingEducationScreen(onNext = { navController.navigate(Screen.OnboardingResume.route) })
        }
        composable(Screen.OnboardingResume.route) {
            ResumeUploadScreen(onUploaded = { navController.navigate(Screen.OnboardingSkills.route) })
        }
        composable(Screen.OnboardingSkills.route) {
            OnboardingSkillsScreen(onNext = { navController.navigate(Screen.OnboardingRole.route) })
        }
        composable(Screen.OnboardingRole.route) {
            OnboardingRoleScreen(onNext = { navController.navigate(Screen.OnboardingGoals.route) })
        }
        composable(Screen.OnboardingGoals.route) {
            OnboardingGoalsScreen(onNext = { navController.navigate(Screen.OnboardingExperienceLevel.route) })
        }
        composable(Screen.OnboardingExperienceLevel.route) {
            OnboardingExperienceLevelScreen(onNext = { navController.navigate(Screen.OnboardingConfirmation.route) })
        }
        composable(Screen.OnboardingConfirmation.route) {
            OnboardingConfirmationScreen(
                onEditName = { navController.navigate(Screen.OnboardingName.route) },
                onEditEducation = { navController.navigate(Screen.OnboardingEducation.route) },
                onEditResume = { navController.navigate(Screen.OnboardingResume.route) },
                onEditSkills = { navController.navigate(Screen.OnboardingSkills.route) },
                onEditRole = { navController.navigate(Screen.OnboardingRole.route) },
                onEditGoals = { navController.navigate(Screen.OnboardingGoals.route) },
                onEditExperienceLevel = { navController.navigate(Screen.OnboardingExperienceLevel.route) },
                onRoadmapCreated = { navController.navigate(Screen.OnboardingSuccess.route) },
            )
        }
        composable(Screen.OnboardingSuccess.route) {
            OnboardingSuccessScreen(
                // Forwards to the optional GitHub step rather than to Main. This is a plain
                // push, NOT the back-stack clear: that moves to the step below, so it happens
                // exactly once and on whichever way the student leaves it.
                onGoToHome = { navController.navigate(Screen.OnboardingProfessionalPresence.route) },
            )
        }

        composable(Screen.OnboardingProfessionalPresence.route) {
            OnboardingProfessionalPresenceScreen(
                // One exit for both "Connect GitHub" and "Maybe later", so declining cannot
                // leave a student anywhere different from accepting. popUpTo(0) { inclusive }
                // wipes the whole onboarding back stack here -- pressing back from Home must
                // not re-enter a flow whose work is already saved server-side.
                onContinue = {
                    navController.navigate(Screen.Main.route) { popUpTo(0) { inclusive = true } }
                },
            )
        }

        composable(Screen.Main.route) {
            MainScreen(navController = navController)
        }

        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen(onRoleSelected = { navController.popBackStack() })
        }
        composable(Screen.RoleSelectionSecondary.route) {
            RoleSelectionScreen(isSecondary = true, onRoleSelected = { navController.popBackStack() })
        }
        composable(Screen.Resume.route) {
            ResumeScreen(onUploadOrReplace = { navController.navigate(Screen.ProfileResumeUpload.route) })
        }
        composable(Screen.ProfileResumeUpload.route) {
            // popBackStack returns to whatever pushed the upload form -- from the Resume screen
            // that is the Resume screen itself, which reloads and shows the new file.
            ResumeUploadScreen(onUploaded = { navController.popBackStack() })
        }

        // Shared assessment/interview engine -- jdId (created by Job Match & Prep) flows
        // all the way to the interview screens, so JD-specific tests/questions stay
        // possible end to end (see the class doc comment on Screen.kt's Prepare Me routes).
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
            arguments = listOf(
                navArgument("jdId") { type = NavType.IntType },
                navArgument("type") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            val interviewType = backStackEntry.arguments?.getString("type") ?: "technical"
            TextInterviewScreen(
                jdId = jdId,
                type = interviewType,
                onFinish = {
                    navController.navigate(voiceInterviewRoute(jdId, interviewType)) {
                        popUpTo(Screen.TextInterview.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Screen.VoiceInterview.route,
            arguments = listOf(
                navArgument("jdId") { type = NavType.IntType },
                navArgument("type") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            val interviewType = backStackEntry.arguments?.getString("type") ?: "technical"
            VoiceInterviewScreen(
                jdId = jdId,
                type = interviewType,
                onFinish = { interviewId ->
                    navController.navigate(interviewEvaluationRoute(interviewId, jdId)) {
                        popUpTo(Screen.VoiceInterview.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Screen.InterviewEvaluation.route,
            arguments = listOf(
                navArgument("interviewId") { type = NavType.IntType },
                navArgument("jdId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            InterviewEvaluationScreen(
                interviewId = backStackEntry.arguments?.getInt("interviewId") ?: 0,
                onContinue = {
                    navController.navigate(roadmapUpdatedRoute(jdId)) {
                        popUpTo(Screen.JobMatchPrep.route) { inclusive = false }
                    }
                },
            )
        }
        composable(
            Screen.RoadmapUpdated.route,
            arguments = listOf(navArgument("jdId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            RoadmapScreen(
                jdId = jdId,
                autoGenerateIfMissing = true,
                onOpenTask = { taskId -> navController.navigate(roadmapTaskDetailRoute(taskId, jdId)) },
            )
        }

        composable(
            Screen.RoadmapTaskDetail.route,
            arguments = listOf(
                navArgument("taskId") { type = NavType.IntType },
                navArgument("jdId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            RoadmapTaskDetailScreen(
                taskId = backStackEntry.arguments?.getInt("taskId") ?: 0,
                onBack = { navController.popBackStack() },
                jdId = backStackEntry.arguments?.getInt("jdId") ?: 0,
            )
        }

        composable(Screen.AlarmList.route) {
            AlarmListScreen(
                onCreateAlarm = { navController.navigate(Screen.AlarmCreate.route) },
                onEditAlarm = { alarmId -> navController.navigate(alarmEditRoute(alarmId)) },
                onOpenActiveChallenge = { clientAlarmId, alarmConfigId ->
                    navController.navigate(alarmChallengeRoute(clientAlarmId, alarmConfigId))
                },
            )
        }
        composable(Screen.AlarmCreate.route) {
            AlarmCreateScreen(
                onSaved = { navController.popBackStack() },
                onGoToResume = { navController.navigate(Screen.ProfileResumeUpload.route) },
            )
        }
        composable(
            Screen.AlarmEdit.route,
            arguments = listOf(navArgument("alarmId") { type = NavType.LongType }),
        ) { backStackEntry ->
            AlarmCreateScreen(
                onSaved = { navController.popBackStack() },
                onGoToResume = { navController.navigate(Screen.ProfileResumeUpload.route) },
                alarmId = backStackEntry.arguments?.getLong("alarmId") ?: 0L,
            )
        }
        composable(
            Screen.AlarmChallenge.route,
            arguments = listOf(
                navArgument("clientAlarmId") { type = NavType.StringType },
                navArgument("alarmConfigId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val context = LocalContext.current
            val clientAlarmId = backStackEntry.arguments?.getString("clientAlarmId") ?: return@composable
            val alarmConfigId = backStackEntry.arguments?.getLong("alarmConfigId") ?: 0L
            PlacementAlarmChallengeScreen(
                clientAlarmId = clientAlarmId,
                alarmConfigId = alarmConfigId,
                onAllSixCorrect = {
                    context.startService(PlacementAlarmService.stopIntent(context))
                },
                onDone = {
                    navController.navigate(Screen.AlarmList.route) {
                        popUpTo(Screen.AlarmChallenge.route) { inclusive = true }
                    }
                },
            )
        }

        // Personalized Placement Roadmap ("Placement Path" internally) -- the screen itself is
        // hosted directly by MainScreen's bottom-tab local state (Screen.Roadmap.route), which
        // is also where its onOpenTopic navigates into this pushed destination. Screen.PlacementPath
        // (a separate, never-navigated-to route registration) was removed as dead code -- nothing
        // in the app ever called navigate(Screen.PlacementPath.route).
        composable(
            Screen.PlacementPathTopicDetail.route,
            arguments = listOf(navArgument("topicId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getInt("topicId") ?: 0
            PlacementPathTopicDetailScreen(
                topicId = topicId,
                onStartPractice = { blueprintId -> navController.navigate(testFlowRoute(blueprintId, jdId = 0)) },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onOpenEditProfile = { navController.navigate(Screen.Profile.route) },
                onOpenChangePassword = { navController.navigate(Screen.ForgotPassword.route) },
                onOpenPrivacy = { navController.navigate(Screen.Privacy.route) },
                onOpenTerms = { navController.navigate(Screen.Terms.route) },
                onOpenSubscriptions = { navController.navigate(Screen.Subscriptions.route) },
                onOpenHelpSupport = { navController.navigate(Screen.HelpSupport.route) },
                onOpenAboutUs = { navController.navigate(Screen.AboutUs.route) },
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        // Also reachable as a real pushed destination from Settings' "Edit Profile" shortcut --
        // MainScreen's bottom tab reaches the same composable via local selectedRoute state
        // with no back-stack entry; this registration is what makes a *pushed* route to it work.
        composable(Screen.Profile.route) {
            ProfileScreen(
                onSelectRole = { navController.navigate(Screen.RoleSelection.route) },
                onSelectSecondaryRole = { navController.navigate(Screen.RoleSelectionSecondary.route) },
                onReplaceResume = { navController.navigate(Screen.ProfileResumeUpload.route) },
                // Into the existing screen, which owns every GitHub action. Profile only shows
                // state and offers the way in, so there is one place connect/sync/disconnect live.
                onOpenProfessionalPresence = {
                    navController.navigate(Screen.ProfessionalPresence.route)
                },
                // Same screen, told to start connecting on arrival: one tap on Profile's
                // "Connect GitHub" reaches GitHub, instead of a second identical button.
                onConnectGitHub = {
                    navController.navigate(Screen.ProfessionalPresenceConnect.route)
                },
            )
        }
        composable(Screen.Privacy.route) { PrivacyScreen() }
        composable(Screen.Terms.route) { TermsScreen() }

        composable(Screen.MockInterviewCategory.route) {
            MockInterviewCategoryScreen(
                onSelectCategory = { type -> navController.navigate(textInterviewRoute(0, type)) },
                onStartFullSimulation = { navController.navigate(Screen.SimulationSetup.route) },
                onOpenPastInterview = { interviewId -> navController.navigate(interviewHistoryDetailRoute(interviewId)) },
            )
        }
        composable(Screen.SimulationSetup.route) {
            SimulationSetupScreen(
                onStart = { jdId ->
                    // Reuses the existing interview route; only the type differs. popUpTo keeps
                    // setup out of the back stack so backing out of the interview returns to the
                    // category list rather than re-offering a setup step for a session that has
                    // already started server-side.
                    navController.navigate(textInterviewRoute(jdId, "full_simulation")) {
                        popUpTo(Screen.SimulationSetup.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Screen.InterviewHistoryDetail.route,
            arguments = listOf(navArgument("interviewId") { type = NavType.IntType }),
        ) { backStackEntry ->
            InterviewEvaluationScreen(
                interviewId = backStackEntry.arguments?.getInt("interviewId") ?: 0,
                onContinue = { navController.popBackStack() },
            )
        }

        composable(Screen.JobMatchPrep.route) {
            JobMatchPrepScreen(
                onAnalyzed = { jdId ->
                    // The analysis replaces the input screen in the back stack: pressing back
                    // from a finished result should return to the list, not re-open a form
                    // still holding the job description that was just analysed.
                    navController.navigate(jobMatchPrepResultRoute(jdId))
                },
                onOpenAnalysis = { jdId -> navController.navigate(jobMatchPrepResultRoute(jdId)) },
                onUploadResume = { navController.navigate(Screen.ProfileResumeUpload.route) },
                onViewResume = { navController.navigate(Screen.Resume.route) },
            )
        }
        composable(
            Screen.JobMatchPrepResult.route,
            arguments = listOf(navArgument("jdId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            JobMatchPrepResultScreen(
                jdId = jdId,
                onBack = { navController.popBackStack() },
                onGenerateRoadmap = {
                    navController.navigate(roadmapUpdatedRoute(jdId)) {
                        popUpTo(Screen.JobMatchPrep.route)
                    }
                },
                // The practice handoff carried over from the old Job Preparation flow: the
                // same blueprint-driven test flow every other practice entry point uses.
                onStartPractice = { blueprintId ->
                    navController.navigate(testFlowRoute(blueprintId, jdId))
                },
                onDeleted = { navController.popBackStack(Screen.JobMatchPrep.route, inclusive = false) },
                onCareerPathUpdated = { pathId -> navController.navigate(careerPathDetailRoute(pathId)) },
                onUploadResume = { navController.navigate(Screen.ProfileResumeUpload.route) },
            )
        }

        // Resume ATS Check -- drawer entry point, deliberately separate from the JD
        // Analyzer above (no JD is ever involved). "Upload New Resume" reuses the
        // existing ResumeUploadScreen (no duplicated upload code), landing on PreCheck.
        composable(Screen.AtsCheckLanding.route) {
            AtsCheckLandingScreen(
                // Both buttons land on PreCheck -- it already handles "show my current
                // resume" and "no resume yet, prompt to upload" (UiState.Empty) in one
                // place, so a second, differently-wired upload destination isn't needed.
                onCheckMyResume = { navController.navigate(Screen.AtsCheckPreCheck.route) },
                onUploadNewResume = { navController.navigate(Screen.AtsCheckPreCheck.route) },
                onOpenJobMatchPrep = { navController.navigate(Screen.JobMatchPrep.route) },
            )
        }
        composable(Screen.AtsCheckPreCheck.route) {
            AtsCheckPreCheckScreen(
                onAnalyze = { resumeId, targetRole ->
                    navController.navigate(atsCheckAnalyzingRoute(resumeId, targetRole))
                },
                // Screen.ProfileResumeUpload (not the jdId-scoped prepare-me upload route)
                // -- its onUploaded does navController.popBackStack(), correctly returning
                // here rather than into the unrelated JD-matching flow.
                onUploadNewResume = { navController.navigate(Screen.ProfileResumeUpload.route) },
            )
        }
        composable(
            Screen.AtsCheckAnalyzing.route,
            arguments = listOf(
                navArgument("resumeId") { type = NavType.IntType },
                navArgument("targetRole") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val resumeId = backStackEntry.arguments?.getInt("resumeId") ?: 0
            val targetRole = backStackEntry.arguments?.getString("targetRole")?.ifBlank { null }
            AtsCheckAnalyzingScreen(
                resumeId = resumeId,
                targetRole = targetRole,
                onComplete = { checkId ->
                    navController.navigate(atsCheckResultRoute(checkId)) {
                        popUpTo(Screen.AtsCheckAnalyzing.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Screen.AtsCheckResult.route,
            arguments = listOf(navArgument("checkId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val checkId = backStackEntry.arguments?.getInt("checkId") ?: 0
            AtsCheckResultScreen(
                checkId = checkId,
                onBack = { navController.popBackStack() },
                onOpenCategory = { category -> navController.navigate(atsCheckCategoryDetailRoute(checkId, category)) },
                onOpenSectionAnalysis = { navController.navigate(atsCheckSectionAnalysisRoute(checkId)) },
                onOpenIssues = { navController.navigate(atsCheckIssuesRoute(checkId)) },
                onOpenImprovementPlan = { navController.navigate(atsCheckImprovementPlanRoute(checkId)) },
                onOpenHistory = { navController.navigate(Screen.AtsCheckHistory.route) },
                onOpenParsedView = { navController.navigate(atsCheckParsedViewRoute(checkId)) },
            )
        }
        composable(
            Screen.AtsCheckCategoryDetail.route,
            arguments = listOf(
                navArgument("checkId") { type = NavType.IntType },
                navArgument("category") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            AtsCheckCategoryDetailScreen(
                checkId = backStackEntry.arguments?.getInt("checkId") ?: 0,
                category = backStackEntry.arguments?.getString("category") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Screen.AtsCheckSectionAnalysis.route,
            arguments = listOf(navArgument("checkId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val checkId = backStackEntry.arguments?.getInt("checkId") ?: 0
            AtsCheckSectionAnalysisScreen(
                checkId = checkId,
                onOpenCategory = { category -> navController.navigate(atsCheckCategoryDetailRoute(checkId, category)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Screen.AtsCheckIssues.route,
            arguments = listOf(navArgument("checkId") { type = NavType.IntType }),
        ) { backStackEntry ->
            AtsCheckIssuesScreen(
                checkId = backStackEntry.arguments?.getInt("checkId") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Screen.AtsCheckImprovementPlan.route,
            arguments = listOf(navArgument("checkId") { type = NavType.IntType }),
        ) { backStackEntry ->
            AtsCheckImprovementPlanScreen(
                checkId = backStackEntry.arguments?.getInt("checkId") ?: 0,
                onOpenResumeUpload = { navController.navigate(Screen.ProfileResumeUpload.route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.AtsCheckHistory.route) {
            AtsCheckHistoryScreen(
                onOpenCheck = { checkId ->
                    navController.navigate(atsCheckResultRoute(checkId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Screen.AtsCheckParsedView.route,
            arguments = listOf(navArgument("checkId") { type = NavType.IntType }),
        ) { backStackEntry ->
            AtsCheckParsedViewScreen(
                checkId = backStackEntry.arguments?.getInt("checkId") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }

        // Career Path -- drawer entry point, deliberately independent of the
        // Roadmap/Placement Path bottom tab: separate screens, separate ViewModels,
        // separate repository, no shared state (see ui/careerpath/ package docs).
        composable(Screen.CareerPathHome.route) {
            CareerPathHomeScreen(
                onSelectRole = { navController.navigate(Screen.CareerPathRoleSelection.route) },
                onOpenDetail = { pathId -> navController.navigate(careerPathDetailRoute(pathId)) },
                onOpenSaved = { navController.navigate(Screen.CareerPathSavedRoles.route) },
            )
        }
        composable(Screen.CareerPathRoleSelection.route) {
            CareerPathRoleSelectionScreen(
                onBack = { navController.popBackStack() },
                onGenerated = { pathId -> navController.navigate(careerPathDetailRoute(pathId)) },
            )
        }
        composable(
            Screen.CareerPathDetail.route,
            arguments = listOf(navArgument("pathId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val pathId = backStackEntry.arguments?.getInt("pathId") ?: 0
            CareerPathDetailScreen(
                pathId = pathId,
                onBack = { navController.popBackStack() },
                onOpenSkillDetail = { pid, itemId -> navController.navigate(careerPathSkillDetailRoute(pid, itemId)) },
                onOpenProjects = { pid -> navController.navigate(careerPathProjectsRoute(pid)) },
                onOpenGapAnalysis = { pid -> navController.navigate(careerPathGapAnalysisRoute(pid)) },
                onOpenResumeConnection = { pid -> navController.navigate(careerPathResumeConnectionRoute(pid)) },
                onOpenInterviewPrep = { pid -> navController.navigate(careerPathInterviewPrepRoute(pid)) },
                onOpenReadiness = { pid -> navController.navigate(careerPathReadinessRoute(pid)) },
            )
        }
        composable(
            Screen.CareerPathSkillDetail.route,
            arguments = listOf(
                navArgument("pathId") { type = NavType.IntType },
                navArgument("itemId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            CareerPathSkillDetailScreen(
                pathId = backStackEntry.arguments?.getInt("pathId") ?: 0,
                itemId = backStackEntry.arguments?.getInt("itemId") ?: 0,
                onBack = { navController.popBackStack() },
                onOpenPractice = { blueprintId -> navController.navigate(testFlowRoute(blueprintId, jdId = 0)) },
            )
        }
        composable(
            Screen.CareerPathGapAnalysis.route,
            arguments = listOf(navArgument("pathId") { type = NavType.IntType }),
        ) { backStackEntry ->
            CareerPathGapAnalysisScreen(
                pathId = backStackEntry.arguments?.getInt("pathId") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Screen.CareerPathProjects.route,
            arguments = listOf(navArgument("pathId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val pathId = backStackEntry.arguments?.getInt("pathId") ?: 0
            CareerPathProjectsScreen(
                pathId = pathId,
                onBack = { navController.popBackStack() },
                onOpenProject = { pid, projectId -> navController.navigate(careerPathProjectDetailRoute(pid, projectId)) },
            )
        }
        composable(
            Screen.CareerPathProjectDetail.route,
            arguments = listOf(
                navArgument("pathId") { type = NavType.IntType },
                navArgument("projectId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            CareerPathProjectDetailScreen(
                pathId = backStackEntry.arguments?.getInt("pathId") ?: 0,
                projectId = backStackEntry.arguments?.getInt("projectId") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Screen.CareerPathResumeConnection.route,
            arguments = listOf(navArgument("pathId") { type = NavType.IntType }),
        ) { backStackEntry ->
            CareerPathResumeConnectionScreen(
                pathId = backStackEntry.arguments?.getInt("pathId") ?: 0,
                onBack = { navController.popBackStack() },
                onUploadResume = { navController.navigate(Screen.ProfileResumeUpload.route) },
                // Reuses the existing ATS Check flow directly -- zero new resume logic.
                onImproveResume = { resumeId, roleName -> navController.navigate(atsCheckAnalyzingRoute(resumeId, roleName)) },
            )
        }
        composable(
            Screen.CareerPathInterviewPrep.route,
            arguments = listOf(navArgument("pathId") { type = NavType.IntType }),
        ) { backStackEntry ->
            CareerPathInterviewPrepScreen(
                pathId = backStackEntry.arguments?.getInt("pathId") ?: 0,
                onBack = { navController.popBackStack() },
                // Deep-links into the existing Mock Interview flow -- zero new interview UI.
                onPractice = { jdId, category -> navController.navigate(textInterviewRoute(jdId, category)) },
            )
        }
        composable(
            Screen.CareerPathReadiness.route,
            arguments = listOf(navArgument("pathId") { type = NavType.IntType }),
        ) { backStackEntry ->
            CareerPathReadinessScreen(
                pathId = backStackEntry.arguments?.getInt("pathId") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.CareerPathSavedRoles.route) {
            CareerPathSavedRolesScreen(
                onBack = { navController.popBackStack() },
                onOpenPath = { pathId -> navController.navigate(careerPathDetailRoute(pathId)) },
            )
        }
        composable(Screen.CareerTwin.route) {
            CareerTwinScreen(
                onSelectRole = { navController.navigate(Screen.CareerPathRoleSelection.route) },
                onUpgrade = { navController.navigate(Screen.Subscriptions.route) },
                // Opens Professional Presence already connecting, the same one-tap entry the
                // Profile card uses -- Career Twin never runs an OAuth flow of its own.
                onConnectGitHub = { navController.navigate(Screen.ProfessionalPresenceConnect.route) },
                // The same destination table Career Strategy resolves its recommendations
                // through: Next Best Action emits that identical vocabulary, so there is one
                // place that decides where a recommendation goes.
                onOpenDestination = { destination ->
                    navController.navigate(careerStrategyDestinationRoute(destination))
                },
            )
        }
        composable(Screen.CareerStrategy.route) {
            CareerStrategyScreen(
                onSelectRole = { navController.navigate(Screen.CareerPathRoleSelection.route) },
                onUpgrade = { navController.navigate(Screen.Subscriptions.route) },
                onOpenDestination = { destination ->
                    navController.navigate(careerStrategyDestinationRoute(destination))
                },
            )
        }
        composable(
            Screen.CareerStrategyForJob.route,
            arguments = listOf(navArgument("listingId") { type = NavType.IntType }),
        ) { backStackEntry ->
            // Only the id crosses the boundary. The verdict about this job is the server's and
            // is fetched fresh -- passing a decision through a navigation argument would let a
            // stale one survive a process death and outlive the data it was computed from.
            val listingId = backStackEntry.arguments?.getInt("listingId") ?: 0
            CareerStrategyScreen(
                listingId = listingId,
                onSelectRole = { navController.navigate(Screen.CareerPathRoleSelection.route) },
                onUpgrade = { navController.navigate(Screen.Subscriptions.route) },
                onOpenDestination = { destination ->
                    navController.navigate(careerStrategyDestinationRoute(destination))
                },
            )
        }
        composable(Screen.ProfessionalPresence.route) {
            ProfessionalPresenceScreen(
                onBack = { navController.popBackStack() },
                onOpenResume = { navController.navigate(Screen.Resume.route) },
                onOpenSkillPassport = { navController.navigate(Screen.SkillPassport.route) },
            )
        }
        composable(Screen.ProfessionalPresenceConnect.route) {
            ProfessionalPresenceScreen(
                onBack = { navController.popBackStack() },
                onOpenResume = { navController.navigate(Screen.Resume.route) },
                onOpenSkillPassport = { navController.navigate(Screen.SkillPassport.route) },
                connectOnOpen = true,
            )
        }
        composable(Screen.SkillPassport.route) {
            SkillPassportScreen(onOpenSkill = { skill -> navController.navigate(skillPassportDetailRoute(skill)) })
        }
        composable(Screen.CareerGoals.route) {
            CareerGoalListScreen(
                onOpenGoal = { goalId -> navController.navigate(careerGoalDetailRoute(goalId)) },
            )
        }
        composable(
            Screen.CareerGoalDetail.route,
            arguments = listOf(navArgument("careerGoalId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val careerGoalId = backStackEntry.arguments?.getInt("careerGoalId") ?: 0
            CareerGoalDetailScreen(
                careerGoalId = careerGoalId,
                onBack = { navController.popBackStack() },
                // Career Goals owns no interview or practice flow of its own. Every step of its
                // preparation plan hands off to the feature that already exists, through the
                // same destination table Career Strategy has always used.
                onOpenDestination = { destination ->
                    navController.navigate(careerStrategyDestinationRoute(destination))
                },
            )
        }
        composable(
            Screen.SkillPassportDetail.route,
            arguments = listOf(navArgument("skill") { type = NavType.StringType }),
        ) { backStackEntry ->
            val skill = backStackEntry.arguments?.getString("skill").orEmpty()
            SkillPassportDetailScreen(skill = skill, onBack = { navController.popBackStack() })
        }

        composable(Screen.JobHunt.route) {
            JobHuntHomeScreen(
                onBack = { navController.popBackStack() },
                onOpenSetup = { navController.navigate(Screen.JobHuntSetup.route) },
                onOpenResults = { navController.navigate(Screen.JobHuntResults.route) },
                onOpenTracker = { navController.navigate(Screen.JobHuntTracker.route) },
                onOpenJob = { listingId -> navController.navigate(jobHuntDetailRoute(listingId)) },
                onOpenSavedSearches = { navController.navigate(Screen.JobHuntSavedSearches.route) },
                onOpenAlerts = { navController.navigate(Screen.JobHuntAlerts.route) },
                onOpenResumeUpload = { navController.navigate(Screen.ProfileResumeUpload.route) },
                onOpenSubscriptions = { navController.navigate(Screen.Subscriptions.route) },
            )
        }
        composable(Screen.JobHuntSetup.route) {
            JobHuntSetupScreen(
                onBack = { navController.popBackStack() },
                // Setup is a step on the way to results, not a place to come back to, so it is
                // popped off the stack before the results screen is pushed -- otherwise Back
                // from results would land the student in the form they just completed.
                onSaved = {
                    navController.navigate(Screen.JobHuntResults.route) {
                        popUpTo(Screen.JobHuntSetup.route) { inclusive = true }
                    }
                },
                onOpenResumeUpload = { navController.navigate(Screen.ProfileResumeUpload.route) },
            )
        }
        composable(
            Screen.JobHuntResults.route,
            arguments = listOf(
                navArgument("filters") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            // A malformed or stale argument opens an unfiltered search rather than crashing
            // the screen. The filters are a convenience; losing them is recoverable, and the
            // student can see immediately that nothing is applied.
            val initialFilters = backStackEntry.arguments?.getString("filters")
                ?.let { runCatching { Json.decodeFromString<JobFilters>(it) }.getOrNull() }
                ?: JobFilters()
            JobHuntResultsScreen(
                initialFilters = initialFilters,
                onBack = { navController.popBackStack() },
                onOpenJob = { listingId -> navController.navigate(jobHuntDetailRoute(listingId)) },
                onOpenSaved = { navController.navigate(Screen.JobHuntSaved.route) },
                onOpenSetup = { navController.navigate(Screen.JobHuntSetup.route) },
                onOpenSubscriptions = { navController.navigate(Screen.Subscriptions.route) },
                onOpenSavedSearches = { navController.navigate(Screen.JobHuntSavedSearches.route) },
            )
        }
        composable(Screen.JobHuntAlerts.route) {
            JobAlertsScreen(
                onBack = { navController.popBackStack() },
                onOpenSubscriptions = { navController.navigate(Screen.Subscriptions.route) },
                onOpenJob = { listingId -> navController.navigate(jobHuntDetailRoute(listingId)) },
            )
        }
        composable(
            Screen.JobHuntSkillAnalysis.route,
            arguments = listOf(navArgument("listingId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val listingId = backStackEntry.arguments?.getInt("listingId") ?: 0
            SkillMatchAnalysisScreen(
                listingId = listingId,
                onBack = { navController.popBackStack() },
                onOpenRoadmap = { navController.navigate(Screen.Roadmap.route) },
                onOpenReadiness = { navController.navigate(jobHuntReadinessRoute(listingId)) },
            )
        }
        composable(
            Screen.JobHuntReadiness.route,
            arguments = listOf(navArgument("listingId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val listingId = backStackEntry.arguments?.getInt("listingId") ?: 0
            ApplicationReadinessScreen(
                listingId = listingId,
                onBack = { navController.popBackStack() },
                onOpenSubscriptions = { navController.navigate(Screen.Subscriptions.route) },
                onFixGap = { destination ->
                    // Each gap kind routes to the feature that actually closes it, rather
                    // than to one catch-all screen that would be wrong for most of them.
                    val route = when (destination) {
                        ReadinessGapDestination.SkillPassport -> Screen.SkillPassport.route
                        ReadinessGapDestination.Resume -> Screen.AtsCheckLanding.route
                        ReadinessGapDestination.Interview -> Screen.MockInterviewCategory.route
                        ReadinessGapDestination.Profile -> Screen.Profile.route
                        ReadinessGapDestination.Roadmap -> Screen.Roadmap.route
                    }
                    navController.navigate(route)
                },
            )
        }
        composable(Screen.JobHuntSavedSearches.route) {
            SavedSearchesScreen(
                onBack = { navController.popBackStack() },
                onRunSearch = { filters ->
                    navController.navigate(jobHuntResultsRoute(Json.encodeToString(filters))) {
                        // Replace this screen: coming "back" from results the student just
                        // asked for should return to the jobs list, not to the picker.
                        popUpTo(Screen.JobHuntSavedSearches.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.JobHuntSaved.route) {
            SavedJobsScreen(
                onBack = { navController.popBackStack() },
                onOpenJob = { listingId -> navController.navigate(jobHuntDetailRoute(listingId)) },
            )
        }
        composable(Screen.JobHuntTracker.route) {
            SavedJobsScreen(
                onBack = { navController.popBackStack() },
                onOpenJob = { listingId -> navController.navigate(jobHuntDetailRoute(listingId)) },
                trackerMode = true,
            )
        }
        composable(
            Screen.JobHuntDetail.route,
            arguments = listOf(navArgument("listingId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val listingId = backStackEntry.arguments?.getInt("listingId") ?: 0
            JobDetailScreen(
                listingId = listingId,
                onBack = { navController.popBackStack() },
                onOpenRoadmap = { navController.navigate(Screen.Roadmap.route) },
                onOpenSkillAnalysis = {
                    navController.navigate(jobHuntSkillAnalysisRoute(listingId))
                },
                onOpenStrategy = {
                    navController.navigate(careerStrategyJobRoute(listingId))
                },
            )
        }

        composable(Screen.ProjectBuilderList.route) {
            ProjectBuilderListScreen(
                onOpenBuild = { buildId -> navController.navigate(projectBuilderDetailRoute(buildId)) },
                onCreateNew = { navController.navigate(Screen.ProjectBuilderCreate.route) },
            )
        }
        composable(Screen.ProjectBuilderCreate.route) {
            ProjectBuilderCreateScreen(
                onBack = { navController.popBackStack() },
                onCreated = { buildId ->
                    navController.navigate(projectBuilderDetailRoute(buildId)) {
                        popUpTo(Screen.ProjectBuilderList.route)
                    }
                },
            )
        }
        composable(
            Screen.ProjectBuilderDetail.route,
            arguments = listOf(navArgument("buildId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val buildId = backStackEntry.arguments?.getInt("buildId") ?: 0
            ProjectBuilderDetailScreen(
                buildId = buildId,
                onBack = { navController.popBackStack() },
                onViewProof = { id -> navController.navigate(projectBuilderProofRoute(id)) },
            )
        }
        composable(
            Screen.ProjectBuilderProof.route,
            arguments = listOf(navArgument("buildId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val buildId = backStackEntry.arguments?.getInt("buildId") ?: 0
            ProjectProofScreen(buildId = buildId, onBack = { navController.popBackStack() })
        }

        composable(Screen.RecruiterAttackList.route) {
            RecruiterAttackListScreen(
                onOpenSession = { sessionId, claimLabel ->
                    navController.navigate(recruiterAttackSessionRoute(sessionId, claimLabel))
                },
                onCreateNew = { navController.navigate(Screen.RecruiterAttackCreate.route) },
            )
        }
        composable(Screen.RecruiterAttackCreate.route) {
            RecruiterAttackCreateScreen(
                onBack = { navController.popBackStack() },
                onCreated = { sessionId, claimLabel ->
                    navController.navigate(recruiterAttackSessionRoute(sessionId, claimLabel)) {
                        popUpTo(Screen.RecruiterAttackList.route)
                    }
                },
            )
        }
        composable(
            Screen.RecruiterAttackSession.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.IntType },
                navArgument("claimLabel") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getInt("sessionId") ?: 0
            val claimLabel = backStackEntry.arguments?.getString("claimLabel").orEmpty()
            RecruiterAttackSessionScreen(
                sessionId = sessionId,
                claimLabel = claimLabel,
                onFinish = { id ->
                    navController.navigate(recruiterAttackResultsRoute(id)) {
                        popUpTo(Screen.RecruiterAttackList.route)
                    }
                },
            )
        }
        composable(
            Screen.RecruiterAttackResults.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getInt("sessionId") ?: 0
            RecruiterAttackResultsScreen(
                sessionId = sessionId,
                onDone = {
                    navController.navigate(Screen.RecruiterAttackList.route) {
                        popUpTo(Screen.RecruiterAttackList.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.CareerDecisionLab.route) {
            CareerDecisionLabScreen(onUpgrade = { navController.navigate(Screen.Subscriptions.route) })
        }

        composable(Screen.ArenaList.route) {
            ArenaListScreen(
                onStarted = { start ->
                    navController.navigate(
                        arenaSessionRoute(start.arenaAttemptId, start.assessmentId, start.timeLimitSeconds)
                    )
                },
            )
        }
        composable(
            Screen.ArenaSession.route,
            arguments = listOf(
                navArgument("arenaAttemptId") { type = NavType.IntType },
                navArgument("assessmentId") { type = NavType.IntType },
                navArgument("timeLimitSeconds") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val arenaAttemptId = backStackEntry.arguments?.getInt("arenaAttemptId") ?: 0
            val assessmentId = backStackEntry.arguments?.getInt("assessmentId") ?: 0
            val timeLimitSeconds = backStackEntry.arguments?.getInt("timeLimitSeconds") ?: 0
            ArenaSessionScreen(
                arenaAttemptId = arenaAttemptId,
                assessmentId = assessmentId,
                timeLimitSeconds = timeLimitSeconds,
                onDone = {
                    navController.navigate(Screen.ArenaList.route) {
                        popUpTo(Screen.ArenaList.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.SavedRoadmaps.route) { SavedRoadmapsScreen() }
        composable(Screen.NotificationCenter.route) { NotificationCenterScreen() }
        composable(Screen.Subscriptions.route) { SubscriptionsScreen() }
        composable(Screen.HelpSupport.route) { HelpSupportScreen() }
        composable(Screen.AboutUs.route) {
            AboutUsScreen(
                onOpenPrivacy = { navController.navigate(Screen.Privacy.route) },
                onOpenTerms = { navController.navigate(Screen.Terms.route) },
            )
        }
    }
}

/** Used for cold start (already-logged-in) and right after a login/signup success --
 * a profile that hasn't finished onboarding always restarts at OnboardingName (no
 * exact-step resumption, see the Phase 3 plan's scope decision). */
private suspend fun resolvePostAuthDestination(profileRepository: ProfileRepository): String =
    if (profileRepository.getMyProfile().onboardingCompleted) Screen.Main.route else Screen.OnboardingName.route

/** Login/signup-only wrapper: a valid session was just established (the token is good), so a
 * transient profile-fetch failure right after must not strand the user with no way forward --
 * falls back to Main, where the dashboard already has its own retry/error handling, rather than
 * crashing or blocking on a screen with no retry action. Cold start uses the throwing
 * [resolvePostAuthDestination] directly instead, since it has a dedicated retryable error UI. */
private suspend fun resolvePostAuthDestinationOrMain(profileRepository: ProfileRepository): String =
    try {
        resolvePostAuthDestination(profileRepository)
    } catch (e: Exception) {
        Screen.Main.route
    }

/**
 * Backend destination key -> an existing route.
 *
 * Career Strategy never invents a place to send someone: every destination it emits is a
 * feature this app already has, and this is the one table that says which. The `else` branch
 * keeps the student where they are rather than crashing on a destination added by a newer
 * backend -- a no-op button is a small disappointment, a crash is not.
 */
internal fun careerStrategyDestinationRoute(destination: String): String = when (destination) {
    "roadmap" -> Screen.Roadmap.route
    "practice" -> Screen.Practice.route
    "project_builder" -> Screen.ProjectBuilderList.route
    "skill_passport" -> Screen.SkillPassport.route
    "resume_ats" -> Screen.AtsCheckLanding.route
    "interview" -> Screen.MockInterviewCategory.route
    // Added with Career Goals, whose preparation plan names these two modes directly. Both
    // are existing screens; before this they fell through to the `else` and quietly sent the
    // student to Career Strategy instead of the practice the step described.
    "recruiter_attack" -> Screen.RecruiterAttackList.route
    "full_simulation" -> Screen.SimulationSetup.route
    "job_hunt" -> Screen.JobHunt.route
    "tracker" -> Screen.JobHuntTracker.route
    "career_path" -> Screen.CareerPathHome.route
    "profile" -> Screen.Profile.route
    else -> Screen.CareerStrategy.route
}
