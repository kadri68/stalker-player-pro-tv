package com.kadri.stalkerplayerpro.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * مقاييس الخطوط التلفزيونية الـ 4 المخصصة للمشاهدة من مسافة بعيدة:
 * كبير (1.15x) - كبير جداً (1.30x) - ضخم (1.45x) - ضخم جداً (1.60x)
 */
enum class TvFontScale(
    val id: Int,
    val factor: Float,
    val labelAr: String,
    val labelEn: String
) {
    LARGE(0, 1.15f, "كبير (1.15x)", "Large (1.15x)"),
    EXTRA_LARGE(1, 1.30f, "كبير جداً (1.30x)", "Extra Large (1.30x)"),
    HUGE(2, 1.45f, "ضخم (1.45x)", "Huge (1.45x)"),
    MASSIVE(3, 1.60f, "ضخم جداً (1.60x)", "Massive (1.60x)");

    companion object {
        fun fromId(id: Int): TvFontScale = entries.find { it.id == id } ?: LARGE
    }
}

fun buildTvTypography(scale: TvFontScale): Typography {
    val f = scale.factor
    return Typography(
        headlineMedium = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (22f * f).sp,
            lineHeight = (28f * f).sp
        ),
        headlineSmall = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (18f * f).sp,
            lineHeight = (24f * f).sp
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (17f * f).sp,
            lineHeight = (22f * f).sp
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = (15f * f).sp,
            lineHeight = (20f * f).sp
        ),
        titleSmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = (13.5f * f).sp,
            lineHeight = (18f * f).sp
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (15f * f).sp,
            lineHeight = (21f * f).sp
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (13.5f * f).sp,
            lineHeight = (18f * f).sp
        ),
        bodySmall = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (12f * f).sp,
            lineHeight = (16f * f).sp
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (14f * f).sp,
            lineHeight = (18f * f).sp
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = (12f * f).sp,
            lineHeight = (16f * f).sp
        ),
        labelSmall = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = (11f * f).sp,
            lineHeight = (14f * f).sp
        )
    )
}
