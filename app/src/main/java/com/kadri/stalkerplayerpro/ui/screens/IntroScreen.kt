package com.kadri.stalkerplayerpro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadri.stalkerplayerpro.BuildConfig
import com.kadri.stalkerplayerpro.core.tv.LtrText
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * شاشة الترحيب (Intro) فائقة الخفة على معالج وذاكرة أجهزة التلفاز (Android TV).
 * - مدة العرض: 5 ثوانٍ بالضبط (5000 ميلي ثانية).
 * - انتقال ألفا انسيابي هادئ دون أي ضغط على الذاكرة أو GPU.
 * - تخطي سلس وفوري عند الضغط على أي زر في الريموت كنترول أو زر الرجوع.
 * - حماية دفاعية تمنع حدوث أي Crash أو ANR أو تجمد.
 */
@Composable
fun IntroScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDismissed = remember { AtomicBoolean(false) }
    val currentOnFinish by rememberUpdatedState(onFinish)

    val safeDismiss = remember {
        {
            if (isDismissed.compareAndSet(false, true)) {
                currentOnFinish()
            }
        }
    }

    // اعتمد زر الرجوع للتخطي الفوري بدلاً من إغلاق التطبيق أثناء الترحيب
    BackHandler(enabled = true) {
        safeDismiss()
    }

    val focusRequester = remember { FocusRequester() }
    val contentAlpha = remember { Animatable(0f) }

    // التحكم في التوقيت والأنيميشن الخفيف (5 ثوانٍ = 5000ms)
    LaunchedEffect(Unit) {
        // طلب التركيز للريموت كنترول فورياً
        runCatching { focusRequester.requestFocus() }
        delay(60L)
        runCatching { focusRequester.requestFocus() }

        runCatching {
            // ظهور تدريجي هادئ (600ms)
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing)
            )
            // ثبات الرؤية لمدة 3900ms (المجموع = 4500ms)
            delay(3900L)
            // تلاشي ناعم لمدة 500ms (المجموع الإجمالي = 5000ms بالضبط)
            contentAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, easing = FastOutLinearInEasing)
            )
        }
        safeDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    safeDismiss()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                safeDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = contentAlpha.value }
                .padding(horizontal = 48.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. مرحبا بكم في تطبيق
            Text(
                text = "مرحبا بكم في تطبيق",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    lineHeight = 32.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. IPTV STALKER مع شارة رقم الإصدار المحدث
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                LtrText(
                    text = "IPTV STALKER",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 42.sp,
                        lineHeight = 50.sp,
                        letterSpacing = 2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                    )
                ) {
                    LtrText(
                        text = "v${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. فريق kadripro
            Text(
                text = "فريق kadripro",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 22.sp,
                    lineHeight = 30.sp
                ),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. اتمنى لكم فرجه ممتعه
            Text(
                text = "اتمنى لكم فرجه ممتعه",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 28.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }

        // تلميح التخطي بالريموت بأسفل الشاشة
        Text(
            text = "اضغط على أي زر للتخطي",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .graphicsLayer { alpha = contentAlpha.value * 0.6f },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 13.sp
            ),
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
