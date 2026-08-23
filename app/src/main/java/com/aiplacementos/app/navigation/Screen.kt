package com.aiplacementos.app.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("auth/login")
    data object Signup : Screen("auth/signup")

    data object Home : Screen("home")
    data object Roadmap : Screen("roadmap")
    data object Progress : Screen("progress")
    data object Profile : Screen("profile")

    data object RoleSelection : Screen("profile/role-selection")

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
    data object TextInterview : Screen("prepare-me/text-interview/{jdId}")
    data object VoiceInterview : Screen("prepare-me/voice-interview/{jdId}")
    data object InterviewEvaluation : Screen("prepare-me/interview-evaluation/{interviewId}/{jdId}")
    data object RoadmapUpdated : Screen("prepare-me/roadmap-updated/{jdId}")

    data object Settings : Screen("settings")
    data object Privacy : Screen("settings/privacy")
    data object Terms : Screen("settings/terms")
}

fun resumeUploadRoute(jdId: Int): String = "prepare-me/resume-upload/$jdId"
fun analysisResultRoute(jdId: Int): String = "prepare-me/analysis-result/$jdId"
fun prepareMeSummaryRoute(jdId: Int): String = "prepare-me/summary/$jdId"
fun testFlowRoute(blueprintId: Int, jdId: Int): String = "prepare-me/test/$blueprintId/$jdId"
fun testTakingRoute(assessmentId: Int, jdId: Int): String = "prepare-me/test-taking/$assessmentId/$jdId"
fun testResultRoute(assessmentId: Int, jdId: Int): String = "prepare-me/test-result/$assessmentId/$jdId"
fun textInterviewRoute(jdId: Int): String = "prepare-me/text-interview/$jdId"
fun voiceInterviewRoute(jdId: Int): String = "prepare-me/voice-interview/$jdId"
fun interviewEvaluationRoute(interviewId: Int, jdId: Int): String =
    "prepare-me/interview-evaluation/$interviewId/$jdId"
fun roadmapUpdatedRoute(jdId: Int): String = "prepare-me/roadmap-updated/$jdId"
fun roadmapTaskDetailRoute(taskId: Int, jdId: Int): String = "roadmap/task-detail/$taskId/$jdId"
