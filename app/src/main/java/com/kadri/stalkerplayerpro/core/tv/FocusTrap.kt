package com.kadri.stalkerplayerpro.core.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvFocusTrap(
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester = remember { FocusRequester() },
    restoreFocusRequester: FocusRequester? = null,
    onDismiss: () -> Unit,
    content: @Composable (firstItemRequester: FocusRequester) -> Unit
) {
    BackHandler {
        restoreFocusRequester?.let { runCatching { it.requestFocus() } }
        onDismiss()
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { initialFocusRequester.requestFocus() }
    }

    Box(
        modifier = modifier.focusGroup()
    ) {
        content(initialFocusRequester)
    }
}

fun Modifier.blockFocusWhen(blocked: Boolean): Modifier =
    this.focusProperties {
        canFocus = !blocked
    }

/**
 * تحكم خطوي دقيق بالريموت (Up / Down) داخل القوائم العمودية (LazyColumn):
 * - يمنع تحرك القائمة بالكامل عند النزول، ويمنع القفز أو تخطي الأسطر عند الصعود.
 * - ينقل التركيز صفاً بصف (index - 1 / index + 1) ولا يسمح بالخروج للأعلى إلا من الصف الأول (index == 0).
 */
fun Modifier.tvVerticalStepItem(
    index: Int,
    totalCount: Int,
    listState: LazyListState,
    requesters: MutableMap<Int, FocusRequester>,
    scope: CoroutineScope,
    onExitTop: (() -> Boolean)? = null,
    onExitBottom: (() -> Boolean)? = null
): Modifier = this.onPreviewKeyEvent { ev ->
    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (ev.key) {
        Key.DirectionDown -> {
            if (index < totalCount - 1) {
                val nextIdx = index + 1
                val nextReq = requesters[nextIdx]
                val layoutInfo = listState.layoutInfo
                val nextInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == nextIdx }
                val fullyVisible = nextInfo != null &&
                    (nextInfo.offset + nextInfo.size) <= layoutInfo.viewportEndOffset
                if (fullyVisible && nextReq != null && runCatching { nextReq.requestFocus() }.isSuccess) {
                    return@onPreviewKeyEvent true
                }
                scope.launch {
                    runCatching { listState.scrollToItem(nextIdx) }
                    withFrameNanos { }
                    runCatching { requesters[nextIdx]?.requestFocus() }
                }
                true
            } else {
                if (onExitBottom != null && onExitBottom.invoke()) {
                    true
                } else {
                    true
                }
            }
        }
        Key.DirectionUp -> {
            if (index > 0) {
                val prevIdx = index - 1
                val prevReq = requesters[prevIdx]
                val layoutInfo = listState.layoutInfo
                val prevInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == prevIdx }
                val fullyVisible = prevInfo != null &&
                    prevInfo.offset >= layoutInfo.viewportStartOffset
                if (fullyVisible && prevReq != null && runCatching { prevReq.requestFocus() }.isSuccess) {
                    return@onPreviewKeyEvent true
                }
                scope.launch {
                    runCatching { listState.scrollToItem(prevIdx) }
                    withFrameNanos { }
                    runCatching { requesters[prevIdx]?.requestFocus() }
                }
                true
            } else {
                onExitTop?.invoke() ?: false
            }
        }
        else -> false
    }
}

/**
 * تحكم خطوي دقيق بالريموت داخل الأسطر الأفقية (Left / Right / Up / Down):
 * - ينتقل زراً تلو الآخر أفقياً مع التمرير التلقائي لإظهار الأزرار الطرفية.
 * - ينقل التركيز مباشرة للحقل المجاور عند الوصول لنهاية السطر أو الضغط Up / Down دون الحاجة لـ OK.
 */
fun Modifier.tvHorizontalStepItem(
    index: Int,
    totalCount: Int,
    requesters: List<FocusRequester>,
    isAr: Boolean,
    scrollState: ScrollState? = null,
    scope: CoroutineScope? = null,
    onExitStart: (() -> Boolean)? = null,
    onExitEnd: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null
): Modifier = this.onPreviewKeyEvent { ev ->
    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val moveNext = (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight)
    val movePrev = (isAr && ev.key == Key.DirectionRight) || (!isAr && ev.key == Key.DirectionLeft)
    when {
        moveNext -> {
            if (index < totalCount - 1) {
                val nextIdx = index + 1
                runCatching { requesters.getOrNull(nextIdx)?.requestFocus() }
                if (scrollState != null && scope != null && totalCount > 1) {
                    val target = (scrollState.maxValue * nextIdx) / (totalCount - 1)
                    scope.launch { runCatching { scrollState.animateScrollTo(target) } }
                }
                true
            } else {
                onExitEnd?.invoke() ?: true
            }
        }
        movePrev -> {
            if (index > 0) {
                val prevIdx = index - 1
                runCatching { requesters.getOrNull(prevIdx)?.requestFocus() }
                if (scrollState != null && scope != null && totalCount > 1) {
                    val target = (scrollState.maxValue * prevIdx) / (totalCount - 1)
                    scope.launch { runCatching { scrollState.animateScrollTo(target) } }
                }
                true
            } else {
                onExitStart?.invoke() ?: true
            }
        }
        ev.key == Key.DirectionUp -> onUp?.invoke() ?: false
        ev.key == Key.DirectionDown -> onDown?.invoke() ?: false
        else -> false
    }
}

@Composable
fun TvSafeSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    val textFieldFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = focusRequester ?: remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (isEditing) {
        BackHandler {
            isEditing = false
            keyboardController?.hide()
            runCatching { buttonFocusRequester.requestFocus() }
        }

        LaunchedEffect(Unit) {
            delay(50)
            runCatching { textFieldFocusRequester.requestFocus() }
            keyboardController?.show()
        }

        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(textFieldFocusRequester),
                singleLine = true,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        isEditing = false
                        keyboardController?.hide()
                        runCatching { buttonFocusRequester.requestFocus() }
                    },
                    onDone = {
                        isEditing = false
                        keyboardController?.hide()
                        runCatching { buttonFocusRequester.requestFocus() }
                    }
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                onQueryChange("")
                                isEditing = false
                                keyboardController?.hide()
                                runCatching { buttonFocusRequester.requestFocus() }
                            }
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .tvFocusItem(
                    focusRequester = buttonFocusRequester,
                    shape = RoundedCornerShape(10.dp),
                    unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    onClick = { isEditing = true }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = query.ifBlank { placeholder },
                style = MaterialTheme.typography.bodyMedium,
                color = if (query.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
