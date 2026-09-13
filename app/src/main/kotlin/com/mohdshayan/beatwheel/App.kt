package com.mohdshayan.beatwheel

import android.app.Application
import com.mohdshayan.beatwheel.di.ServiceLocator
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ServiceLocator.appScope.launch {
            ServiceLocator.profiles.ensureSeeded()
            ServiceLocator.tuner
            ServiceLocator.recorder
        }
    }
}
