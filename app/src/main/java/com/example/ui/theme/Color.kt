package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppThemeMode(
    val id: String,
    val labelBn: String,
    val labelEn: String,
    val bg: Color,
    val bgAlt: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val accentFg: Color,
    val isDark: Boolean
) {
    LIGHT(
        id = "light",
        labelBn = "লাইট",
        labelEn = "Light",
        bg = Color(0xFFFFFFFF),
        bgAlt = Color(0xFFF4F4F5),
        surface = Color(0xFFFAFAFA),
        text = Color(0xFF09090B),
        muted = Color(0xFF71717A),
        border = Color(0xFFE4E4E7),
        accent = Color(0xFF09090B),
        accentFg = Color(0xFFFFFFFF),
        isDark = false
    ),
    DARK(
        id = "dark",
        labelBn = "ডার্ক",
        labelEn = "Dark",
        bg = Color(0xFF09090B),
        bgAlt = Color(0xFF18181B),
        surface = Color(0xFF121215),
        text = Color(0xFFFAFAFA),
        muted = Color(0xFFA1A1AA),
        border = Color(0xFF27272A),
        accent = Color(0xFFFAFAFA),
        accentFg = Color(0xFF09090B),
        isDark = true
    ),
    EYE_CARE(
        id = "eye-care",
        labelBn = "আই রিল্যাক্সিং",
        labelEn = "Eye Relaxing",
        bg = Color(0xFFF6F3E8),
        bgAlt = Color(0xFFEDE8D5),
        surface = Color(0xFFFAF8F1),
        text = Color(0xFF2C261E),
        muted = Color(0xFF786E5F),
        border = Color(0xFFDDD5C0),
        accent = Color(0xFF2C261E),
        accentFg = Color(0xFFFAF8F1),
        isDark = false
    ),
    GREEN(
        id = "green",
        labelBn = "গ্রিন",
        labelEn = "Green",
        bg = Color(0xFFEEF5F0),
        bgAlt = Color(0xFFE2EDE5),
        surface = Color(0xFFF6FAF7),
        text = Color(0xFF102819),
        muted = Color(0xFF4D6B57),
        border = Color(0xFFCDE0D2),
        accent = Color(0xFF143621),
        accentFg = Color(0xFFF6FAF7),
        isDark = false
    ),
    SEPIA(
        id = "sepia",
        labelBn = "সেপিয়া",
        labelEn = "Sepia",
        bg = Color(0xFFF4ECD8),
        bgAlt = Color(0xFFEAE0C8),
        surface = Color(0xFFF9F3E5),
        text = Color(0xFF3B2D1F),
        muted = Color(0xFF7D6954),
        border = Color(0xFFD9CBB3),
        accent = Color(0xFF3B2D1F),
        accentFg = Color(0xFFF9F3E5),
        isDark = false
    ),
    MIDNIGHT(
        id = "midnight",
        labelBn = "মিডনাইট",
        labelEn = "Midnight",
        bg = Color(0xFF0B1120),
        bgAlt = Color(0xFF1E293B),
        surface = Color(0xFF0F172A),
        text = Color(0xFFE2E8F0),
        muted = Color(0xFF94A3B8),
        border = Color(0xFF1E293B),
        accent = Color(0xFFE2E8F0),
        accentFg = Color(0xFF0B1120),
        isDark = true
    ),
    AMOLED(
        id = "amoled",
        labelBn = "অ্যামোলেড ব্ল্যাক",
        labelEn = "AMOLED Black",
        bg = Color(0xFF000000),
        bgAlt = Color(0xFF121212),
        surface = Color(0xFF080808),
        text = Color(0xFFFFFFFF),
        muted = Color(0xFF8E8E93),
        border = Color(0xFF222222),
        accent = Color(0xFFFFFFFF),
        accentFg = Color(0xFF000000),
        isDark = true
    );

    companion object {
        fun fromId(id: String): AppThemeMode = entries.find { it.id == id } ?: LIGHT
    }
}
