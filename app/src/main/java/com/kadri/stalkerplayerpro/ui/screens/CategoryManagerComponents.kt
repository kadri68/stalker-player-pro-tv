package com.kadri.stalkerplayerpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kadri.stalkerplayerpro.core.theme.LocalTvExtraColors
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.core.tv.tvVerticalStepItem
import com.kadri.stalkerplayerpro.data.local.CategoryRuleEntity
import com.kadri.stalkerplayerpro.data.model.CategoryItem
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog

private val ADULT_KEYWORDS = listOf(
    "adult", "18+", "+18", "xxx", "porn", "sex", "erotic", "nsfw", "for adults", "playboy", "brazzers", "redlight", "hustler"
)

fun isAdultCategoryName(name: String): Boolean {
    val lower = name.lowercase()
    return ADULT_KEYWORDS.any { kw -> lower.contains(kw) }
}

@Composable
fun CategoryRulesManagerDialog(
    portalName: String,
    sectionTitle: String,
    categories: List<CategoryItem>,
    rulesMap: Map<String, CategoryRuleEntity>,
    isAr: Boolean,
    onToggleHidden: (CategoryItem, Boolean) -> Unit,
    onToggleLocked: (CategoryItem, Boolean) -> Unit,
    onSetAllAdultHidden: (List<CategoryItem>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val extraColors = LocalTvExtraColors.current
    val scope = rememberCoroutineScope()
    val adultCategories = remember(categories) { categories.filter { isAdultCategoryName(it.name) } }
    val allAdultHidden = remember(adultCategories, rulesMap) {
        adultCategories.isNotEmpty() && adultCategories.all { rulesMap[it.id]?.isHidden == true }
    }

    val closeBtnReq = remember { FocusRequester() }
    val doneBtnReq = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val visRequesters = remember(categories.size) { mutableStateMapOf<Int, FocusRequester>() }
    val lockRequesters = remember(categories.size) { mutableStateMapOf<Int, FocusRequester>() }

    TvModalDialog(
        title = if (isAr) "إدارة مجموعات ($sectionTitle) - $portalName" else "Manage $sectionTitle Categories - $portalName",
        onDismiss = onDismiss,
        maxWidth = 650.dp,
        closeBtnRequester = closeBtnReq,
        footer = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp) {
                                val lastIdx = (categories.size - 1).coerceAtLeast(0)
                                runCatching { visRequesters[lastIdx]?.requestFocus() }.isSuccess
                            } else false
                        }
                        .tvFocusItem(
                            focusRequester = doneBtnReq,
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 28.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isAr) "تم / إغلاق" else "Done / Close",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    ) { firstRequester ->
        val adultBtnReq = remember { FocusRequester() }
        if (adultCategories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isAr) "مجموعات الكبار المكتشفة (${adultCategories.size})" else "Detected Adult Categories (${adultCategories.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (ev.key) {
                                Key.DirectionUp -> runCatching { closeBtnReq.requestFocus() }.isSuccess
                                Key.DirectionDown -> {
                                    if (categories.isNotEmpty()) runCatching { visRequesters[0]?.requestFocus() }.isSuccess
                                    else runCatching { doneBtnReq.requestFocus() }.isSuccess
                                }
                                else -> false
                            }
                        }
                        .tvFocusItem(
                            focusRequester = firstRequester,
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (allAdultHidden) extraColors.badgeSuccess else extraColors.badgeWarning,
                            onClick = { onSetAllAdultHidden(adultCategories, !allAdultHidden) }
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (allAdultHidden) {
                            if (isAr) "إظهار الكبار" else "Show Adult"
                        } else {
                            if (isAr) "إخفاء الكبار" else "Hide Adult"
                        },
                        color = Color.Black,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (categories.isEmpty()) {
            Text(
                text = if (isAr) "لا توجد مجموعات محمّلة حالياً." else "No categories loaded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (adultCategories.isEmpty()) {
                visRequesters[0] = firstRequester
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 330.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = categories,
                    key = { idx, cat -> "rule_${cat.id}_$idx" }
                ) { idx, cat ->
                    val rule = rulesMap[cat.id]
                    val isHidden = rule?.isHidden == true
                    val isLocked = rule?.isLocked == true
                    val vReq = if (idx == 0 && adultCategories.isEmpty()) firstRequester else visRequesters.getOrPut(idx) { FocusRequester() }
                    val lReq = lockRequesters.getOrPut(idx) { FocusRequester() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isHidden) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isHidden) FontWeight.Normal else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .tvVerticalStepItem(
                                    index = idx,
                                    totalCount = categories.size,
                                    listState = listState,
                                    requesters = visRequesters,
                                    scope = scope,
                                    onExitTop = {
                                        if (adultCategories.isNotEmpty()) runCatching { firstRequester.requestFocus() }.isSuccess
                                        else runCatching { closeBtnReq.requestFocus() }.isSuccess
                                    },
                                    onExitBottom = { runCatching { doneBtnReq.requestFocus() }.isSuccess }
                                )
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    val moveToLock = (isAr && ev.key == Key.DirectionLeft) || (!isAr && ev.key == Key.DirectionRight)
                                    if (moveToLock) runCatching { lReq.requestFocus() }.isSuccess else false
                                }
                                .tvFocusItem(
                                    focusRequester = vReq,
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedBackgroundColor = if (isHidden) extraColors.badgeWarning else MaterialTheme.colorScheme.surface,
                                    onClick = { onToggleHidden(cat, !isHidden) }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = if (isHidden) Color.Black else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isHidden) (if (isAr) "مخفية" else "Hidden") else (if (isAr) "ظاهرة" else "Visible"),
                                    color = if (isHidden) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .tvVerticalStepItem(
                                    index = idx,
                                    totalCount = categories.size,
                                    listState = listState,
                                    requesters = lockRequesters,
                                    scope = scope,
                                    onExitTop = {
                                        if (adultCategories.isNotEmpty()) runCatching { firstRequester.requestFocus() }.isSuccess
                                        else runCatching { closeBtnReq.requestFocus() }.isSuccess
                                    },
                                    onExitBottom = { runCatching { doneBtnReq.requestFocus() }.isSuccess }
                                )
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    val moveToVis = (isAr && ev.key == Key.DirectionRight) || (!isAr && ev.key == Key.DirectionLeft)
                                    if (moveToVis) runCatching { vReq.requestFocus() }.isSuccess else false
                                }
                                .tvFocusItem(
                                    focusRequester = lReq,
                                    shape = RoundedCornerShape(8.dp),
                                    unfocusedBackgroundColor = if (isLocked) extraColors.badgeWarning else MaterialTheme.colorScheme.surface,
                                    onClick = { onToggleLocked(cat, !isLocked) }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (isLocked) Color.Black else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isLocked) (if (isAr) "مقفلة" else "Locked") else (if (isAr) "مفتوحة" else "Open"),
                                    color = if (isLocked) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
