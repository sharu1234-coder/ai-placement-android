package com.aiplacementos.app

import android.app.Application
import com.aiplacementos.app.network.NetworkModule
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class AiPlacementApp : Application() {
    lateinit var networkModule: NetworkModule
        private set

    override fun onCreate() {
        super.onCreate()

        Firebase.initialize(this)
        // App Check attests that requests come from a genuine install of this app. Must be
        // installed before anything else touches a Firebase SDK (Firebase's own guidance),
        // so this runs first in onCreate. Play Integrity doesn't work on emulators or
        // unregistered debug installs, so the choice is BuildConfig.DEBUG (build type), not
        // the dev/prod flavor -- a prod-flavor debug build (assembleProdDebug) still needs
        // the Debug provider. A real release build always gets Play Integrity.
        //
        // NOTE: installing the provider here only makes the *app* generate App Check tokens.
        // Nothing currently verifies them -- the FastAPI backend doesn't check App Check
        // tokens on incoming requests (it has no Firebase Admin SDK integration), so this is
        // not yet enforced protection end-to-end. See README's Firebase status entry.
        Firebase.appCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        )

        networkModule = NetworkModule(this)
    }
}
