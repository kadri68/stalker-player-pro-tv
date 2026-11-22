package com.kadri.stalkerplayerpro.core.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
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
        modifier = modifier
            .focusGroup()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown &&
                    (ev.key == Key.DirectionUp || ev.key == Key.DirectionDown)
                ) {
                    // منع تسرب التركيز خارج النافذة المنبثقة إذا لم يلتقطه عنصر داخلي
                    true
                } else {
                    false
                }
            }
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
                if (nextReq != null && runCatching { nextReq.requestFocus() }.isSuccess) {
                    return@onPreviewKeyEvent true
                }
                scope.launch {
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val itemSize = visibleItems.lastOrNull()?.size ?: visibleItems.firstOrNull()?.size ?: 60
                    val spacing = layoutInfo.mainAxisItemSpacing
                    val delta = (itemSize + spacing).toFloat().coerceAtLeast(40f)
                    runCatching { listState.scrollBy(delta) }
                    withFrameNanos { }
                    val req = requesters[nextIdx]
                    val success = req != null && runCatching { req.requestFocus() }.isSuccess
                    if (!success) {
                        withFrameNanos { }
                        runCatching { requesters[nextIdx]?.requestFocus() }
                    }
                }
                true
            } else {
                onExitBottom?.invoke()
                true
            }
        }
        Key.DirectionUp -> {
            if (index > 0) {
                val prevIdx = index - 1
                val prevReq = requesters[prevIdx]
                if (prevReq != null && runCatching { prevReq.requestFocus() }.isSuccess) {
                    return@onPreviewKeyEvent true
                }
                scope.launch {
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val itemSize = visibleItems.firstOrNull()?.size ?: 60
                    val spacing = layoutInfo.mainAxisItemSpacing
                    val delta = (itemSize + spacing).toFloat().coerceAtLeast(40f)
                    runCatching { listState.scrollBy(-delta) }
                    withFrameNanos { }
                    val req = requesters[prevIdx]
                    val success = req != null && runCatching { req.requestFocus() }.isSuccess
                    if (!success) {
                        withFrameNanos { }
                        runCatching { requesters[prevIdx]?.requestFocus() }
                    }
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
    focusRequester: FocusRequester? = null,
    isAr: Boolean = true,
    searchHistory: List<String> = emptyList(),
    onSaveSearchQuery: ((String) -> Unit)? = null,
    onClearSearchHistory: (() -> Unit)? = null
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

        val visibleHistory = remember(searchHistory) { searchHistory.take(5) }
        val totalHistoryItems = visibleHistory.size + if (visibleHistory.isNotEmpty() && onClearSearchHistory != null) 1 else 0
        val historyRequesters = remember(totalHistoryItems) { List(totalHistoryItems) { FocusRequester() } }

        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(textFieldFocusRequester)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown && totalHistoryItems > 0) {
                                keyboardController?.hide()
                                runCatching { historyRequesters[0].requestFocus() }.isSuccess
                            } else {
                                false
                            }
                        },
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
                            if (query.trim().length >= 2) onSaveSearchQuery?.invoke(query.trim())
                            isEditing = false
                            keyboardController?.hide()
                            runCatching { buttonFocusRequester.requestFocus() }
                        },
                        onDone = {
                            if (query.trim().length >= 2) onSaveSearchQuery?.invoke(query.trim())
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

            if (visibleHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    visibleHistory.forEachIndexed { hIdx, itemText ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (ev.key) {
                                        Key.DirectionDown -> {
                                            if (hIdx < totalHistoryItems - 1) {
                                                runCatching { historyRequesters[hIdx + 1].requestFocus() }.isSuccess
                                            } else true
                                        }
                                        Key.DirectionUp -> {
                                            if (hIdx > 0) {
                                                runCatching { historyRequesters[hIdx - 1].requestFocus() }.isSuccess
                                            } else {
                                                runCatching { textFieldFocusRequester.requestFocus() }.isSuccess
                                            }
                                        }
                                        else -> false
                                    }
                                }
                                .tvFocusItem(
                                    focusRequester = historyRequesters[hIdx],
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    onClick = {
                                        onQueryChange(itemText)
                                        isEditing = false
                                        keyboardController?.hide()
                                        runCatching { buttonFocusRequester.requestFocus() }
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = itemText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (onClearSearchHistory != null) {
                        val clearIdx = visibleHistory.size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                        if (clearIdx > 0) {
                                            runCatching { historyRequesters[clearIdx - 1].requestFocus() }.isSuccess
                                        } else {
                                            runCatching { textFieldFocusRequester.requestFocus() }.isSuccess
                                        }
                                    } else false
                                }
                                .tvFocusItem(
                                    focusRequester = historyRequesters[clearIdx],
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedBackgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                                    onClick = {
                                        onClearSearchHistory()
                                        runCatching { textFieldFocusRequester.requestFocus() }
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAr) "مسح سجل البحث" else "Clear Search History",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
