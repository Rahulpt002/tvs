package com.ntorqnav.bridge

import android.app.Application
import com.ntorqnav.bridge.logging.AppLogger
import timber.log.Timber

class BridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        AppLogger.ble("NTORQ Nav Bridge Application Initialized")
    }
}
