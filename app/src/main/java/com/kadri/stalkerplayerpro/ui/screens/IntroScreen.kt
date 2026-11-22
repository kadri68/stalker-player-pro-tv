package com.kadri.stalkerplayerpro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TvIntroScreen(
    onFinished: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        isVisible = true
        delay(80)
        runCatching { focusRequester.requestFocus() }
        delay(2400)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF132248),
                        Color(0xFF0A1128),
                        Color(0xFF04060E)
                    )
                )
            )
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) {
                    onFinished()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(600)) + scaleIn(tween(600, easing = FastOutSlowInEasing), initialScale = 0.85f),
            exit = fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 1.05f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // TV Logo Emblem
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF00E5FF), Color(0xFF0091EA))
                            )
                        )
                        .border(3.dp, Color(0xFF80D8FF), CircleShape)
                        .shadow(20.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "مرحبا بكم في تطبيق",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    color = Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "IPTV STALKER",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    ),
                    color = Color(0xFF00E5FF),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "فرجة ممتعة",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFFFD600),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(30.dp))

                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color(0xFF00E5FF),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
