package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

enum class ReaderFontOption(val id: String, val label: String) {
    NOTO_SANS("noto-sans", "Noto Sans"),
    NOTO_SERIF("noto-serif", "Noto Serif"),
    HIND_SILIGURI("hind-siliguri", "Hind Siliguri"),
    TIRO_BANGLA("tiro-bangla", "Tiro Bangla"),
    JETBRAINS_MONO("jetbrains-mono", "JetBrains Mono");

    fun toFontFamily(): FontFamily = when (this) {
        NOTO_SANS -> NotoSansBengaliFont
        NOTO_SERIF -> NotoSerifBengaliFont
        HIND_SILIGURI -> HindSiliguriFont
        TIRO_BANGLA -> TiroBanglaFont
        JETBRAINS_MONO -> JetBrainsMonoFont
    }

    companion object {
        fun fromId(id: String): ReaderFontOption = entries.find { it.id == id } ?: NOTO_SANS
    }
}

val NotoSansBengaliFont = FontFamily(
    Font(R.font.noto_sans_bengali, FontWeight.Normal),
    Font(R.font.noto_sans_bengali, FontWeight.Medium),
    Font(R.font.noto_sans_bengali, FontWeight.Bold)
)

val NotoSerifBengaliFont = FontFamily(
    Font(R.font.noto_serif_bengali, FontWeight.Normal),
    Font(R.font.noto_serif_bengali, FontWeight.Bold)
)

val HindSiliguriFont = FontFamily(
    Font(R.font.hind_siliguri, FontWeight.Normal),
    Font(R.font.hind_siliguri, FontWeight.Bold)
)

val TiroBanglaFont = FontFamily(
    Font(R.font.tiro_bangla, FontWeight.Normal)
)

val JetBrainsMonoFont = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = NotoSansBengaliFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NotoSansBengaliFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NotoSansBengaliFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NotoSansBengaliFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NotoSansBengaliFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NotoSansBengaliFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
