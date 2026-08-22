package com.aiplacementos.app

import android.app.Application
import com.aiplacementos.app.network.NetworkModule

class AiPlacementApp : Application() {
    lateinit var networkModule: NetworkModule
        private set

    override fun onCreate() {
        super.onCreate()
        networkModule = NetworkModule(this)
    }
}
