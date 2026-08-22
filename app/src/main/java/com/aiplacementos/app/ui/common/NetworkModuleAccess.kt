package com.aiplacementos.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.aiplacementos.app.AiPlacementApp
import com.aiplacementos.app.network.NetworkModule

@Composable
fun networkModule(): NetworkModule {
    val app = LocalContext.current.applicationContext as AiPlacementApp
    return app.networkModule
}
