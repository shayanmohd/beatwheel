package com.mohdshayan.beatwheel.di

import android.content.Context
import com.mohdshayan.beatwheel.audio.AudioEngine
import com.mohdshayan.beatwheel.audio.DroneSynth
import com.mohdshayan.beatwheel.audio.SignalSources
import com.mohdshayan.beatwheel.data.db.AppDatabase
import com.mohdshayan.beatwheel.data.prefs.AppPrefs
import com.mohdshayan.beatwheel.data.repo.BackupRepository
import com.mohdshayan.beatwheel.data.repo.DriftRecorder
import com.mohdshayan.beatwheel.data.repo.ProfileRepository
import com.mohdshayan.beatwheel.data.repo.TunerController
import com.mohdshayan.beatwheel.data.repo.UiMessages
import com.mohdshayan.beatwheel.data.repo.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. No Hilt: a single-module paid app does not need
 * the method count or the build time. Initialised in App.onCreate, and idempotent.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun ctx(): Context =
        appContext ?: error("ServiceLocator.init() must be called before use")

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    val appPrefs: AppPrefs by lazy { AppPrefs(ctx()) }

    val database: AppDatabase by lazy { AppDatabase.get(ctx()) }
    val sessionDao get() = database.sessionDao()

    val messages: UiMessages by lazy { UiMessages() }

    val audioEngine: AudioEngine by lazy {
        val context = ctx()
        AudioEngine(
            sourceFactory = { SignalSources.create(context) },
            droneFactory = { onFocusLost -> DroneSynth(context, onFocusLost) },
            scope = appScope,
        )
    }

    val profiles: ProfileRepository by lazy {
        ProfileRepository(database.profileDao(), database.temperamentDao(), appPrefs)
    }

    val tuner: TunerController by lazy { TunerController(profiles, appPrefs, audioEngine, appScope) }

    val usage: UsageRepository by lazy { UsageRepository(appPrefs) }

    val recorder: DriftRecorder by lazy {
        DriftRecorder(audioEngine, tuner, sessionDao, appPrefs, usage, messages, appScope)
    }

    val backup: BackupRepository by lazy { BackupRepository(ctx(), database, appPrefs) }
}
