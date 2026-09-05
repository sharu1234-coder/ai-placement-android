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
    // coexists with rather than replaces). The screen itself is hosted directly by
    // MainScreen's bottom-tab local state (Screen.Roadmap.route); only its topic-detail
    // drill-in is a real pushed NavGraph destination. A standalone PlacementPath route used to
    // be registered here too, but nothing in the app ever navigated to it -- removed as dead code.
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

    // Career Twin -- a continuously-updated readiness view composed server-side from Evidence
    // Engine + Skill Graph + whichever of Career Path/target role is active. Read-only: never
    // writes to Career Path, Placement Path, or the profile's target role. Single screen for
    // now (no path params) -- per-skill drill-down reuses this same screen's data.
    data object CareerTwin : Screen("career-twin")

    // Skill Passport -- per-skill evidence deep-dive over the same server-side Evidence
    // Engine Career Twin reads. Independent screens/ViewModels; never calls into Career Twin.
    data object SkillPassport : Screen("skill-passport")
    data object SkillPassportDetail : Screen("skill-passport/{skill}")

    // Project Builder -- a guided, staged build of one real project. A completed build
    // automatically feeds Evidence Engine as a new project source and unlocks Project->Proof;
    // never reads/writes Career Path's own project *recommendations* (CareerPathProject).
    data object ProjectBuilderList : Screen("project-builder")
    data object ProjectBuilderCreate : Screen("project-builder/create")
    data object ProjectBuilderDetail : Screen("project-builder/{buildId}/detail")
    data object ProjectBuilderProof : Screen("project-builder/{buildId}/proof")

    // Recruiter Attack -- a skeptical AI recruiter fires a bounded series of follow-up
    // questions at one real, verifiable claim (a completed Project Builder build, or a
    // substring picked from the student's own resume), then grades the defense. Feeds
    // Interview Memory and Evidence Engine on completion, same as Project->Proof; never
    // reads/writes ProjectBuilder's own stage state.
    data object RecruiterAttackList : Screen("recruiter-attack")
    data object RecruiterAttackCreate : Screen("recruiter-attack/create")
    data object RecruiterAttackSession : Screen("recruiter-attack/{sessionId}/session/{claimLabel}")
    data object RecruiterAttackResults : Screen("recruiter-attack/{sessionId}/results")

    // Career Decision Lab -- a stateless "what if" simulation comparing 2-3 candidate roles
    // side by side, reusing Career Twin's own readiness math. Nothing is persisted server-side
    // (see career_decision_service's docstring); this screen owns no other state either.
    data object CareerDecisionLab : Screen("career-decision-lab")

    // Placement Arena -- a timed-mode + personal-best wrapper around the existing Assessment
    // engine. ArenaSession reuses TestTakingScreen unchanged for the actual Q&A (see
    // ArenaSessionScreen), so it needs the real assessmentId alongside the arena attempt id.
    data object ArenaList : Screen("arena")
    data object ArenaSession : Screen("arena/{arenaAttemptId}/{assessmentId}/{timeLimitSeconds}/session")
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
fun projectBuilderDetailRoute(buildId: Int): String = "project-builder/$buildId/detail"
fun projectBuilderProofRoute(buildId: Int): String = "project-builder/$buildId/proof"
// Uri.encode -- a claim label is free text pulled straight from a project title or a resume
// line, which can contain spaces/slashes/parentheses that would otherwise break path
// segmentation, same rationale as atsCheckAnalyzingRoute's targetRole.
fun recruiterAttackSessionRoute(sessionId: Int, claimLabel: String): String =
    "recruiter-attack/$sessionId/session/${android.net.Uri.encode(claimLabel)}"
fun recruiterAttackResultsRoute(sessionId: Int): String = "recruiter-attack/$sessionId/results"
fun arenaSessionRoute(arenaAttemptId: Int, assessmentId: Int, timeLimitSeconds: Int): String =
    "arena/$arenaAttemptId/$assessmentId/$timeLimitSeconds/session"

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

// Uri.encode -- a skill name can contain spaces/parentheses (e.g. "Databases (SQL)") which
// would otherwise break path segmentation, same rationale as atsCheckAnalyzingRoute's targetRole.
fun skillPassportDetailRoute(skill: String): String = "skill-passport/${android.net.Uri.encode(skill)}"
