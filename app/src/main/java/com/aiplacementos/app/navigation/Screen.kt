package com.aiplacementos.app.navigation

sealed class Screen(val route: String) {
    data object Welcome : Screen("auth/welcome")
    data object Login : Screen("auth/login")
    data object Signup : Screen("auth/signup")
    data object ForgotPassword : Screen("auth/forgot-password")

    // Onboarding -- reached after signup/login when the profile isn't onboarded yet
    // (NavGraph's cold-start/login/signup gating). Route-based, no shared ViewModel
    // across steps, same pattern as the Prepare Me flow below -- each step screen
    // saves its own slice of the profile and forwards with a plain no-arg callback.
    data object OnboardingName : Screen("onboarding/name")
    data object OnboardingEducation : Screen("onboarding/education")
    data object OnboardingResume : Screen("onboarding/resume")
    data object OnboardingSkills : Screen("onboarding/skills")
    data object OnboardingRole : Screen("onboarding/role")
    data object OnboardingGoals : Screen("onboarding/goals")
    data object OnboardingExperienceLevel : Screen("onboarding/experience-level")
    data object OnboardingConfirmation : Screen("onboarding/confirmation")
    data object OnboardingSuccess : Screen("onboarding/success")

    data object Home : Screen("home")
    data object Roadmap : Screen("roadmap")
    data object Practice : Screen("practice")
    data object Tests : Screen("tests")
    data object Progress : Screen("progress")
    // Drawer-only (not a bottom tab) -- also a real pushed NavHost destination (see
    // NavGraph.kt), unlike the tab-tag-only routes above which are never composable()-
    // registered and only ever compared as MainScreen's local selectedRoute string.
    data object Profile : Screen("profile")

    data object RoleSelection : Screen("profile/role-selection")
    data object RoleSelectionSecondary : Screen("profile/role-selection-secondary")
    data object ProfileResumeUpload : Screen("profile/resume-upload")

    data object Main : Screen("main")
    data object RoadmapTaskDetail : Screen("roadmap/task-detail/{taskId}/{jdId}")

    // Prepare Me flow (nested graph pushed from Home). jdId is threaded through the whole
    // flow, all the way to the interview screens -- PROJECT_HANDOFF.md section 1: "If the
    // student pastes a JD, tests, interview questions... must be customized to that JD --
    // generic tests are not the default." A jdId of 0 means "no JD" (role-only flow, e.g.
    // entered via role selection rather than pasting a JD) -- see the *Route() helpers.
    data object RoleOrJdEntry : Screen("prepare-me/entry")
    data object ResumeUpload : Screen("prepare-me/resume-upload/{jdId}")
    data object AnalysisResult : Screen("prepare-me/analysis-result/{jdId}")
    data object PrepareMeSummary : Screen("prepare-me/summary/{jdId}")
    data object TestFlow : Screen("prepare-me/test/{blueprintId}/{jdId}")
    data object TestTaking : Screen("prepare-me/test-taking/{assessmentId}/{jdId}")
    data object TestResult : Screen("prepare-me/test-result/{assessmentId}/{jdId}")
    data object TextInterview : Screen("prepare-me/text-interview/{jdId}/{type}")
    data object VoiceInterview : Screen("prepare-me/voice-interview/{jdId}/{type}")
    data object InterviewEvaluation : Screen("prepare-me/interview-evaluation/{interviewId}/{jdId}")
    data object RoadmapUpdated : Screen("prepare-me/roadmap-updated/{jdId}")

    data object Settings : Screen("settings")
    data object Privacy : Screen("settings/privacy")
    data object Terms : Screen("settings/terms")

    // Placement Accountability Alarm. The notification/lock-screen path bypasses this graph
    // entirely via PlacementAlarmChallengeActivity -- these routes are only the in-app entry
    // points (Home -> list -> create, or list -> "Continue challenge" while the app is open).
    data object AlarmList : Screen("placement-alarm/list")
    data object AlarmCreate : Screen("placement-alarm/create")
    data object AlarmEdit : Screen("placement-alarm/edit/{alarmId}")
    data object AlarmChallenge : Screen("placement-alarm/challenge/{clientAlarmId}/{alarmConfigId}")

    // Personalized Placement Roadmap ("Placement Path" internally -- distinct route prefix
    // from the existing day-plan Roadmap tab/routes above, which this feature deliberately
    // coexists with rather than replaces). Reached only via the Home card, not the bottom nav.
    data object PlacementPath : Screen("placement-path")
    data object PlacementPathTopicDetail : Screen("placement-path/topic-detail/{topicId}")
    data object SavedRoadmaps : Screen("placement-path/saved")

    // Phase 5 drawer-only screens.
    data object NotificationCenter : Screen("notifications")
    data object Subscriptions : Screen("subscriptions")
    data object HelpSupport : Screen("help-support")
    data object AboutUs : Screen("about-us")

    // Job Description Analyzer -- drawer + Home promo card entry point.
    data object JdAnalyzer : Screen("jd-analyzer")
    data object JdAnalyzerResult : Screen("jd-analyzer/result/{jdId}")

    // Drawer's "Mock Interviews" entry point -- picks the interview type before handing off
    // to the existing text/voice interview flow (previously hardcoded to "technical").
    data object MockInterviewCategory : Screen("mock-interview/category")
    data object InterviewHistoryDetail : Screen("mock-interview/history/{interviewId}")

    // Resume ATS Check -- drawer entry point under CAREER. Deliberately separate from the
    // Job Description Analyzer above: no JD is ever involved in this flow.
    data object AtsCheckLanding : Screen("ats-check")
    data object AtsCheckPreCheck : Screen("ats-check/pre-check")
    data object AtsCheckAnalyzing : Screen("ats-check/analyzing/{resumeId}/{targetRole}")
    data object AtsCheckResult : Screen("ats-check/result/{checkId}")
    data object AtsCheckCategoryDetail : Screen("ats-check/result/{checkId}/category/{category}")
    data object AtsCheckSectionAnalysis : Screen("ats-check/result/{checkId}/sections")
    data object AtsCheckIssues : Screen("ats-check/result/{checkId}/issues")
    data object AtsCheckImprovementPlan : Screen("ats-check/result/{checkId}/improve")
    data object AtsCheckHistory : Screen("ats-check/history")
    data object AtsCheckParsedView : Screen("ats-check/result/{checkId}/parsed-view")

    // Career Path -- drawer entry point under CAREER. Deliberately independent of the
    // Roadmap/Placement Path bottom tab and its screens/ViewModels/data: answers "how do I
    // become job-ready for a role I want", not "what should I work on in my current
    // learning plan". Never reads or writes placement_path tables/state.
    data object CareerPathHome : Screen("career-path")
    data object CareerPathRoleSelection : Screen("career-path/role-selection")
    data object CareerPathDetail : Screen("career-path/{pathId}/detail")
    data object CareerPathGapAnalysis : Screen("career-path/{pathId}/gap-analysis")
    data object CareerPathSkillDetail : Screen("career-path/{pathId}/skills/{itemId}")
    data object CareerPathProjects : Screen("career-path/{pathId}/projects")
    data object CareerPathProjectDetail : Screen("career-path/{pathId}/projects/{projectId}")
    data object CareerPathResumeConnection : Screen("career-path/{pathId}/resume-connection")
    data object CareerPathInterviewPrep : Screen("career-path/{pathId}/interview-prep")
    data object CareerPathReadiness : Screen("career-path/{pathId}/readiness")
    data object CareerPathSavedRoles : Screen("career-path/saved")
}

fun resumeUploadRoute(jdId: Int): String = "prepare-me/resume-upload/$jdId"
fun analysisResultRoute(jdId: Int): String = "prepare-me/analysis-result/$jdId"
fun prepareMeSummaryRoute(jdId: Int): String = "prepare-me/summary/$jdId"
fun testFlowRoute(blueprintId: Int, jdId: Int): String = "prepare-me/test/$blueprintId/$jdId"
fun testTakingRoute(assessmentId: Int, jdId: Int): String = "prepare-me/test-taking/$assessmentId/$jdId"
fun testResultRoute(assessmentId: Int, jdId: Int): String = "prepare-me/test-result/$assessmentId/$jdId"
fun textInterviewRoute(jdId: Int, type: String = "technical"): String = "prepare-me/text-interview/$jdId/$type"
fun voiceInterviewRoute(jdId: Int, type: String = "technical"): String = "prepare-me/voice-interview/$jdId/$type"
fun interviewEvaluationRoute(interviewId: Int, jdId: Int): String =
    "prepare-me/interview-evaluation/$interviewId/$jdId"
fun roadmapUpdatedRoute(jdId: Int): String = "prepare-me/roadmap-updated/$jdId"
fun roadmapTaskDetailRoute(taskId: Int, jdId: Int): String = "roadmap/task-detail/$taskId/$jdId"
fun alarmChallengeRoute(clientAlarmId: String, alarmConfigId: Long): String =
    "placement-alarm/challenge/$clientAlarmId/$alarmConfigId"
fun placementPathTopicDetailRoute(topicId: Int): String = "placement-path/topic-detail/$topicId"
fun jdAnalyzerResultRoute(jdId: Int): String = "jd-analyzer/result/$jdId"
fun alarmEditRoute(alarmId: Long): String = "placement-alarm/edit/$alarmId"
fun interviewHistoryDetailRoute(interviewId: Int): String = "mock-interview/history/$interviewId"

// "" is the "no target role" sentinel (matching this codebase's existing jdId=0 convention
// for optional route args) -- Uri.encode/decode because a free-typed role like "AI/ML
// Engineer" would otherwise break path segmentation.
fun atsCheckAnalyzingRoute(resumeId: Int, targetRole: String?): String =
    "ats-check/analyzing/$resumeId/${android.net.Uri.encode(targetRole.orEmpty())}"
fun atsCheckResultRoute(checkId: Int): String = "ats-check/result/$checkId"
fun atsCheckCategoryDetailRoute(checkId: Int, category: String): String = "ats-check/result/$checkId/category/$category"
fun atsCheckSectionAnalysisRoute(checkId: Int): String = "ats-check/result/$checkId/sections"
fun atsCheckIssuesRoute(checkId: Int): String = "ats-check/result/$checkId/issues"
fun atsCheckImprovementPlanRoute(checkId: Int): String = "ats-check/result/$checkId/improve"
fun atsCheckParsedViewRoute(checkId: Int): String = "ats-check/result/$checkId/parsed-view"

fun careerPathDetailRoute(pathId: Int): String = "career-path/$pathId/detail"
fun careerPathGapAnalysisRoute(pathId: Int): String = "career-path/$pathId/gap-analysis"
fun careerPathSkillDetailRoute(pathId: Int, itemId: Int): String = "career-path/$pathId/skills/$itemId"
fun careerPathProjectsRoute(pathId: Int): String = "career-path/$pathId/projects"
fun careerPathProjectDetailRoute(pathId: Int, projectId: Int): String = "career-path/$pathId/projects/$projectId"
fun careerPathResumeConnectionRoute(pathId: Int): String = "career-path/$pathId/resume-connection"
fun careerPathInterviewPrepRoute(pathId: Int): String = "career-path/$pathId/interview-prep"
fun careerPathReadinessRoute(pathId: Int): String = "career-path/$pathId/readiness"
