package com.example.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Режим пользовательского интерфейса в зависимости от типа устройства:
 * [TvUi] — интерфейс для Android TV / ТВ-приставок с управлением с пульта D-pad (Leanback-стиль).
 * [PhoneUi] — тач-интерфейс для смартфонов и планшетов с нижней навигацией.
 */
enum class AppUiMode {
    TvUi,
    PhoneUi
}

/**
 * Определяет режим интерфейса при запуске с помощью системного UiModeManager.
 */
fun detectUiMode(context: Context): AppUiMode {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    return if (isTelevision) AppUiMode.TvUi else AppUiMode.PhoneUi
}

val LocalAppUiMode = staticCompositionLocalOf { AppUiMode.PhoneUi }
