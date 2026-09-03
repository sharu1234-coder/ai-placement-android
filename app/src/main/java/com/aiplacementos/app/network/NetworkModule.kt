package com.aiplacementos.app.network

import android.content.Context
import com.aiplacementos.app.BuildConfig
import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.data.remote.ArenaApi
import com.aiplacementos.app.data.remote.AssessmentsApi
import com.aiplacementos.app.data.remote.AtsCheckApi
import com.aiplacementos.app.data.remote.AuthApi
import com.aiplacementos.app.data.remote.CareerDecisionApi
import com.aiplacementos.app.data.remote.CareerPathApi
import com.aiplacementos.app.data.remote.CareerTwinApi
import com.aiplacementos.app.data.remote.InterviewsApi
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
import com.aiplacementos.app.data.remote.WeaknessesApi
import com.aiplacementos.app.data.repository.ArenaRepositoryImpl
import com.aiplacementos.app.data.repository.AssessmentRepositoryImpl
import com.aiplacementos.app.data.repository.AtsCheckRepositoryImpl
import com.aiplacementos.app.data.repository.AuthRepositoryImpl
import com.aiplacementos.app.data.repository.CareerDecisionRepositoryImpl
import com.aiplacementos.app.data.repository.CareerPathRepositoryImpl
import com.aiplacementos.app.data.repository.CareerTwinRepositoryImpl
import com.aiplacementos.app.data.repository.InterviewRepositoryImpl
import com.aiplacementos.app.data.repository.JobRepositoryImpl
import com.aiplacementos.app.data.repository.LegalRepositoryImpl
import com.aiplacementos.app.data.repository.MissionRepositoryImpl
import com.aiplacementos.app.data.repository.NextBestActionRepositoryImpl
import com.aiplacementos.app.data.repository.NotificationRepositoryImpl
import com.aiplacementos.app.data.repository.NotificationSyncRepositoryImpl
import com.aiplacementos.app.data.repository.PlacementAlarmRepositoryImpl
import com.aiplacementos.app.data.repository.PlacementPathRepositoryImpl
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
import com.aiplacementos.app.domain.repository.ArenaRepository
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import com.aiplacementos.app.domain.repository.AuthRepository
import com.aiplacementos.app.domain.repository.CareerDecisionRepository
import com.aiplacementos.app.domain.repository.CareerPathRepository
import com.aiplacementos.app.domain.repository.CareerTwinRepository
import com.aiplacementos.app.domain.repository.InterviewRepository
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.domain.repository.LegalRepository
import com.aiplacementos.app.domain.repository.MissionRepository
import com.aiplacementos.app.domain.repository.NextBestActionRepository
import com.aiplacementos.app.domain.repository.NotificationRepository
import com.aiplacementos.app.domain.repository.NotificationSyncRepository
import com.aiplacementos.app.domain.repository.PlacementAlarmRepository
import com.aiplacementos.app.domain.repository.PlacementPathRepository
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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Manual composition root (no Hilt yet -- avoided until there's a concrete need for it).
 * Constructed once in AiPlacementApp.onCreate() and reached via LocalContext in Compose.
 */
class NetworkModule(context: Context) {
    val tokenStore = TokenStore(context.applicationContext)
    val alarmScheduler = AlarmScheduler(context.applicationContext)

    private val json = Json { ignoreUnknownKeys = true }

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
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val authApi: AuthApi = retrofit.create()
    private val usersApi: UsersApi = retrofit.create()
    private val resumesApi: ResumesApi = retrofit.create()
    private val atsCheckApi: AtsCheckApi = retrofit.create()
    private val jobsApi: JobsApi = retrofit.create()
    private val assessmentsApi: AssessmentsApi = retrofit.create()
    private val interviewsApi: InterviewsApi = retrofit.create()
    private val roadmapApi: RoadmapApi = retrofit.create()
    private val progressApi: ProgressApi = retrofit.create()
    private val projectBuilderApi: ProjectBuilderApi = retrofit.create()
    private val legalApi: LegalApi = retrofit.create()
    private val nextBestActionApi: NextBestActionApi = retrofit.create()
    private val missionApi: MissionApi = retrofit.create()
    private val notificationsApi: NotificationsApi = retrofit.create()
    private val placementAlarmApi: PlacementAlarmApi = retrofit.create()
    private val placementPathApi: PlacementPathApi = retrofit.create()
    private val careerPathApi: CareerPathApi = retrofit.create()
    private val careerTwinApi: CareerTwinApi = retrofit.create()
    private val skillPassportApi: SkillPassportApi = retrofit.create()
    private val subscriptionsApi: SubscriptionsApi = retrofit.create()
    private val weaknessesApi: WeaknessesApi = retrofit.create()
    private val recruiterAttackApi: RecruiterAttackApi = retrofit.create()
    private val careerDecisionApi: CareerDecisionApi = retrofit.create()
    private val arenaApi: ArenaApi = retrofit.create()

    private val alarmDatabase = AlarmDatabase.getInstance(context)

    val authRepository: AuthRepository = AuthRepositoryImpl(authApi, tokenStore)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(usersApi)
    val resumeRepository: ResumeRepository = ResumeRepositoryImpl(resumesApi)
    val atsCheckRepository: AtsCheckRepository = AtsCheckRepositoryImpl(atsCheckApi)
    val jobRepository: JobRepository = JobRepositoryImpl(jobsApi)
    val assessmentRepository: AssessmentRepository = AssessmentRepositoryImpl(assessmentsApi)
    val interviewRepository: InterviewRepository = InterviewRepositoryImpl(interviewsApi)
    val roadmapRepository: RoadmapRepository = RoadmapRepositoryImpl(roadmapApi)
    val progressRepository: ProgressRepository = ProgressRepositoryImpl(progressApi)
    val projectBuilderRepository: ProjectBuilderRepository = ProjectBuilderRepositoryImpl(projectBuilderApi)
    val legalRepository: LegalRepository = LegalRepositoryImpl(legalApi)
    val nextBestActionRepository: NextBestActionRepository = NextBestActionRepositoryImpl(nextBestActionApi)
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
    val skillPassportRepository: SkillPassportRepository = SkillPassportRepositoryImpl(skillPassportApi)
    val notificationRepository: NotificationRepository = NotificationRepositoryImpl(alarmDatabase.notificationDao())
    val subscriptionRepository: SubscriptionRepository = SubscriptionRepositoryImpl(subscriptionsApi)
    val weaknessRepository: WeaknessRepository = WeaknessRepositoryImpl(weaknessesApi)
    val recruiterAttackRepository: RecruiterAttackRepository = RecruiterAttackRepositoryImpl(recruiterAttackApi)
    val careerDecisionRepository: CareerDecisionRepository = CareerDecisionRepositoryImpl(careerDecisionApi)
    val arenaRepository: ArenaRepository = ArenaRepositoryImpl(arenaApi)
}
