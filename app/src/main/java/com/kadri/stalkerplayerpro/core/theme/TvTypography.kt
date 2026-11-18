package com.kadri.stalkerplayerpro.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class TvFontScale(
    val id: Int,
    val factor: Float,
    val labelAr: String,
    val labelEn: String
) {
    PHONE_SMALL(4, 0.82f, "صغير - للهاتف (0.82x)", "Small - Phone (0.82x)"),
    MEDIUM(5, 1.00f, "متوسط (1.00x)", "Medium (1.00x)"),
    LARGE(0, 1.15f, "كبير - للتلفاز (1.15x)", "Large - TV (1.15x)"),
    EXTRA_LARGE(1, 1.30f, "كبير جداً (1.30x)", "Extra Large (1.30x)"),
    HUGE(2, 1.45f, "ضخم (1.45x)", "Huge (1.45x)"),
    MASSIVE(3, 1.60f, "ضخم جداً (1.60x)", "Massive (1.60x)");

    companion object {
        fun fromId(id: Int, isTvDefault: Boolean = true): TvFontScale {
            if (id == -1) return if (isTvDefault) LARGE else PHONE_SMALL
            return entries.find { it.id == id } ?: if (isTvDefault) LARGE else PHONE_SMALL
        }
    }
}

fun buildTvTypography(scale: TvFontScale): Typography {
    val f = scale.factor
    return Typography(
        headlineMedium = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (20f * f).sp,
            lineHeight = (26f * f).sp
        ),
        headlineSmall = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (17f * f).sp,
            lineHeight = (22f * f).sp
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (16f * f).sp,
            lineHeight = (21f * f).sp
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = (14.5f * f).sp,
            lineHeight = (19f * f).sp
        ),
        titleSmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = (13f * f).sp,
            lineHeight = (17f * f).sp
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (14.5f * f).sp,
            lineHeight = (20f * f).sp
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (13f * f).sp,
            lineHeight = (17.5f * f).sp
        ),
        bodySmall = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = (11.5f * f).sp,
            lineHeight = (15f * f).sp
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = (13.5f * f).sp,
            lineHeight = (17f * f).sp
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = (11.5f * f).sp,
            lineHeight = (15f * f).sp
        ),
        labelSmall = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = (10.5f * f).sp,
            lineHeight = (13.5f * f).sp
        )
    )
}
