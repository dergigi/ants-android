package org.dergigi.ants

import android.app.Application

class AntsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
