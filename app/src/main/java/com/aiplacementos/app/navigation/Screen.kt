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
    data object RoadmapTaskDetail : Screen("roadmap/task-detail")

    // Prepare Me flow (nested graph pushed from Home). ResumeUpload/AnalysisResult carry the
    // jdId created by JDPasteScreen through the flow -- see resumeUploadRoute/analysisResultRoute.
    data object RoleOrJdEntry : Screen("prepare-me/entry")
    data object ResumeUpload : Screen("prepare-me/resume-upload/{jdId}")
    data object AnalysisResult : Screen("prepare-me/analysis-result/{jdId}")
    data object PrepareMeSummary : Screen("prepare-me/summary")
    data object TestFlow : Screen("prepare-me/test")
    data object TextInterview : Screen("prepare-me/text-interview")
    data object VoiceInterview : Screen("prepare-me/voice-interview")
    data object InterviewEvaluation : Screen("prepare-me/interview-evaluation")
    data object RoadmapUpdated : Screen("prepare-me/roadmap-updated")

    data object Settings : Screen("settings")
    data object Privacy : Screen("settings/privacy")
}

fun resumeUploadRoute(jdId: Int): String = "prepare-me/resume-upload/$jdId"
fun analysisResultRoute(jdId: Int): String = "prepare-me/analysis-result/$jdId"
