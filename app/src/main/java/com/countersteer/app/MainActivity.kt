package com.countersteer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.countersteer.ui.navigation.MainNavViewModel
import com.countersteer.ui.navigation.CountersteerApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainNavViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition { viewModel.onboardingComplete.value == null }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CountersteerApp()
        }
    }
}
