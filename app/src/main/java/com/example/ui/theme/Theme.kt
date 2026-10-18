package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalAppThemeMode = staticCompositionLocalOf { AppThemeMode.LIGHT }

@Composable
fun CloudNoteTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = if (themeMode.isDark) {
        darkColorScheme(
            primary = themeMode.accent,
            onPrimary = themeMode.accentFg,
            secondary = themeMode.accent,
            onSecondary = themeMode.accentFg,
            background = themeMode.bg,
            onBackground = themeMode.text,
            surface = themeMode.surface,
            onSurface = themeMode.text,
            surfaceVariant = themeMode.bgAlt,
            onSurfaceVariant = themeMode.muted,
            outline = themeMode.border,
            outlineVariant = themeMode.border,
            error = Color(0xFFEF4444),
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = themeMode.accent,
            onPrimary = themeMode.accentFg,
            secondary = themeMode.accent,
            onSecondary = themeMode.accentFg,
            background = themeMode.bg,
            onBackground = themeMode.text,
            surface = themeMode.surface,
            onSurface = themeMode.text,
            surfaceVariant = themeMode.bgAlt,
            onSurfaceVariant = themeMode.muted,
            outline = themeMode.border,
            outlineVariant = themeMode.border,
            error = Color(0xFFDC2626),
            onError = Color.White
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = themeMode.bg.toArgb()
                window.navigationBarColor = themeMode.bg.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !themeMode.isDark
                insetsController.isAppearanceLightNavigationBars = !themeMode.isDark
            }
        }
    }

    CompositionLocalProvider(LocalAppThemeMode provides themeMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
