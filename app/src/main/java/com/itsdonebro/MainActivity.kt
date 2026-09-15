package com.itsdonebro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.itsdonebro.data.preferences.SettingsDataStore
import com.itsdonebro.service.TrackingForegroundService
import com.itsdonebro.ui.navigation.AppNavigation
import com.itsdonebro.ui.theme.ItsDoneBroTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settings: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the foreground tracking service
        startForegroundTrackingService()

        lifecycleScope.launch {
            val onboardingComplete = settings.onboardingComplete.first()

            setContent {
                ItsDoneBroTheme {
                    AppNavigation(showOnboarding = !onboardingComplete)
                }
            }
        }
    }

    private fun startForegroundTrackingService() {
        val intent = Intent(this, TrackingForegroundService::class.java)
        startForegroundService(intent)
    }
}
