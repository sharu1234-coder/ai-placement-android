package com.aiplacementos.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
import com.aiplacementos.app.ui.careerpath.CareerPathGenerationRecovery
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
import com.aiplacementos.app.ui.common.BackBar
import com.aiplacementos.app.ui.common.toUserMessage
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
import com.aiplacementos.app.ui.jobhunt.AddManualJobScreen
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
import com.aiplacementos.app.ui.history.ConversationDetailScreen
import com.aiplacementos.app.ui.history.ConversationHistoryScreen
import com.aiplacementos.app.ui.interview.MockInterviewCategoryScreen
import com.aiplacementos.app.ui.interview.SimulationSetupScreen
import com.aiplacementos.app.ui.interview.TextInterviewScreen
import com.aiplacementos.app.ui.interview.VoiceInterviewScreen
import com.aiplacementos.app.ui.theme.Motion
import com.aiplacementos.app.ui.voiceagent.VoiceAgentScreen
import com.aiplacementos.app.voice.realtime.VoiceAgentMode
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
    var sessionRestoreError by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableStateOf(0) }
    LaunchedEffect(retryToken) {
        sessionRestoreError = null
        startDestination = if (!network.authRepository.isLoggedIn()) {
            Screen.Welcome.route
        } else {
            try {
                resolvePostAuthDestination(network.profileRepository)
            } catch (e: Exception) {
                // Keeping the throwable is the point: discarding it left this screen able
                // to say only "check your connection", including for a 500 or an expired
                // session -- the two failures a student is most likely to hit here.
                sessionRestoreError = e.toUserMessage("restore your session")
                null
            }
        }
    }
    // A Career Path generation whose response was lost, and whose deadline has passed while the
    // app was gone, is ended here -- see CareerPathGenerationRecovery.resolveAtLaunch.
    LaunchedEffect(Unit) {
        if (network.authRepository.isLoggedIn()) {
            CareerPathGenerationRecovery(network.careerPathRepository, network.pendingOperationStore)
                .resolveAtLaunch()
        }
    }
    val restoreError = sessionRestoreError
    if (restoreError != null) {
        ErrorState(
            message = restoreError,
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

    /*
     * Screen transitions, set once for all 103 destinations.
     *
     * Navigation Compose's default is a cross-fade, which gives no sense of direction: pushing
     * a detail screen and backing out of it look identical, so the app reads as a deck of
     * unrelated cards rather than a place with a structure. A short horizontal slide paired
     * with a fade says which way the student just moved, and the pop transitions mirror it so
     * going back visibly undoes the push.
     *
     * The distance is deliberately a fraction of the width rather than the full width: a
     * full-width slide at this duration reads as a swipe the student did not make. Motion.enter
     * decelerates into place, which is what makes the arriving screen look like it settled.
     *
     * The system's "Remove animations" setting is honoured by the platform for these -- when
     * ANIMATOR_DURATION_SCALE is 0, Android scales the animators themselves to zero, so this
     * does not need (and must not have) its own `rememberReduceMotion()` check. Composables
     * that animate with their own clock, like the recording pulse, still check it themselves.
     */
    val slide = tween<IntOffset>(durationMillis = Motion.standard, easing = Motion.enter)
    val dim = tween<Float>(durationMillis = Motion.standard, easing = Motion.enter)
    NavHost(
        navController = navController,
        startDestination = resolvedStart,
        enterTransition = {
            slideInHorizontally(animationSpec = slide, initialOffsetX = { it / 6 }) +
                fadeIn(animationSpec = dim)
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = slide, targetOffsetX = { -it / 12 }) +
                fadeOut(animationSpec = dim)
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = slide, initialOffsetX = { -it / 12 }) +
                fadeIn(animationSpec = dim)
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = slide, targetOffsetX = { it / 6 }) +
                fadeOut(animationSpec = dim)
        },
    ) {
        safeAreaComposable(Screen.Welcome.route) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(Screen.Signup.route) },
                onNavigateToLogin = { navController.navigate(Screen.Login.route) },
            )
        }
        backComposable(navController, Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    val dest = resolvePostAuthDestinationOrMain(network.profileRepository)
                    navController.navigate(dest) { popUpTo(0) { inclusive = true } }
                },
                onNavigateToSignup = { navController.navigate(Screen.Signup.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
            )
        }
        backComposable(navController, Screen.Signup.route) {
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
        safeAreaComposable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onResetSuccess = { navController.popBackStack() },
                onBackToLogin = { navController.popBackStack() },
            )
        }

        safeAreaComposable(Screen.OnboardingName.route) {
            OnboardingNameScreen(onNext = { navController.navigate(Screen.OnboardingEducation.route) })
        }
        backComposable(navController, Screen.OnboardingEducation.route) {
            OnboardingEducationScreen(onNext = { navController.navigate(Screen.OnboardingResume.route) })
        }
        backComposable(navController, Screen.OnboardingResume.route) {
            ResumeUploadScreen(onUploaded = { navController.navigate(Screen.OnboardingSkills.route) })
        }
        backComposable(navController, Screen.OnboardingSkills.route) {
            OnboardingSkillsScreen(onNext = { navController.navigate(Screen.OnboardingRole.route) })
        }
        backComposable(navController, Screen.OnboardingRole.route) {
            OnboardingRoleScreen(onNext = { navController.navigate(Screen.OnboardingGoals.route) })
        }
        backComposable(navController, Screen.OnboardingGoals.route) {
            OnboardingGoalsScreen(onNext = { navController.navigate(Screen.OnboardingExperienceLevel.route) })
        }
        backComposable(navController, Screen.OnboardingExperienceLevel.route) {
            OnboardingExperienceLevelScreen(onNext = { navController.navigate(Screen.OnboardingConfirmation.route) })
        }
        backComposable(navController, Screen.OnboardingConfirmation.route) {
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
        safeAreaComposable(Screen.OnboardingSuccess.route) {
            OnboardingSuccessScreen(
                // Forwards to the optional GitHub step rather than to Main. This is a plain
                // push, NOT the back-stack clear: that moves to the step below, so it happens
                // exactly once and on whichever way the student leaves it.
                onGoToHome = { navController.navigate(Screen.OnboardingProfessionalPresence.route) },
            )
        }

        backComposable(navController, Screen.OnboardingProfessionalPresence.route) {
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

        backComposable(navController, Screen.RoleSelection.route) {
            RoleSelectionScreen(onRoleSelected = { navController.popBackStack() })
        }
        backComposable(navController, Screen.RoleSelectionSecondary.route) {
            RoleSelectionScreen(isSecondary = true, onRoleSelected = { navController.popBackStack() })
        }
        backComposable(navController, Screen.Resume.route) {
            ResumeScreen(onUploadOrReplace = { navController.navigate(Screen.ProfileResumeUpload.route) })
        }
        backComposable(navController, Screen.ProfileResumeUpload.route) {
            // popBackStack returns to whatever pushed the upload form -- from the Resume screen
            // that is the Resume screen itself, which reloads and shows the new file.
            ResumeUploadScreen(onUploaded = { navController.popBackStack() })
        }

        // Shared assessment/interview engine -- jdId (created by Job Match & Prep) flows
        // all the way to the interview screens, so JD-specific tests/questions stay
        // possible end to end (see the class doc comment on Screen.kt's Prepare Me routes).
        backComposable(navController, Screen.TestFlow.route,
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
        backComposable(navController, Screen.TestTaking.route,
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
        backComposable(navController, Screen.TestResult.route,
            arguments = listOf(
                navArgument("assessmentId") { type = NavType.IntType },
                navArgument("jdId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val jdId = backStackEntry.arguments?.getInt("jdId") ?: 0
            com.aiplacementos.app.ui.tests.TestResultScreen(
                assessmentId = backStackEntry.arguments?.getInt("assessmentId") ?: 0,
                onContinue = { navController.navigate(voiceInterviewRoute(jdId)) },
            )
        }
        backComposable(navController, Screen.TextInterview.route, title = "Written Interview",
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
        backComposable(navController, Screen.VoiceInterview.route,
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
                // The interview journey into the realtime agent. Both ids come from the state
                // the server confirmed, and the agent's own handshake re-verifies ownership of
                // the pair before its socket is accepted -- neither check stands alone.
                onLiveConversation = { interviewId, questionId ->
                    navController.navigate(
                        voiceAgentRoute(
                            mode = VoiceAgentMode.INTERVIEW.wire,
                            interviewId = interviewId,
                            questionId = questionId,
                        )
                    )
                },
                onFinish = { interviewId ->
                    navController.navigate(interviewEvaluationRoute(interviewId, jdId)) {
                        popUpTo(Screen.VoiceInterview.route) { inclusive = true }
                    }
                },
            )
        }
        backComposable(navController, Screen.VoiceAgent.route,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("interviewId") { type = NavType.IntType },
                navArgument("questionId") { type = NavType.IntType },
                navArgument("listingId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            // The mode is resolved against the closed enum here, so an unknown path segment
            // falls back to the read-only coach rather than reaching the interview engine. The
            // server validates it a second time and closes the socket on anything it does not
            // recognise -- neither check is load-bearing alone.
            val requested = backStackEntry.arguments?.getString("mode")
            val mode = VoiceAgentMode.entries.firstOrNull { it.wire == requested } ?: VoiceAgentMode.COACH
            // 0 is the sentinel for "absent", which is what coach mode passes.
            val interviewId = backStackEntry.arguments?.getInt("interviewId")?.takeIf { it > 0 }
            val questionId = backStackEntry.arguments?.getInt("questionId")?.takeIf { it > 0 }
            // The advert the conversation is about. Absent on the drawer route, which is why
            // that route offers no save-job action: the control needs a real listing, and the
            // server would refuse an empty argument map anyway.
            val listingId = backStackEntry.arguments?.getInt("listingId")?.takeIf { it > 0 }
            VoiceAgentScreen(
                mode = mode,
                interviewId = interviewId,
                interviewQuestionId = questionId,
                listingId = listingId,
            )
        }
        backComposable(navController, Screen.InterviewEvaluation.route,
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
        backComposable(navController, Screen.RoadmapUpdated.route,
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
                onBack = { navController.popBackStack() },
                onCreateAlarm = { navController.navigate(Screen.AlarmCreate.route) },
                onEditAlarm = { alarmId -> navController.navigate(alarmEditRoute(alarmId)) },
                onOpenActiveChallenge = { clientAlarmId, alarmConfigId ->
                    navController.navigate(alarmChallengeRoute(clientAlarmId, alarmConfigId))
                },
            )
        }
        backComposable(navController, Screen.AlarmCreate.route) {
            AlarmCreateScreen(
                onSaved = { navController.popBackStack() },
                onGoToResume = { navController.navigate(Screen.ProfileResumeUpload.route) },
            )
        }
        backComposable(navController, Screen.AlarmEdit.route,
            arguments = listOf(navArgument("alarmId") { type = NavType.LongType }),
        ) { backStackEntry ->
            AlarmCreateScreen(
                onSaved = { navController.popBackStack() },
                onGoToResume = { navController.navigate(Screen.ProfileResumeUpload.route) },
                alarmId = backStackEntry.arguments?.getLong("alarmId") ?: 0L,
            )
        }
        backComposable(navController, Screen.AlarmChallenge.route,
            arguments = listOf(
                navArgument("clientAlarmId") { type = NavType.StringType },
                navArgument("alarmConfigId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val context = LocalContext.current
            val clientAlarmId = backStackEntry.arguments?.getString("clientAlarmId") ?: return@backComposable
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
        backComposable(navController, Screen.PlacementPathTopicDetail.route,
            arguments = listOf(navArgument("topicId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getInt("topicId") ?: 0
            PlacementPathTopicDetailScreen(
                topicId = topicId,
                onStartPractice = { blueprintId -> navController.navigate(testFlowRoute(blueprintId, jdId = 0)) },
            )
        }

        backComposable(navController, Screen.Settings.route) {
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
        backComposable(navController, Screen.Profile.route) {
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
        backComposable(navController, Screen.Privacy.route, title = "Privacy Policy") { PrivacyScreen() }
        backComposable(navController, Screen.Terms.route, title = "Terms of Service") { TermsScreen() }

        safeAreaComposable(Screen.ConversationHistory.route) {
            ConversationHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenConversation = { id ->
                    navController.navigate(conversationDetailRoute(id))
                },
            )
        }
        safeAreaComposable(
            Screen.ConversationDetail.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            // 0 is unreachable through conversationDetailRoute, which only ever receives
            // a server id; the detail screen reports a 404 honestly if one arrived.
            val id = backStackEntry.arguments?.getInt("conversationId") ?: 0
            ConversationDetailScreen(
                conversationId = id,
                onBack = { navController.popBackStack() },
            )
        }
        backComposable(navController, Screen.MockInterviewCategory.route) {
            MockInterviewCategoryScreen(
                // Voice is the default path through the interview engine. The written route
                // stays wired for a student who picks "Type answers" -- same engine, same
                // grading, so nothing is lost by preferring voice here.
                onSelectCategory = { type -> navController.navigate(voiceInterviewRoute(0, type)) },
                onSelectWrittenCategory = { type -> navController.navigate(textInterviewRoute(0, type)) },
                onStartFullSimulation = { navController.navigate(Screen.SimulationSetup.route) },
                onOpenPastInterview = { interviewId -> navController.navigate(interviewHistoryDetailRoute(interviewId)) },
            )
        }
        backComposable(navController, Screen.SimulationSetup.route) {
            SimulationSetupScreen(
                onStart = { jdId ->
                    // Reuses the existing interview route; only the type differs. popUpTo keeps
                    // setup out of the back stack so backing out of the interview returns to the
                    // category list rather than re-offering a setup step for a session that has
                    // already started server-side.
                    navController.navigate(voiceInterviewRoute(jdId, "full_simulation")) {
                        popUpTo(Screen.SimulationSetup.route) { inclusive = true }
                    }
                },
            )
        }
        backComposable(navController, Screen.InterviewHistoryDetail.route,
            arguments = listOf(navArgument("interviewId") { type = NavType.IntType }),
        ) { backStackEntry ->
            InterviewEvaluationScreen(
                interviewId = backStackEntry.arguments?.getInt("interviewId") ?: 0,
                onContinue = { navController.popBackStack() },
            )
        }

        backComposable(navController, Screen.JobMatchPrep.route) {
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
        safeAreaComposable(
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
        backComposable(navController, Screen.AtsCheckLanding.route) {
            AtsCheckLandingScreen(
                // Both buttons land on PreCheck -- it already handles "show my current
                // resume" and "no resume yet, prompt to upload" (UiState.Empty) in one
                // place, so a second, differently-wired upload destination isn't needed.
                onCheckMyResume = { navController.navigate(Screen.AtsCheckPreCheck.route) },
                onUploadNewResume = { navController.navigate(Screen.AtsCheckPreCheck.route) },
                onOpenJobMatchPrep = { navController.navigate(Screen.JobMatchPrep.route) },
            )
        }
        backComposable(navController, Screen.AtsCheckPreCheck.route, title = "Resume check") {
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
        backComposable(navController, Screen.AtsCheckAnalyzing.route, title = "Resume check",
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
        backComposable(navController, Screen.CareerPathHome.route) {
            CareerPathHomeScreen(
                onSelectRole = { navController.navigate(Screen.CareerPathRoleSelection.route) },
                onOpenDetail = { pathId -> navController.navigate(careerPathDetailRoute(pathId)) },
                onOpenSaved = { navController.navigate(Screen.CareerPathSavedRoles.route) },
            )
        }
        safeAreaComposable(Screen.CareerPathRoleSelection.route) {
            CareerPathRoleSelectionScreen(
                onBack = { navController.popBackStack() },
                onGenerated = { pathId -> navController.navigate(careerPathDetailRoute(pathId)) },
            )
        }
        safeAreaComposable(
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
        safeAreaComposable(
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
        safeAreaComposable(
            Screen.CareerPathGapAnalysis.route,
            arguments = listOf(navArgument("pathId") { type = NavType.IntType }),
        ) { backStackEntry ->
            CareerPathGapAnalysisScreen(
                pathId = backStackEntry.arguments?.getInt("pathId") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
        safeAreaComposable(
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
        safeAreaComposable(
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
        safeAreaComposable(
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
        safeAreaComposable(
            Screen.CareerPathInterviewPrep.route,
            arguments = listOf(navArgument("pathId") { type = NavType.IntType }),
        ) { backStackEntry ->
            CareerPathInterviewPrepScreen(
                pathId = backStackEntry.arguments?.getInt("pathId") ?: 0,
                onBack = { navController.popBackStack() },
                // Deep-links into the existing Mock Interview flow -- zero new interview UI.
                onPractice = { jdId, category -> navController.navigate(voiceInterviewRoute(jdId, category)) },
            )
        }
        safeAreaComposable(
            Screen.CareerPathReadiness.route,
            arguments = listOf(navArgument("pathId") { type = NavType.IntType }),
        ) { backStackEntry ->
            CareerPathReadinessScreen(
                pathId = backStackEntry.arguments?.getInt("pathId") ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
        safeAreaComposable(Screen.CareerPathSavedRoles.route) {
            CareerPathSavedRolesScreen(
                onBack = { navController.popBackStack() },
                onOpenPath = { pathId -> navController.navigate(careerPathDetailRoute(pathId)) },
            )
        }
        backComposable(navController, Screen.CareerTwin.route) {
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
        backComposable(navController, Screen.CareerStrategy.route) {
            CareerStrategyScreen(
                onSelectRole = { navController.navigate(Screen.CareerPathRoleSelection.route) },
                onUpgrade = { navController.navigate(Screen.Subscriptions.route) },
                onOpenDestination = { destination ->
                    navController.navigate(careerStrategyDestinationRoute(destination))
                },
            )
        }
        backComposable(navController, Screen.CareerStrategyForJob.route,
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
        backComposable(navController, Screen.SkillPassport.route) {
            SkillPassportScreen(onOpenSkill = { skill -> navController.navigate(skillPassportDetailRoute(skill)) })
        }
        composable(Screen.CareerGoals.route) {
            CareerGoalListScreen(
                onBack = { navController.popBackStack() },
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
                onAddManualJob = { navController.navigate(Screen.JobHuntAddManual.route) },
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
                // Carried as a string rather than an Int so it can genuinely be absent: an
                // IntType argument cannot be null, and a sentinel like -1 would have to be
                // remembered and checked at every use.
                navArgument("savedSearchId") {
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
            // Same tolerance, and it degrades the right way: an unreadable id means this stops
            // being a saved-search run and becomes an ordinary search of the same filters,
            // which shows the student the jobs they asked for rather than an error.
            val savedSearchId = backStackEntry.arguments?.getString("savedSearchId")?.toIntOrNull()
            JobHuntResultsScreen(
                initialFilters = initialFilters,
                savedSearchId = savedSearchId,
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
                onRunSearch = { savedSearchId, filters ->
                    navController.navigate(
                        jobHuntResultsRoute(Json.encodeToString(filters), savedSearchId),
                    ) {
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
                onAddManualJob = { navController.navigate(Screen.JobHuntAddManual.route) },
            )
        }
        composable(Screen.JobHuntTracker.route) {
            SavedJobsScreen(
                onBack = { navController.popBackStack() },
                onOpenJob = { listingId -> navController.navigate(jobHuntDetailRoute(listingId)) },
                onAddManualJob = { navController.navigate(Screen.JobHuntAddManual.route) },
                trackerMode = true,
            )
        }
        composable(Screen.JobHuntAddManual.route) {
            AddManualJobScreen(
                onBack = { navController.popBackStack() },
                // Straight to the tracker, and the form is popped so Back does not return to
                // a filled-in form for a job that has already been added. The tracker reloads
                // on resume (LifecycleResumeEffect), so the new row is there on arrival
                // without this screen having to pass it along.
                onAdded = {
                    navController.navigate(Screen.JobHuntTracker.route) {
                        popUpTo(Screen.JobHuntAddManual.route) { inclusive = true }
                    }
                },
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
                // VA-7's save-job confirmation becomes reachable here and only here: this
                // screen is showing one authoritative, server-returned listing, so its id is
                // the one the action is allowed to carry.
                onDiscussWithCoach = {
                    navController.navigate(
                        voiceAgentRoute(mode = VoiceAgentMode.COACH.wire, listingId = listingId)
                    )
                },
            )
        }

        composable(Screen.ProjectBuilderList.route) {
            ProjectBuilderListScreen(
                onBack = { navController.popBackStack() },
                onOpenBuild = { buildId -> navController.navigate(projectBuilderDetailRoute(buildId)) },
                onCreateNew = { navController.navigate(Screen.ProjectBuilderCreate.route) },
            )
        }
        safeAreaComposable(Screen.ProjectBuilderCreate.route) {
            ProjectBuilderCreateScreen(
                onBack = { navController.popBackStack() },
                onCreated = { buildId ->
                    navController.navigate(projectBuilderDetailRoute(buildId)) {
                        popUpTo(Screen.ProjectBuilderList.route)
                    }
                },
            )
        }
        safeAreaComposable(
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
        safeAreaComposable(
            Screen.ProjectBuilderProof.route,
            arguments = listOf(navArgument("buildId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val buildId = backStackEntry.arguments?.getInt("buildId") ?: 0
            ProjectProofScreen(buildId = buildId, onBack = { navController.popBackStack() })
        }

        composable(Screen.RecruiterAttackList.route) {
            RecruiterAttackListScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId, claimLabel ->
                    navController.navigate(recruiterAttackSessionRoute(sessionId, claimLabel))
                },
                onCreateNew = { navController.navigate(Screen.RecruiterAttackCreate.route) },
            )
        }
        safeAreaComposable(Screen.RecruiterAttackCreate.route) {
            RecruiterAttackCreateScreen(
                onBack = { navController.popBackStack() },
                onCreated = { sessionId, claimLabel ->
                    navController.navigate(recruiterAttackSessionRoute(sessionId, claimLabel)) {
                        popUpTo(Screen.RecruiterAttackList.route)
                    }
                },
            )
        }
        backComposable(navController, Screen.RecruiterAttackSession.route, title = "Recruiter Attack",
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
        backComposable(navController, Screen.RecruiterAttackResults.route,
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

        backComposable(navController, Screen.CareerDecisionLab.route) {
            CareerDecisionLabScreen(onUpgrade = { navController.navigate(Screen.Subscriptions.route) })
        }

        backComposable(navController, Screen.ArenaList.route) {
            ArenaListScreen(
                onStarted = { start ->
                    navController.navigate(
                        arenaSessionRoute(start.arenaAttemptId, start.assessmentId, start.timeLimitSeconds)
                    )
                },
            )
        }
        backComposable(navController, Screen.ArenaSession.route,
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

        backComposable(navController, Screen.SavedRoadmaps.route) { SavedRoadmapsScreen() }
        backComposable(navController, Screen.NotificationCenter.route) { NotificationCenterScreen() }
        backComposable(navController, Screen.Subscriptions.route) { SubscriptionsScreen() }
        backComposable(navController, Screen.HelpSupport.route) { HelpSupportScreen() }
        backComposable(navController, Screen.AboutUs.route) {
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

/**
 * The inset contract for every destination in this graph that does not own a Scaffold.
 *
 * [MainActivity] calls `enableEdgeToEdge()`, so the app draws behind the status and navigation
 * bars and something must put the content back inside the safe area. Until Phase 28 the only
 * thing in the app that did was [MainScreen]'s Scaffold, which covers the five bottom tabs it
 * hosts directly -- every other destination is a sibling entry in this NavHost and inherited
 * nothing, so its first row of content started at y=0 underneath the clock.
 *
 * The padding is taken from `WindowInsets.safeDrawing` -- the system's own report of the status
 * bar, the navigation bar or large-screen taskbar, the display cutout and the keyboard -- and
 * never from a fixed dp value, which would be wrong on the next device.
 *
 * `safeDrawingPadding` also *consumes* what it applies, so a screen that already handles part
 * of this (the 71 call sites of `DetailTopBar`, whose TopAppBar pads itself by the top inset)
 * sees the remaining insets as zero and does not double-apply.
 *
 * Destinations that own a Scaffold deliberately keep plain `composable`: their Scaffold already
 * applies the content insets, and their top and bottom bars are supposed to draw *behind* the
 * system bars. Padding above them would pull those bars down and leave a strip of bare window
 * behind -- breaking screens that are already correct.
 */
private fun NavGraphBuilder.safeAreaComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route = route, arguments = arguments) { backStackEntry ->
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { content(backStackEntry) }
}
/**
 * A destination that a student was pushed into, with the way back drawn on it.
 *
 * The same safe-area treatment as [safeAreaComposable], plus a [BackBar] above the content. A
 * navigation audit on 2026-10-18 found 52 of 97 screens with no visible back control at all, and
 * this is how the graph supplies one without editing every screen to accept an `onBack` lambda.
 *
 * [title] is left null for a screen that already draws its own heading, which is most of them --
 * passing one there would print the same words twice. See [BackBar] for that rule.
 *
 * Destinations that own a `Scaffold` are deliberately NOT routed through here: this applies
 * `safeDrawingPadding()`, and a Scaffold applies the content insets itself, so the two together
 * would push the screen down by the status bar twice. Those four screens take an `onBack`
 * parameter and place the bar in their own `topBar` slot instead.
 */
private fun NavGraphBuilder.backComposable(
    navController: NavHostController,
    route: String,
    title: String? = null,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route = route, arguments = arguments) { backStackEntry ->
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        BackBar(onBack = { navController.popBackStack() }, title = title)
        Box(modifier = Modifier.fillMaxSize()) { content(backStackEntry) }
    }
}
