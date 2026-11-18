package com.kadri.stalkerplayerpro.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class TvExtraColors(
    val focusRing: Color,
    val badgeLive: Color,
    val badgeSuccess: Color,
    val badgeWarning: Color,
    val panelOverlay: Color
)

val LocalTvExtraColors = staticCompositionLocalOf {
    TvExtraColors(
        focusRing = Color(0xFFFFD600),
        badgeLive = Color(0xFFFF1744),
        badgeSuccess = Color(0xFF00E676),
        badgeWarning = Color(0xFFFFAB00),
        panelOverlay = Color(0xE60A1128)
    )
}

/**
 * الثيمات الـ 6 المصممة خصيصاً لشاشات التلفاز بتباين عالٍ بدون وهج مزعج
 */
enum class AppThemeId(
    val id: Int,
    val titleAr: String,
    val titleEn: String,
    val colorScheme: ColorScheme,
    val extraColors: TvExtraColors
) {
    MIDNIGHT_CYAN(
        id = 0,
        titleAr = "الأزرق الليلي (الافتراضي)",
        titleEn = "Midnight Cyan (Default)",
        colorScheme = darkColorScheme(
            primary = Color(0xFF00E5FF),
            onPrimary = Color(0xFF002026),
            secondary = Color(0xFFFFD600),
            onSecondary = Color(0xFF261E00),
            background = Color(0xFF0A1128),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF132042),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF1C2E59),
            onSurfaceVariant = Color(0xFFCBD5E1),
            outline = Color(0xFF475569),
            error = Color(0xFFFF5252),
            onError = Color(0xFF2B0000)
        ),
        extraColors = TvExtraColors(
            focusRing = Color(0xFFFFD600),
            badgeLive = Color(0xFFFF1744),
            badgeSuccess = Color(0xFF00E676),
            badgeWarning = Color(0xFFFFAB00),
            panelOverlay = Color(0xEB0A1128)
        )
    ),

    AMOLED_OBSIDIAN(
        id = 1,
        titleAr = "الأسود الأوليد الفائق",
        titleEn = "AMOLED Obsidian",
        colorScheme = darkColorScheme(
            primary = Color(0xFF00E676),
            onPrimary = Color(0xFF002611),
            secondary = Color(0xFF00B0FF),
            onSecondary = Color(0xFF001E2E),
            background = Color(0xFF050505),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFF5F5F5),
            surfaceVariant = Color(0xFF1F1F1F),
            onSurfaceVariant = Color(0xFFD4D4D4),
            outline = Color(0xFF525252),
            error = Color(0xFFFF5252),
            onError = Color(0xFF2B0000)
        ),
        extraColors = TvExtraColors(
            focusRing = Color(0xFF00E676),
            badgeLive = Color(0xFFFF1744),
            badgeSuccess = Color(0xFF00E676),
            badgeWarning = Color(0xFFFFD600),
            panelOverlay = Color(0xF0050505)
        )
    ),

    CINEMA_SLATE(
        id = 2,
        titleAr = "الرمادي السينمائي",
        titleEn = "Cinema Slate",
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF9100),
            onPrimary = Color(0xFF291500),
            secondary = Color(0xFF40C4FF),
            onSecondary = Color(0xFF002030),
            background = Color(0xFF12151C),
            onBackground = Color(0xFFF3F4F6),
            surface = Color(0xFF1C212B),
            onSurface = Color(0xFFF9FAFB),
            surfaceVariant = Color(0xFF282F3D),
            onSurfaceVariant = Color(0xFFD1D5DB),
            outline = Color(0xFF4B5563),
            error = Color(0xFFFF5252),
            onError = Color(0xFF2B0000)
        ),
        extraColors = TvExtraColors(
            focusRing = Color(0xFFFFAB00),
            badgeLive = Color(0xFFFF1744),
            badgeSuccess = Color(0xFF00E676),
            badgeWarning = Color(0xFFFFD600),
            panelOverlay = Color(0xEB12151C)
        )
    ),

    EMERALD_DARK(
        id = 3,
        titleAr = "الزمردي الداكن",
        titleEn = "Emerald Dark",
        colorScheme = darkColorScheme(
            primary = Color(0xFF1DE9B6),
            onPrimary = Color(0xFF00261C),
            secondary = Color(0xFFFFD740),
            onSecondary = Color(0xFF261F00),
            background = Color(0xFF071A14),
            onBackground = Color(0xFFECFDF5),
            surface = Color(0xFF0E2B22),
            onSurface = Color(0xFFF0FDF4),
            surfaceVariant = Color(0xFF163D31),
            onSurfaceVariant = Color(0xFFA7F3D0),
            outline = Color(0xFF34D399),
            error = Color(0xFFFF5252),
            onError = Color(0xFF2B0000)
        ),
        extraColors = TvExtraColors(
            focusRing = Color(0xFFFFD740),
            badgeLive = Color(0xFFFF1744),
            badgeSuccess = Color(0xFF1DE9B6),
            badgeWarning = Color(0xFFFFAB00),
            panelOverlay = Color(0xEB071A14)
        )
    ),

    ROYAL_INDIGO(
        id = 4,
        titleAr = "البنفسجي الملكي",
        titleEn = "Royal Indigo",
        colorScheme = darkColorScheme(
            primary = Color(0xFFB388FF),
            onPrimary = Color(0xFF1F004D),
            secondary = Color(0xFF00E5FF),
            onSecondary = Color(0xFF002026),
            background = Color(0xFF110B29),
            onBackground = Color(0xFFF5F3FF),
            surface = Color(0xFF1C1440),
            onSurface = Color(0xFFFAF5FF),
            surfaceVariant = Color(0xFF2A1F5C),
            onSurfaceVariant = Color(0xFFDDD6FE),
            outline = Color(0xFF6D28D9),
            error = Color(0xFFFF5252),
            onError = Color(0xFF2B0000)
        ),
        extraColors = TvExtraColors(
            focusRing = Color(0xFF00E5FF),
            badgeLive = Color(0xFFFF1744),
            badgeSuccess = Color(0xFF00E676),
            badgeWarning = Color(0xFFFFD600),
            panelOverlay = Color(0xEB110B29)
        )
    ),

    WARM_AMBER(
        id = 5,
        titleAr = "الكهرماني الدافئ",
        titleEn = "Warm Amber",
        colorScheme = darkColorScheme(
            primary = Color(0xFFFFCA28),
            onPrimary = Color(0xFF261C00),
            secondary = Color(0xFFFF7043),
            onSecondary = Color(0xFF2B0B00),
            background = Color(0xFF1A120B),
            onBackground = Color(0xFFFEF3C7),
            surface = Color(0xFF291D12),
            onSurface = Color(0xFFFFFBEB),
            surfaceVariant = Color(0xFF3B2A1B),
            onSurfaceVariant = Color(0xFFFDE68A),
            outline = Color(0xFF78350F),
            error = Color(0xFFFF5252),
            onError = Color(0xFF2B0000)
        ),
        extraColors = TvExtraColors(
            focusRing = Color(0xFFFF6D00),
            badgeLive = Color(0xFFFF1744),
            badgeSuccess = Color(0xFF00E676),
            badgeWarning = Color(0xFFFFCA28),
            panelOverlay = Color(0xEB1A120B)
        )
    );

    companion object {
        fun fromId(id: Int): AppThemeId = entries.find { it.id == id } ?: MIDNIGHT_CYAN
    }
}

@Composable
fun StalkerTvTheme(
    themeId: AppThemeId = AppThemeId.MIDNIGHT_CYAN,
    fontScale: TvFontScale = TvFontScale.LARGE,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTvExtraColors provides themeId.extraColors
    ) {
        MaterialTheme(
            colorScheme = themeId.colorScheme,
            typography = buildTvTypography(fontScale),
            content = content
        )
    }
}
