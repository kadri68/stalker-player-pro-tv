package com.kadri.stalkerplayerpro.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadri.stalkerplayerpro.data.model.SubtitleCue

/**
 * عارض الترجمة المستقل:
 * يطبّق إزاحة التوقيت (subtitleOffsetMs) حصرياً على طوابع الترجمة الزمنية
 * دون المساس بتدفق الفيديو أو الصوت إطلاقاً.
 */
@Composable
fun SubtitleOverlayRenderer(
    cues: List<SubtitleCue>,
    videoPositionMs: Long,
    subtitleOffsetMs: Long,
    textColor: Color,
    fontSizeSp: Float,
    bgAlpha: Float,
    modifier: Modifier = Modifier
) {
    if (cues.isEmpty()) return

    val activeText = remember(cues, videoPositionMs, subtitleOffsetMs) {
        val adjustedSubTime = videoPositionMs - subtitleOffsetMs
        findActiveCueText(cues, adjustedSubTime)
    }

    if (activeText.isNullOrBlank()) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 42.dp, start = 36.dp, end = 36.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = activeText,
            color = textColor,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.28f).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = bgAlpha.coerceIn(0f, 1f)),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

private fun findActiveCueText(cues: List<SubtitleCue>, timeMs: Long): String? {
    if (timeMs < 0L || cues.isEmpty()) return null
    var low = 0
    var high = cues.lastIndex
    while (low <= high) {
        val mid = (low + high) ushr 1
        val cue = cues[mid]
        when {
            timeMs < cue.startMs -> high = mid - 1
            timeMs > cue.endMs -> low = mid + 1
            else -> return cue.text
        }
    }
    return null
}
