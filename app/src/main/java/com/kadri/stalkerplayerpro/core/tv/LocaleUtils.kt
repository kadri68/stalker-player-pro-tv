package com.kadri.stalkerplayerpro.core.tv

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * أدوات توحيد الأرقام اللاتينية (0-9) واتجاه العرض LTR للقيم الرقمية والرموز
 */
object LocaleUtils {

    /**
     * تحويل أي أرقام عربية مشرقية أو فارسية إلى أرقام لاتينية (0-9)
     */
    fun String.toLatinDigits(): String {
        val sb = StringBuilder(length)
        for (ch in this) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * تنسيق الأرقام الصحيحة بأرقام لاتينية ثابتة
     */
    fun formatInt(value: Int): String = String.format(Locale.US, "%d", value)

    /**
     * تنسيق الأرقام العشرية بإشارة (مثل إزاحة الترجمة +0.5s / -0.1s)
     */
    fun formatOffsetSeconds(offsetMs: Long): String {
        val seconds = offsetMs / 1000.0
        return String.format(Locale.US, "%+.1fs", seconds)
    }

    /**
     * تنسيق الوقت الحالي (HH:mm) بأرقام لاتينية
     */
    fun formatClock(timestampMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        return sdf.format(Date(timestampMs))
    }

    /**
     * تنسيق التاريخ (yyyy-MM-dd) بأرقام لاتينية
     */
    fun formatDate(timestampMs: Long): String {
        if (timestampMs <= 0L) return "N/A"
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(timestampMs))
    }

    /**
     * تنسيق مدة الفيديو (HH:mm:ss أو mm:ss) بأرقام لاتينية
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}

/**
 * مكون نصي يفرض الاتجاه من اليسار لليمين (LTR) والأرقام اللاتينية (0-9)
 * لمنع انقلاب الساعات، أرقام القنوات، عناوين MAC، والروابط داخل الواجهة العربية
 */
@Composable
fun LtrText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    val normalized = with(LocaleUtils) { text.toLatinDigits() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = normalized,
            modifier = modifier,
            color = color,
            style = style,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}
