package com.aiplacementos.app.network

import android.content.Context
import com.aiplacementos.app.BuildConfig
import com.aiplacementos.app.data.remote.AuthApi
import com.aiplacementos.app.data.remote.UsersApi
import com.aiplacementos.app.data.repository.AuthRepositoryImpl
import com.aiplacementos.app.data.repository.ProfileRepositoryImpl
import com.aiplacementos.app.domain.repository.AuthRepository
import com.aiplacementos.app.domain.repository.ProfileRepository
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
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val authApi: AuthApi = retrofit.create()
    private val usersApi: UsersApi = retrofit.create()

    val authRepository: AuthRepository = AuthRepositoryImpl(authApi, tokenStore)
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(usersApi)
}
