package com.zeex

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class ZeexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val app = FirebaseApp.initializeApp(this)
        if (app == null) {
            Log.w("Zeex", "Firebase is not configured yet. Add app/google-services.json to enable cloud features.")
            return
        }
        val provider = if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance() else PlayIntegrityAppCheckProviderFactory.getInstance()
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(provider)
    }
}
