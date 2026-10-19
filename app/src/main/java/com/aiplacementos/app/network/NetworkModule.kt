package com.aiplacementos.app.network

import com.aiplacementos.app.domain.repository.ConversationRepository
import com.aiplacementos.app.data.repository.ConversationRepositoryImpl
import com.aiplacementos.app.data.remote.ConversationsApi
import android.content.Context
import com.aiplacementos.app.BuildConfig
import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.data.local.LocalAccountDataCleaner
import com.aiplacementos.app.data.local.PendingOperationStore
import com.aiplacementos.app.data.remote.ActionsApi
import com.aiplacementos.app.data.remote.ArenaApi
import com.aiplacementos.app.data.remote.AssessmentsApi
import com.aiplacementos.app.data.remote.AtsCheckApi
import com.aiplacementos.app.data.remote.AuthApi
import com.aiplacementos.app.data.remote.CareerDecisionApi
import com.aiplacementos.app.data.remote.CareerGoalsApi
import com.aiplacementos.app.data.remote.CareerPathApi
import com.aiplacementos.app.data.remote.CareerStrategyApi
import com.aiplacementos.app.data.remote.CareerTwinApi
import com.aiplacementos.app.data.remote.InterviewsApi
import com.aiplacementos.app.data.remote.JobHuntApi
import com.aiplacementos.app.data.remote.JobsApi
import com.aiplacementos.app.data.remote.LegalApi
import com.aiplacementos.app.data.remote.MissionApi
import com.aiplacementos.app.data.remote.NextBestActionApi
import com.aiplacementos.app.data.remote.NotificationsApi
import com.aiplacementos.app.data.remote.PlacementAlarmApi
import com.aiplacementos.app.data.remote.PlacementPathApi
import com.aiplacementos.app.data.remote.ProgressApi
import com.aiplacementos.app.data.remote.ProjectBuilderApi
import com.aiplacementos.app.data.remote.RecruiterAttackApi
import com.aiplacementos.app.data.remote.ResumesApi
import com.aiplacementos.app.data.remote.RoadmapApi
import com.aiplacementos.app.data.remote.SkillPassportApi
import com.aiplacementos.app.data.remote.SubscriptionsApi
import com.aiplacementos.app.data.remote.UsersApi
import com.aiplacementos.app.data.remote.ProfessionalPresenceApi
import com.aiplacementos.app.data.remote.WeaknessesApi
import com.aiplacementos.app.data.remote.addApiConverters
import com.aiplacementos.app.data.remote.clientNotResendingGenerations
import com.aiplacementos.app.data.repository.ActionRepositoryImpl
import com.aiplacementos.app.data.repository.ArenaRepositoryImpl
import com.aiplacementos.app.data.repository.AssessmentRepositoryImpl
import com.aiplacementos.app.data.repository.AtsCheckRepositoryImpl
import com.aiplacementos.app.data.repository.AuthRepositoryImpl
import com.aiplacementos.app.data.repository.CareerDecisionRepositoryImpl
import com.aiplacementos.app.data.repository.CareerGoalRepositoryImpl
import com.aiplacementos.app.data.repository.CareerPathRepositoryImpl
import com.aiplacementos.app.data.repository.CareerStrategyRepositoryImpl
import com.aiplacementos.app.data.repository.CareerTwinRepositoryImpl
import com.aiplacementos.app.data.repository.InterviewRepositoryImpl
import com.aiplacementos.app.data.repository.JobHuntRepositoryImpl
import com.aiplacementos.app.data.repository.JobRepositoryImpl
import com.aiplacementos.app.data.repository.LegalRepositoryImpl
import com.aiplacementos.app.data.repository.MissionRepositoryImpl
import com.aiplacementos.app.data.repository.NextBestActionRepositoryImpl
import com.aiplacementos.app.data.repository.NotificationRepositoryImpl
import com.aiplacementos.app.data.repository.NotificationSyncRepositoryImpl
import com.aiplacementos.app.data.repository.PlacementAlarmRepositoryImpl
import com.aiplacementos.app.data.repository.PlacementPathRepositoryImpl
import com.aiplacementos.app.data.repository.ProfessionalPresenceRepositoryImpl
import com.aiplacementos.app.data.repository.ProfileRepositoryImpl
import com.aiplacementos.app.data.repository.ProgressRepositoryImpl
import com.aiplacementos.app.data.repository.ProjectBuilderRepositoryImpl
import com.aiplacementos.app.data.repository.RecruiterAttackRepositoryImpl
import com.aiplacementos.app.data.repository.ResumeRepositoryImpl
import com.aiplacementos.app.data.repository.RoadmapRepositoryImpl
import com.aiplacementos.app.data.repository.SkillPassportRepositoryImpl
import com.aiplacementos.app.data.repository.SubscriptionRepositoryImpl
import com.aiplacementos.app.data.repository.WeaknessRepositoryImpl
import com.aiplacementos.app.database.AlarmDatabase
import com.aiplacementos.app.domain.repository.ActionRepository
import com.aiplacementos.app.voice.AndroidVoiceDiagnostics
import com.aiplacementos.app.voice.VoiceInterviewManager
import com.aiplacementos.app.voice.core.AndroidAudioFocusController
import com.aiplacementos.app.voice.core.AudioFocusController
import com.aiplacementos.app.domain.repository.ArenaRepository
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import com.aiplacementos.app.domain.repository.AuthRepository
import com.aiplacementos.app.domain.repository.CareerDecisionRepository
import com.aiplacementos.app.domain.repository.CareerGoalRepository
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.CareerStrategyRepository
import com.aiplacementos.app.domain.repository.CareerTwinRepository
import com.aiplacementos.app.domain.repository.InterviewRepository
import com.aiplacementos.app.domain.repository.JobHuntRepository
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.domain.repository.LegalRepository
import com.aiplacementos.app.domain.repository.MissionRepository
import com.aiplacementos.app.domain.repository.NextBestActionRepository
import com.aiplacementos.app.domain.repository.NotificationRepository
import com.aiplacementos.app.domain.repository.NotificationSyncRepository
import com.aiplacementos.app.domain.repository.PlacementAlarmRepository
import com.aiplacementos.app.domain.repository.PlacementPathRepository
import com.aiplacementos.app.domain.repository.ProfessionalPresenceRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.domain.repository.ProgressRepository
import com.aiplacementos.app.domain.repository.ProjectBuilderRepository
import com.aiplacementos.app.domain.repository.RecruiterAttackRepository
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.domain.repository.RoadmapRepository
import com.aiplacementos.app.domain.repository.SkillPassportRepository
import com.aiplacementos.app.domain.repository.SubscriptionRepository
import com.aiplacementos.app.domain.repository.WeaknessRepository
import java.util.concurrent.TimeUnit
import com.aiplacementos.app.voice.core.AndroidAudioOutputRouter
import com.aiplacementos.app.voice.core.AndroidAudioRouteMonitor
import com.aiplacementos.app.voice.core.AndroidMicrophonePermission
import com.aiplacementos.app.voice.core.AudioRecordPcmCapture
import com.aiplacementos.app.voice.core.AudioTrackPcmPlayback
import com.aiplacementos.app.voice.core.VoiceAudioConfig
import com.aiplacementos.app.voice.core.VoiceSession
import com.aiplacementos.app.voice.realtime.InterviewContext
import com.aiplacementos.app.voice.realtime.RealtimeVoiceSession
import com.aiplacementos.app.voice.realtime.VoiceAgentMode
import com.aiplacementos.app.voice.realtime.WebSocketRealtimeTransport
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create

/**
 * Manual composition root (no Hilt yet -- avoided until there's a concrete need for it).
 * Constructed once in AiPlacementApp.onCreate() and reached via LocalContext in Compose.
 */
class NetworkModule(context: Context) {
    /**
     * Held because `context` is a constructor parameter, not a property: it is in scope for the
     * property initialisers below but not inside a function, and the realtime session factory is
     * a function. The application context specifically -- an Activity kept here would outlive
     * the screen that supplied it, which is the leak D2 was about.
     */
    private val appContext: Context = context.applicationContext

    val tokenStore = TokenStore(context.applicationContext)
    val alarmScheduler = AlarmScheduler(context.applicationContext)
    val pendingOperationStore = PendingOperationStore(context.applicationContext)

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        // Level.BODY logs headers too -- without this, the bearer token lands in Logcat on
        // every authenticated request in debug builds, readable by anything with logcat
        // access on the device.
        redactHeader("Authorization")
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(ObservabilityInterceptor())
        .addInterceptor(AppCheckInterceptor())
        .addInterceptor(AuthInterceptor(tokenStore))
        .addInterceptor(loggingInterceptor)
        .authenticator(TokenAuthenticator(tokenStore))
        .connectTimeout(15, TimeUnit.SECONDS)
        // Resume/JD parsing and placement-path generation run real AI inference server-side --
        // measured at ~163s for a full placement-path generate() call against a local
        // "thinking" model during this session's debugging, so 150s was too tight (see the
        // Phase 3 plan). 300s comfortably covers that with margin.
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        // okHttpClient for everything; see NonResentRequests.kt for the one request it never re-sends.
        .clientNotResendingGenerations(okHttpClient)
        // The shared chain from data/remote/ApiJson.kt -- see that file for why it lives outside
        // this Context-bound class (so wire-contract tests decode with the real thing, not a copy).
        .addApiConverters()
        .build()

    private val authApi: AuthApi = retrofit.create()
    private val usersApi: UsersApi = retrofit.create()
    private val resumesApi: ResumesApi = retrofit.create()
    private val atsCheckApi: AtsCheckApi = retrofit.create()
    private val jobsApi: JobsApi = retrofit.create()
    private val jobHuntApi: JobHuntApi = retrofit.create()
    private val assessmentsApi: AssessmentsApi = retrofit.create()
    private val interviewsApi: InterviewsApi = retrofit.create()
    private val conversationsApi: ConversationsApi = retrofit.create()
    private val roadmapApi: RoadmapApi = retrofit.create()
    private val progressApi: ProgressApi = retrofit.create()
    private val projectBuilderApi: ProjectBuilderApi = retrofit.create()
    private val legalApi: LegalApi = retrofit.create()
    private val nextBestActionApi: NextBestActionApi = retrofit.create()
    private val actionsApi: ActionsApi = retrofit.create()
    private val missionApi: MissionApi = retrofit.create()
    private val notificationsApi: NotificationsApi = retrofit.create()
    private val placementAlarmApi: PlacementAlarmApi = retrofit.create()
    private val placementPathApi: PlacementPathApi = retrofit.create()
    private val careerPathApi: CareerPathApi = retrofit.create()
    private val careerTwinApi: CareerTwinApi = retrofit.create()
    private val careerGoalsApi: CareerGoalsApi = retrofit.create()
    private val careerStrategyApi: CareerStrategyApi = retrofit.create()
    private val skillPassportApi: SkillPassportApi = retrofit.create()
    private val subscriptionsApi: SubscriptionsApi = retrofit.create()
    private val weaknessesApi: WeaknessesApi = retrofit.create()
    private val professionalPresenceApi: ProfessionalPresenceApi = retrofit.create()
    private val recruiterAttackApi: RecruiterAttackApi = retrofit.create()
    private val careerDecisionApi: CareerDecisionApi = retrofit.create()
    private val arenaApi: ArenaApi = retrofit.create()

    private val alarmDatabase = AlarmDatabase.getInstance(context)

    /** Shared by logout and account deletion so both empty the same tables -- see the class
     * doc for why local data is purged rather than scoped by user id. */
    val localAccountDataCleaner = LocalAccountDataCleaner(alarmDatabase, alarmScheduler, pendingOperationStore)

    val authRepository: AuthRepository =
        AuthRepositoryImpl(authApi, tokenStore, localAccountDataCleaner)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(usersApi)
    val resumeRepository: ResumeRepository = ResumeRepositoryImpl(resumesApi)
    val atsCheckRepository: AtsCheckRepository = AtsCheckRepositoryImpl(atsCheckApi)
    val jobRepository: JobRepository = JobRepositoryImpl(jobsApi)
    val jobHuntRepository: JobHuntRepository = JobHuntRepositoryImpl(jobHuntApi)
    val assessmentRepository: AssessmentRepository = AssessmentRepositoryImpl(assessmentsApi)
    val interviewRepository: InterviewRepository = InterviewRepositoryImpl(interviewsApi)
    val roadmapRepository: RoadmapRepository = RoadmapRepositoryImpl(roadmapApi)
    val progressRepository: ProgressRepository = ProgressRepositoryImpl(progressApi)
    val projectBuilderRepository: ProjectBuilderRepository = ProjectBuilderRepositoryImpl(projectBuilderApi)
    val legalRepository: LegalRepository = LegalRepositoryImpl(legalApi)
    val nextBestActionRepository: NextBestActionRepository = NextBestActionRepositoryImpl(nextBestActionApi)

    /** VA-7's confirmed-action path. Preview and execute are separate calls so the student
     * sees what is about to happen before anything is written. */
    val actionRepository: ActionRepository = ActionRepositoryImpl(actionsApi)

    /** D1/D2: the voice-interview session, application-scoped.
     *
     * It was previously created by `remember { }` inside `VoiceInterviewScreen`, so an activity
     * recreation dropped the object holding a live `MediaRecorder` -- the microphone stayed
     * open and the file was never released. Built here with the application context, it holds
     * no screen and no `Activity`, so the ViewModel can own the session without leaking one.
     * Audio focus comes from the Voice Core's existing controller rather than a second
     * implementation of the same thing. */
    val voiceAudioFocus: AudioFocusController = AndroidAudioFocusController(context.applicationContext)
    val voiceInterviewManager = VoiceInterviewManager(
        context.applicationContext,
        interviewRepository,
        voiceAudioFocus,
        AndroidVoiceDiagnostics(),
    )
    /**
     * The realtime voice agent, built per session rather than held as a singleton.
     *
     * **Why a factory and not a field.** A `RealtimeVoiceSession` owns a microphone, an
     * `AudioTrack`, a socket and a `CoroutineScope`; it is alive only while a conversation is.
     * One application-scoped instance would hold the mic for the life of the process and would
     * have to be reset between conversations, which is the shape of bug D2 was about. The
     * factory is application-scoped; what it builds is owned by the ViewModel that asked for it
     * and dies with that ViewModel's scope.
     *
     * **Why the transport is built here too.** It needs the shared `OkHttpClient` (so it inherits
     * the auth interceptor's configuration) and the API base URL, neither of which belongs in a
     * ViewModel. The access token is read from `TokenStore` at connect time through a suspending
     * lambda, not captured: a session that reconnects after a refresh must use the new token.
     *
     * The permission check, audio focus, capture, playback and route monitoring all come from
     * the Voice Core's existing adapters -- no second implementation of any of them.
     */
    fun createRealtimeVoiceSession(
        scope: CoroutineScope,
        mode: VoiceAgentMode,
        interview: InterviewContext? = null,
        audioConfig: VoiceAudioConfig = VoiceAudioConfig(),
    ): RealtimeVoiceSession {
        val audio = VoiceSession(
            config = audioConfig,
            permission = AndroidMicrophonePermission(appContext),
            focus = voiceAudioFocus,
            capture = AudioRecordPcmCapture(),
            playback = AudioTrackPcmPlayback(),
            routes = AndroidAudioRouteMonitor(appContext),
            // Without this the session gets AudioOutputRouter.None and the assistant's voice
            // goes wherever the platform puts communication audio by default -- the earpiece,
            // on the voice-call stream. That was the reported "too quiet / wrong speaker"
            // defect; this is the line that fixes it.
            output = AndroidAudioOutputRouter(appContext),
        )
        val transport = WebSocketRealtimeTransport(
            client = okHttpClient,
            url = realtimeVoiceUrl,
            accessToken = { tokenStore.currentAccessToken() },
        )
        return RealtimeVoiceSession(
            audio = audio,
            transport = transport,
            config = audioConfig,
            scope = scope,
            mode = mode,
            interview = interview,
        )
    }

    /**
     * `/voice/session` as a WebSocket URL.
     *
     * Derived from the one configured base URL rather than declared separately, so a build that
     * points at a different environment cannot leave the voice socket talking to the old one.
     * The scheme stays `http`/`https` and is deliberately **not** rewritten to `ws`/`wss`.
     * OkHttp upgrades a WebSocket over an HTTP(S) URL, and [WebSocketRealtimeTransport]
     * parses this string with `toHttpUrl()`, which throws `IllegalArgumentException` on any
     * other scheme. A `ws://` URL here threw inside `connect()` -- before a socket was ever
     * opened -- and terminated the process when a student tapped Voice Career Coach.
     */
    private val realtimeVoiceUrl: String =
        BuildConfig.API_BASE_URL.trimEnd('/') + "/voice/session"

    val conversationRepository: ConversationRepository =
        ConversationRepositoryImpl(conversationsApi)
    val missionRepository: MissionRepository = MissionRepositoryImpl(missionApi)
    val notificationSyncRepository: NotificationSyncRepository = NotificationSyncRepositoryImpl(notificationsApi)
    val placementAlarmRepository: PlacementAlarmRepository = PlacementAlarmRepositoryImpl(
        alarmDatabase.alarmConfigDao(),
        alarmDatabase.challengeDao(),
        placementAlarmApi,
    )
    val placementPathRepository: PlacementPathRepository = PlacementPathRepositoryImpl(placementPathApi)
    val careerPathRepository: CareerPathRepository = CareerPathRepositoryImpl(careerPathApi)
    val careerTwinRepository: CareerTwinRepository = CareerTwinRepositoryImpl(careerTwinApi)
    val careerGoalRepository: CareerGoalRepository = CareerGoalRepositoryImpl(careerGoalsApi)
    val careerStrategyRepository: CareerStrategyRepository = CareerStrategyRepositoryImpl(careerStrategyApi)
    val skillPassportRepository: SkillPassportRepository = SkillPassportRepositoryImpl(skillPassportApi)
    val notificationRepository: NotificationRepository = NotificationRepositoryImpl(alarmDatabase.notificationDao())
    val subscriptionRepository: SubscriptionRepository = SubscriptionRepositoryImpl(subscriptionsApi)
    val weaknessRepository: WeaknessRepository = WeaknessRepositoryImpl(weaknessesApi)
    val professionalPresenceRepository: ProfessionalPresenceRepository =
        ProfessionalPresenceRepositoryImpl(professionalPresenceApi)
    val recruiterAttackRepository: RecruiterAttackRepository = RecruiterAttackRepositoryImpl(recruiterAttackApi)
    val careerDecisionRepository: CareerDecisionRepository = CareerDecisionRepositoryImpl(careerDecisionApi)
    val arenaRepository: ArenaRepository = ArenaRepositoryImpl(arenaApi)
}
