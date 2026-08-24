package com.aiplacementos.app.network

import android.content.Context
import com.aiplacementos.app.BuildConfig
import com.aiplacementos.app.data.remote.AssessmentsApi
import com.aiplacementos.app.data.remote.AuthApi
import com.aiplacementos.app.data.remote.InterviewsApi
import com.aiplacementos.app.data.remote.JobsApi
import com.aiplacementos.app.data.remote.LegalApi
import com.aiplacementos.app.data.remote.ProgressApi
import com.aiplacementos.app.data.remote.ResumesApi
import com.aiplacementos.app.data.remote.RoadmapApi
import com.aiplacementos.app.data.remote.UsersApi
import com.aiplacementos.app.data.repository.AssessmentRepositoryImpl
import com.aiplacementos.app.data.repository.AuthRepositoryImpl
import com.aiplacementos.app.data.repository.InterviewRepositoryImpl
import com.aiplacementos.app.data.repository.JobRepositoryImpl
import com.aiplacementos.app.data.repository.LegalRepositoryImpl
import com.aiplacementos.app.data.repository.ProfileRepositoryImpl
import com.aiplacementos.app.data.repository.ProgressRepositoryImpl
import com.aiplacementos.app.data.repository.ResumeRepositoryImpl
import com.aiplacementos.app.data.repository.RoadmapRepositoryImpl
import com.aiplacementos.app.domain.repository.AssessmentRepository
import com.aiplacementos.app.domain.repository.AuthRepository
import com.aiplacementos.app.domain.repository.InterviewRepository
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.domain.repository.LegalRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
import com.aiplacementos.app.domain.repository.ProgressRepository
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.domain.repository.RoadmapRepository
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
        .addInterceptor(AuthInterceptor(tokenStore))
        .addInterceptor(loggingInterceptor)
        .authenticator(TokenAuthenticator(tokenStore))
        .connectTimeout(15, TimeUnit.SECONDS)
        // Resume/JD parsing calls run real AI inference server-side and can take ~1-2 minutes.
        .readTimeout(150, TimeUnit.SECONDS)
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
    private val jobsApi: JobsApi = retrofit.create()
    private val assessmentsApi: AssessmentsApi = retrofit.create()
    private val interviewsApi: InterviewsApi = retrofit.create()
    private val roadmapApi: RoadmapApi = retrofit.create()
    private val progressApi: ProgressApi = retrofit.create()
    private val legalApi: LegalApi = retrofit.create()

    val authRepository: AuthRepository = AuthRepositoryImpl(authApi, tokenStore)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(usersApi)
    val resumeRepository: ResumeRepository = ResumeRepositoryImpl(resumesApi)
    val jobRepository: JobRepository = JobRepositoryImpl(jobsApi)
    val assessmentRepository: AssessmentRepository = AssessmentRepositoryImpl(assessmentsApi)
    val interviewRepository: InterviewRepository = InterviewRepositoryImpl(interviewsApi)
    val roadmapRepository: RoadmapRepository = RoadmapRepositoryImpl(roadmapApi)
    val progressRepository: ProgressRepository = ProgressRepositoryImpl(progressApi)
    val legalRepository: LegalRepository = LegalRepositoryImpl(legalApi)
}
