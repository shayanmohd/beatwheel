package com.mohdshayan.beatwheel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohdshayan.beatwheel.data.prefs.ThemeChoice
import com.mohdshayan.beatwheel.di.ServiceLocator
import com.mohdshayan.beatwheel.ui.nav.AppNav
import com.mohdshayan.beatwheel.ui.theme.AppTheme
import com.mohdshayan.beatwheel.ui.theme.Beatwheel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by ServiceLocator.appPrefs.theme.collectAsStateWithLifecycle(ThemeChoice.SYSTEM)
            val dark = when (theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            AppTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(Beatwheel.colors.stand)) {
                    AppNav()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // No audio in the background: the drone stops with the app. Listening stops through the screens' lifecycle.
        if (!isChangingConfigurations) ServiceLocator.audioEngine.stopDrone()
    }
}
