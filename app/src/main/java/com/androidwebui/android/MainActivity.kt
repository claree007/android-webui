package com.androidwebui.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import com.androidwebui.android.core.database.LocalProfile
import com.androidwebui.android.ui.AppNavigation
import com.androidwebui.android.ui.AppTheme
import com.androidwebui.android.ui.isDarkFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AndroidWebuiApp).container
        setContent {
            val mode by container.devicePrefs.themeMode.collectAsState()
            val dark = isDarkFor(mode)
            var profile by remember { mutableStateOf<LocalProfile?>(null) }

            LaunchedEffect(Unit) { profile = withContext(Dispatchers.IO) { container.loadProfile() } }
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            AppTheme(dark) { AppNavigation(container.devicePrefs, profile) }
        }
    }
}
