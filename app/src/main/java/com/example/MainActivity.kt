package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.example.ui.MelTiViApp
import com.example.ui.detectUiMode
import com.example.ui.theme.MelTiViTheme

/**
 * Главная Activity приложения MelTiVi (запускается и на телефоне, и через LeanbackLauncher на Android TV).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val uiMode = remember { detectUiMode(this@MainActivity) }
            MelTiViTheme {
                MelTiViApp(uiMode = uiMode)
            }
        }
    }
}
