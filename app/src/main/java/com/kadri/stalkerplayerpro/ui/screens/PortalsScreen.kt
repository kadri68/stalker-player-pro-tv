package com.kadri.stalkerplayerpro.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kadri.stalkerplayerpro.core.theme.LocalTvExtraColors
import com.kadri.stalkerplayerpro.core.tv.LocaleUtils
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.blockFocusWhen
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.core.tv.tvHorizontalStepItem
import com.kadri.stalkerplayerpro.core.tv.tvVerticalStepItem
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import com.kadri.stalkerplayerpro.data.model.ProtocolType
import com.kadri.stalkerplayerpro.data.network.M3uStreamParser
import com.kadri.stalkerplayerpro.data.network.SmartMultiParser
import com.kadri.stalkerplayerpro.data.network.StalkerApiClient
import com.kadri.stalkerplayerpro.data.network.XtreamApiClient
import com.kadri.stalkerplayerpro.ui.components.PortalQrDialog
import com.kadri.stalkerplayerpro.ui.components.QrCodeHelper
import com.kadri.stalkerplayerpro.ui.components.TvActionMenuDialog
import com.kadri.stalkerplayerpro.ui.components.TvMenuOption
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import com.kadri.stalkerplayerpro.ui.components.TvTextInputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

const val BUILTIN_FREE_AR_M3U_URL = "https://iptv-org.github.io/iptv/languages/ara.m3u"

@Composable
fun PortalsScreen(
    dao: AppDao,
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() },
    onNavigateToTopBar: () -> Boolean = { false },
    lastFocusedPortalId: Long = -1L,
    onPortalFocused: (Long) -> Unit = {},
    onPortalActivated: (PortalEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val portals by dao.observePortals().collectAsState(initial = emptyList())

    var selectedProtocolFilter by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSmartParserDialog by remember { mutableStateOf(false) }
    var smartInitialText by remember { mutableStateOf("") }
    var selectedForMenu by remember { mutableStateOf<PortalEntity?>(null) }
    var selectedForEdit by remember { mutableStateOf<PortalEntity?>(null) }
    var selectedForQr by remember { mutableStateOf<PortalEntity?>(null) }
    var selectedForRename by remember { mutableStateOf<PortalEntity?>(null) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isCheckingAll by remember { mutableStateOf(false) }

    val anyDialogOpen = showAddDialog || showSmartParserDialog || selectedForMenu != null ||
            selectedForEdit != null || selectedForQr != null || selectedForRename != null

    val stalkerList = remember(portals) { portals.filter { it.protocol == "STALKER" } }
    val xtreamList = remember(portals) { portals.filter { it.protocol == "XTREAM" } }
    val m3uList = remember(portals) { portals.filter { it.protocol == "M3U" } }

    val displayedPortals = remember(portals, selectedProtocolFilter) {
        when (selectedProtocolFilter) {
            "STALKER" -> stalkerList
            "XTREAM" -> xtreamList
            "M3U" -> m3uList
            else -> portals
        }
    }

    val targetFocusIndex = remember(displayedPortals, lastFocusedPortalId) {
        val idx = displayedPortals.indexOfFirst { it.id == lastFocusedPortalId }
        if (idx >= 0) idx else 0
    }
    val listState = rememberLazyListState()
    val quickRowScroll = rememberScrollState()
    val filterRowScroll = rememberScrollState()
    val quickRequesters = remember { List(8) { FocusRequester() } }
    val filterRequesters = remember { List(4) { FocusRequester() } }
    val portalRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }
    var didInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(displayedPortals.isNotEmpty(), anyDialogOpen) {
        if (!anyDialogOpen && !didInitialFocus) {
            delay(90)
            if (displayedPortals.isNotEmpty()) {
                val startIdx = targetFocusIndex.coerceIn(0, displayedPortals.lastIndex)
                if (startIdx > 0) runCatching { listState.scrollToItem(startIdx) }
                delay(30)
                runCatching { portalRequesters.getOrPut(startIdx) { FocusRequester() }.requestFocus() }
            } else {
                runCatching { quickRequesters[0].requestFocus() }
            }
            didInitialFocus = true
        }
    }

    fun readClipboardText(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }

    val txtImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isCheckingAll = true
                val rawText = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    }.getOrDefault("")
                }
                val breakdown = SmartMultiParser.analyzeText(rawText)
                val working = SmartMultiParser.filterWorkingAccounts(breakdown.allExtracted)
                if (working.isNotEmpty()) {
                    dao.insertPortals(working)
                    val stkOk = working.count { it.protocol == "STALKER" }
                    val xtOk = working.count { it.protocol == "XTREAM" }
                    val m3uOk = working.count { it.protocol == "M3U" }
                    Toast.makeText(
                        context,
                        if (isAr) {
                            "تم استيراد ${working.size} شغال: Stalker($stkOk) | Xtream($xtOk) | M3U($m3uOk)"
                        } else {
                            "Imported ${working.size} working: Stalker($stkOk) | Xtream($xtOk) | M3U($m3uOk)"
                        },
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        if (isAr) {
                            "تم اكتشاف (${breakdown.allExtracted.size}) حساب، الشغال منها: 0"
                        } else {
                            "Detected ${breakdown.allExtracted.size} accounts, Working: 0"
                        },
                        Toast.LENGTH_LONG
                    ).show()
                }
                isCheckingAll = false
            }
        }
    }

    val m3uFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isCheckingAll = true
                val importedEntity = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(context.filesDir, "m3u_playlists").apply { if (!exists()) mkdirs() }
                        val savedFile = File(dir, "playlist_${System.currentTimeMillis()}.m3u")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            savedFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        val (_, channels) = M3uStreamParser.parseFromUrl(savedFile.absolutePath, 0L)
                        if (channels.isEmpty()) {
                            savedFile.delete()
                            null
                        } else {
                            val rawName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "M3U"
                            PortalEntity(
                                name = SmartMultiParser.shortPortalName(rawName, "M3U"),
                                protocol = "M3U",
                                url = savedFile.absolutePath,
                                statusText = "Active (${channels.size} CH)"
                            )
                        }
                    }.getOrNull()
                }
                isCheckingAll = false
                if (importedEntity != null) {
                    dao.insertPortal(importedEntity)
                    Toast.makeText(
                        context,
                        if (isAr) "تم استيراد ملف M3U كقائمة قنوات واحدة (${importedEntity.statusText})" else "Imported M3U playlist (${importedEntity.statusText})",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        if (isAr) "ملف M3U فارغ أو غير صالح" else "Invalid or empty M3U file",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val qrImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { QrCodeHelper.decodeQrFromImageUri(context, uri) }
                if (!decoded.isNullOrBlank()) {
                    val inserted = importFromQrPayload(dao, decoded)
                    Toast.makeText(
                        context,
                        if (inserted) {
                            if (isAr) "تم استيراد الحساب من QR بنجاح" else "Imported from QR"
                        } else {
                            if (isAr) "رمز QR غير صالح" else "Invalid QR Code"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blockFocusWhen(anyDialogOpen)
                .padding(12.dp)
        ) {
            val focusFirstPortalOrQuick: () -> Boolean = {
                if (displayedPortals.isNotEmpty()) {
                    runCatching { portalRequesters.getOrPut(0) { FocusRequester() }.requestFocus() }.isSuccess
                } else {
                    runCatching { quickRequesters[0].requestFocus() }.isSuccess
                }
            }

            // السطر الأفقي الأول: إضافة حساب / لصق ذكي / المحلل الذكي / ملف M3U / ملف TXT / QR / فحص الكل / حذف متعدد
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .horizontalScroll(quickRowScroll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCheckingAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                ActionChipButton(
                    label = if (isAr) "إضافة حساب" else "Add Account",
                    icon = Icons.Default.Add,
                    focusRequester = quickRequesters[0],
                    stepModifier = Modifier.tvHorizontalStepItem(
                        index = 0, totalCount = 8, requesters = quickRequesters, isAr = isAr,
                        scrollState = quickRowScroll, scope = scope,
                        onUp = { onNavigateToTopBar() },
                        onDown = { runCatching { filterRequesters[0].requestFocus() }.isSuccess }
                    ),
                    onClick = { showAddDialog = true }
                )

                ActionChipButton(
                    label = if (isAr) "لصق ذكي" else "Smart Paste",
                    icon = Icons.Default.ContentPaste,
                    focusRequester = quickRequesters[1],
                    stepModifier = Modifier.tvHorizontalStepItem(
                        index = 1, totalCount = 8, requesters = quickRequesters, isAr = isAr,
                        scrollState = quickRowScroll, scope = scope,
                        onUp = { onNavigateToTopBar() },
                        onDown = { runCatching { filterRequesters[0].requestFocus() }.isSuccess }
                    ),
                    onClick = {
                        smartInitialText = readClipboardText()
                        showSmartParserDialog = true
                    }
                )

                ActionChipButton(
                    label = if (isAr) "المحلل الذكي" else "Smart Parser",
                    icon = Icons.Default.AutoFixHigh,
                    focusRequester = quickRequesters[2],
                    stepModifier = Modifier.tvHorizontalStepItem(
                        index = 2, totalCount = 8, requesters = quickRequesters, isAr = isAr,
                        scrollState = quickRowScroll, scope = scope,
                        onUp = { onNavigateToTopBar() },
                        onDown = { runCatching { filterRequesters[0].requestFocus() }.isSuccess }
                    ),
                    onClick = {
                        smartInitialText = ""
                        showSmartParserDialog = true
                    }
                )

                ActionChipButton(
                    label = if (isAr) "ملف M3U" else "Import M3U",
                    icon = Icons.Default.VideoLibrary,
                    focusRequester = quickRequesters[3],
                    stepModifier = Modifier.tvHorizontalStepItem(
                        index = 3, totalCount = 8, requesters = quickRequesters, isAr = isAr,
                        scrollState = quickRowScroll, scope = scope,
                        onUp = { onNavigateToTopBar() },
                        onDown = { runCatching { filterRequesters[0].requestFocus() }.isSuccess }
                    ),
                    onClick = {
                        val ok = runCatching { com.kadri.stalkerplayerpro.MainActivity.isLaunchingExternalPicker = true; m3uFileLauncher.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "*/*")) }.isSuccess
                        if (!ok) {
                            Toast.makeText(context, if (isAr) "متصفح الملفات غير متوفر" else "File picker unavailable", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ActionChipButton(
                    label = if (isAr) "ملف TXT" else "Import TXT",
                    icon = Icons.Default.FileOpen,
                    focusRequester = quickRequesters[4],
                    stepModifier = Modifier.tvHorizontalStepItem(
                        index = 4, totalCount = 8, requesters = quickRequesters, isAr = isAr,
                        scrollState = quickRowScroll, scope = scope,
                        onUp = { onNavigateToTopBar() },
                        onDown = { runCatching { filterRequesters[0].requestFocus() }.isSuccess }
                    ),
                    onClick = {
                        val ok = runCatching { com.kadri.stalkerplayerpro.MainActivity.isLaunchingExternalPicker = true; txtImportLauncher.launch(arrayOf("text/plain", "*/*")) }.isSuccess
                        if (!ok) {
                            Toast.makeText(context, if (isAr) "استخدم اللصق الذكي" else "Use Smart Paste", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ActionChipButton(
                    label = "QR",
                    icon = Icons.Default.QrCodeScanner,
                    focusRequester = quickRequesters[5],
                    stepModifier = Modifier.tvHorizontalStepItem(
                        index = 5, totalCount = 8, requesters = quickRequesters, isAr = isAr,
                        scrollState = quickRowScroll, scope = scope,
                        onUp = { onNavigateToTopBar() },
                        onDown = { runCatching { filterRequesters[0].requestFocus() }.isSuccess }
                    ),
                    onClick = {
                        val ok = runCatching { com.kadri.stalkerplayerpro.MainActivity.isLaunchingExternalPicker = true; qrImportLauncher.launch(arrayOf("image/*")) }.isSuccess
                        if (!ok) {
                            Toast.makeText(context, if (isAr) "متصفح الصور غير متوفر" else "Gallery unavailable", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ActionChipButton(
                    label = if (isAr) "فحص الكل" else "Check All",
                    icon = Icons.Default.CheckCircle,
                    focusRequester = quickRequesters[6],
                    stepModifier = Modifier.tvHorizontalStepItem(
                        index = 6, totalCount = 8, requesters = quickRequesters, isAr = isAr,
                        scrollState = quickRowScroll, scope = scope,
                        onUp = { onNavigateToTopBar() },
                        onDown = { runCatching { filterRequesters[0].requestFocus() }.isSuccess }
                    ),
                    onClick = {
                        if (!isCheckingAll && portals.isNotEmpty()) {
                            scope.launch {
                                isCheckingAll = true
                                portals.forEach { p ->
                                    val updated = refreshPortalStatus(p)
                                    dao.updatePortal(updated)
                                }
                                isCheckingAll = false
                            }
                        }
                    }
                )

                ActionChipButton(
                    label = if (multiSelectMode) {
                        if (isAr) "حذف المحدد (${selectedIds.size})" else "Delete (${selectedIds.size})"
                    } else {
                        if (isAr) "حذف متعدد" else "Multi-Delete"
                    },
                    icon = Icons.Default.Delete,
                    focusRequester = quickRequesters[7],
                    stepModifier = Modifier.tvHorizontalStepItem(
                        index = 7, totalCount = 8, requesters = quickRequesters, isAr = isAr,
                        scrollState = quickRowScroll, scope = scope,
                        onUp = { onNavigateToTopBar() },
                        onDown = { runCatching { filterRequesters[0].requestFocus() }.isSuccess },
                        onExitEnd = { runCatching { filterRequesters[0].requestFocus() }.isSuccess }
                    ),
                    isDestructive = multiSelectMode && selectedIds.isNotEmpty(),
                    onClick = {
                        if (!multiSelectMode) {
                            multiSelectMode = true
                            selectedIds = emptySet()
                        } else {
                            if (selectedIds.isNotEmpty()) {
                                val builtInIds = portals.filter { it.url == BUILTIN_FREE_AR_M3U_URL }.map { it.id }.toSet()
                                val toDelete = selectedIds.filterNot { it in builtInIds }
                                if (toDelete.isNotEmpty()) {
                                    scope.launch { dao.deletePortalsByIds(toDelete) }
                                }
                            }
                            multiSelectMode = false
                            selectedIds = emptySet()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // السطر الأفقي الثاني: تصنيفات السيرفرات (الكل / Stalker / Xtream / M3U)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .horizontalScroll(filterRowScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterTabs = listOf(
                    "ALL" to (if (isAr) "الكل (${portals.size})" else "All (${portals.size})"),
                    "STALKER" to "Stalker (${stalkerList.size})",
                    "XTREAM" to "Xtream (${xtreamList.size})",
                    "M3U" to "M3U (${m3uList.size})"
                )
                filterTabs.forEachIndexed { fIdx, (code, title) ->
                    val active = selectedProtocolFilter == code
                    Box(
                        modifier = Modifier
                            .tvHorizontalStepItem(
                                index = fIdx,
                                totalCount = 4,
                                requesters = filterRequesters,
                                isAr = isAr,
                                scrollState = filterRowScroll,
                                scope = scope,
                                onExitStart = { runCatching { quickRequesters[7].requestFocus() }.isSuccess },
                                onUp = { runCatching { quickRequesters[0].requestFocus() }.isSuccess },
                                onDown = focusFirstPortalOrQuick,
                                onExitEnd = focusFirstPortalOrQuick
                            )
                            .tvFocusItem(
                                focusRequester = filterRequesters[fIdx],
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBackgroundColor = if (active) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                onClick = { selectedProtocolFilter = code }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (displayedPortals.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isAr) "لا توجد حسابات في هذا القسم" else "No accounts in this category",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionChipButton(
                            label = if (isAr) "إضافة حساب يدوي" else "Add Account",
                            icon = Icons.Default.Add,
                            onClick = { showAddDialog = true }
                        )
                        ActionChipButton(
                            label = if (isAr) "لصق ذكي من الحافظة" else "Smart Paste",
                            icon = Icons.Default.ContentPaste,
                            onClick = {
                                smartInitialText = readClipboardText()
                                showSmartParserDialog = true
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = displayedPortals,
                        key = { idx, portal -> "portal_row_${portal.id}_$idx" }
                    ) { index, portal ->
                        val isSelected = portal.id in selectedIds
                        val isBuiltInFree = portal.url == BUILTIN_FREE_AR_M3U_URL
                        val rowCardRequester = portalRequesters.getOrPut(index) { FocusRequester() }
                        val rowStepMod = Modifier.tvVerticalStepItem(
                            index = index,
                            totalCount = displayedPortals.size,
                            listState = listState,
                            requesters = portalRequesters,
                            scope = scope,
                            onExitTop = { runCatching { filterRequesters[0].requestFocus() }.isSuccess }
                        )
                        PortalCardItem(
                            portal = portal,
                            isBuiltInFree = isBuiltInFree,
                            isAr = isAr,
                            isFirst = index == 0,
                            isLast = index == displayedPortals.lastIndex,
                            multiSelectMode = multiSelectMode,
                            isSelected = isSelected,
                            cardFocusRequester = rowCardRequester,
                            verticalStepModifier = rowStepMod,
                            onFocused = { onPortalFocused(portal.id) },
                            onActivate = {
                                if (multiSelectMode) {
                                    if (!isBuiltInFree) {
                                        selectedIds = if (isSelected) selectedIds - portal.id else selectedIds + portal.id
                                    }
                                } else {
                                    scope.launch {
                                        dao.setActivePortal(portal.id)
                                        onPortalActivated(portal.copy(isActive = true))
                                    }
                                }
                            },
                            onToggleFavorite = {
                                scope.launch { dao.setPortalFavorite(portal.id, !portal.isFavorite) }
                            },
                            onMoveUp = {
                                scope.launch { dao.movePortal(portal.id, moveUp = true) }
                            },
                            onMoveDown = {
                                scope.launch { dao.movePortal(portal.id, moveUp = false) }
                            },
                            onOpenMenu = { selectedForMenu = portal }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            PortalFormDialog(
                title = if (isAr) "إضافة حساب جديد" else "Add New Account",
                initialPortal = null,
                isAr = isAr,
                onSave = { newPortal ->
                    scope.launch {
                        val checked = refreshPortalStatus(newPortal)
                        dao.insertPortal(checked)
                    }
                },
                onDismiss = { showAddDialog = false }
            )
        }

        selectedForEdit?.let { editingPortal ->
            PortalFormDialog(
                title = if (isAr) "تعديل بيانات البوابة" else "Edit Portal Details",
                initialPortal = editingPortal,
                isAr = isAr,
                onSave = { updatedPortal ->
                    scope.launch {
                        val checked = refreshPortalStatus(updatedPortal.copy(id = editingPortal.id))
                        dao.updatePortal(checked)
                        Toast.makeText(
                            context,
                            if (isAr) "تم تحديث بيانات البوابة بنجاح" else "Portal updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onDismiss = { selectedForEdit = null }
            )
        }

        if (showSmartParserDialog) {
            SmartParserDialog(
                initialText = smartInitialText,
                isAr = isAr,
                onImportWorking = { workingList ->
                    scope.launch {
                        dao.insertPortals(workingList)
                    }
                },
                onDismiss = { showSmartParserDialog = false }
            )
        }

        selectedForMenu?.let { portal ->
            val isBuiltInFree = portal.url == BUILTIN_FREE_AR_M3U_URL
            val opts = buildList {
                add(
                    TvMenuOption(
                        id = "fav",
                        label = if (portal.isFavorite) {
                            if (isAr) "إزالة من المفضلة" else "Unfavorite Account"
                        } else {
                            if (isAr) "تفضيل الحساب ★" else "Favorite Account ★"
                        },
                        icon = if (portal.isFavorite) Icons.Default.StarOutline else Icons.Default.Star
                    )
                )
                add(
                    TvMenuOption(
                        id = "up",
                        label = if (isAr) "تحريك للأعلى ▲" else "Move Up ▲",
                        icon = Icons.Default.ArrowUpward
                    )
                )
                add(
                    TvMenuOption(
                        id = "down",
                        label = if (isAr) "تحريك للأسفل ▼" else "Move Down ▼",
                        icon = Icons.Default.ArrowDownward
                    )
                )
                add(
                    TvMenuOption(
                        id = "qr",
                        label = if (isAr) "عرض رمز QR" else "Show QR Code",
                        icon = Icons.Default.QrCode
                    )
                )
                if (!isBuiltInFree) {
                    add(
                        TvMenuOption(
                            id = "edit",
                            label = if (isAr) "تعديل البوابة (الرابط / MAC / البيانات)" else "Edit Portal Details",
                            icon = Icons.Default.SettingsApplications
                        )
                    )
                    add(
                        TvMenuOption(
                            id = "rename",
                            label = if (isAr) "إعادة تسمية (حتى 10 حروف)" else "Rename (up to 10 chars)",
                            icon = Icons.Default.Edit
                        )
                    )
                    add(
                        TvMenuOption(
                            id = "delete",
                            label = if (isAr) "حذف الحساب" else "Delete Account",
                            icon = Icons.Default.Delete,
                            isDestructive = true
                        )
                    )
                }
            }
            TvActionMenuDialog(
                title = portal.name.take(10),
                options = opts,
                onSelect = { opt ->
                    when (opt.id) {
                        "fav" -> scope.launch { dao.setPortalFavorite(portal.id, !portal.isFavorite) }
                        "up" -> scope.launch { dao.movePortal(portal.id, moveUp = true) }
                        "down" -> scope.launch { dao.movePortal(portal.id, moveUp = false) }
                        "qr" -> selectedForQr = portal
                        "edit" -> selectedForEdit = portal
                        "rename" -> selectedForRename = portal
                        "delete" -> if (!isBuiltInFree) {
                            scope.launch { dao.deletePortalsByIds(listOf(portal.id)) }
                        }
                    }
                },
                onDismiss = { selectedForMenu = null }
            )
        }

        selectedForQr?.let { portal ->
            PortalQrDialog(portal = portal, isAr = isAr, onDismiss = { selectedForQr = null })
        }

        selectedForRename?.let { portal ->
            TvTextInputDialog(
                title = if (isAr) "إعادة تسمية البوابة (حتى 10 حروف)" else "Rename Portal (Up to 10 chars)",
                initialValue = portal.name.take(10),
                confirmLabel = if (isAr) "حفظ" else "Save",
                onConfirm = { newName ->
                    val shortName = SmartMultiParser.shortPortalName(newName, portal.name)
                    scope.launch { dao.updatePortal(portal.copy(name = shortName)) }
                },
                onDismiss = { selectedForRename = null }
            )
        }
    }
}

@Composable
private fun PortalCardItem(
    portal: PortalEntity,
    isBuiltInFree: Boolean,
    isAr: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    cardFocusRequester: FocusRequester? = null,
    verticalStepModifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onActivate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val extraColors = LocalTvExtraColors.current
    val bg = when {
        portal.isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isBuiltInFree -> Color(0xFFFFB300).copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surface
    }

    val expiryLabel = when {
        isBuiltInFree -> {
            if (isAr) "قنوات عربية مفتوحة ومجانية (محدثة باستمرار)" else "Free Arabic FTA Channels (Auto-Updated)"
        }
        portal.expiryTimestampMs > 0L -> {
            val dateStr = LocaleUtils.formatDate(portal.expiryTimestampMs)
            val daysLeft = ((portal.expiryTimestampMs - System.currentTimeMillis()) / 86_400_000L).toInt()
            val daysSuffix = when {
                daysLeft > 0 -> if (isAr) "(${LocaleUtils.formatInt(daysLeft)} يوم)" else "(${LocaleUtils.formatInt(daysLeft)} days)"
                daysLeft == 0 -> if (isAr) "(ينتهي اليوم)" else "(Expires today)"
                else -> if (isAr) "(منتهي الصلاحية)" else "(Expired)"
            }
            if (isAr) "تاريخ الصلاحية: $dateStr $daysSuffix" else "Expiry: $dateStr $daysSuffix"
        }
        else -> {
            if (isAr) "تاريخ الصلاحية: غير محدد" else "Expiry: Unlimited / N/A"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .then(
                if (isBuiltInFree) {
                    Modifier.border(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                } else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(verticalStepModifier)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .tvFocusItem(
                    focusRequester = cardFocusRequester,
                    shape = RoundedCornerShape(10.dp),
                    onLongClick = onOpenMenu,
                    onMenuClick = onOpenMenu,
                    onClick = onActivate
                )
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (multiSelectMode) {
                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked
                } else {
                    if (portal.isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (portal.isActive || isSelected) extraColors.badgeSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBuiltInFree) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    } else if (portal.isFavorite) {
                        Text(
                            text = "★ ",
                            color = Color(0xFFFFD600),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    LtrText(
                        text = portal.name.take(10),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    LtrText(
                        text = if (isBuiltInFree) "[FREE-AR]" else "[${portal.protocol}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBuiltInFree) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary
                    )
                    if (portal.statusText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        LtrText(
                            text = portal.statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (portal.statusText.startsWith("Active", true) || portal.statusText.equals("Ready", true)) {
                                extraColors.badgeSuccess
                            } else {
                                extraColors.badgeWarning
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = expiryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isBuiltInFree -> Color(0xFFFFD54F)
                        portal.expiryTimestampMs > System.currentTimeMillis() -> extraColors.badgeSuccess
                        portal.expiryTimestampMs > 0L -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        SmallIconTvButton(
            icon = if (portal.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
            tint = if (portal.isFavorite) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onToggleFavorite
        )

        SmallIconTvButton(
            icon = Icons.Default.ArrowUpward,
            enabled = !isFirst,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onMoveUp
        )

        SmallIconTvButton(
            icon = Icons.Default.ArrowDownward,
            enabled = !isLast,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onMoveDown
        )

        SmallIconTvButton(
            icon = Icons.Default.MoreVert,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onOpenMenu
        )
    }
}

@Composable
private fun SmallIconTvButton(
    icon: ImageVector,
    tint: Color,
    enabled: Boolean = true,
    stepModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .then(stepModifier)
            .tvFocusItem(
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.25f),
                onClick = onClick
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ActionChipButton(
    label: String,
    icon: ImageVector,
    focusRequester: FocusRequester? = null,
    stepModifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .then(stepModifier)
            .tvFocusItem(
                focusRequester = focusRequester,
                shape = RoundedCornerShape(10.dp),
                unfocusedBackgroundColor = bg,
                onClick = onClick
            )
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = fg, softWrap = false)
    }
}

@Composable
private fun PortalFormDialog(
    title: String,
    initialPortal: PortalEntity?,
    isAr: Boolean,
    onSave: (PortalEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val initialProto = remember(initialPortal) {
        ProtocolType.entries.find { it.code == initialPortal?.protocol } ?: ProtocolType.STALKER
    }
    var selectedProtocol by remember { mutableStateOf(initialProto) }
    var name by remember { mutableStateOf(initialPortal?.name.orEmpty()) }
    var url by remember { mutableStateOf(initialPortal?.url ?: "http://") }
    var mac by remember { mutableStateOf(initialPortal?.mac?.ifBlank { "00:1A:79:" } ?: "00:1A:79:") }
    var username by remember { mutableStateOf(initialPortal?.username.orEmpty()) }
    var password by remember { mutableStateOf(initialPortal?.password.orEmpty()) }

    TvModalDialog(
        title = title,
        onDismiss = onDismiss,
        maxWidth = 560.dp,
        scrollableBody = true,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isAr) "إلغاء" else "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                if (url.isNotBlank() && url != "http://") {
                                    val shortName = SmartMultiParser.shortPortalName(
                                        name.ifBlank { url },
                                        selectedProtocol.code
                                    )
                                    val base = initialPortal ?: PortalEntity(
                                        name = shortName,
                                        protocol = selectedProtocol.code,
                                        url = url.trim()
                                    )
                                    onSave(
                                        base.copy(
                                            name = shortName,
                                            protocol = selectedProtocol.code,
                                            url = url.trim(),
                                            mac = mac.trim().uppercase(),
                                            username = username.trim(),
                                            password = password.trim()
                                        )
                                    )
                                    onDismiss()
                                }
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isAr) "حفظ وفحص" else "Save & Check",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { firstRequester ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtocolType.entries.forEachIndexed { idx, proto ->
                val active = proto == selectedProtocol
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
                            focusRequester = if (idx == 0) firstRequester else null,
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedProtocol = proto }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LtrText(
                        text = proto.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(if (isAr) "اسم البوابة (حتى 10 حروف)" else "Portal Name (Up to 10 chars)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        when (selectedProtocol) {
            ProtocolType.STALKER -> {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("MAC Address (00:1A:79:XX:XX:XX)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ProtocolType.XTREAM -> {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            ProtocolType.M3U -> Unit
        }
    }
}

@Composable
private fun SmartParserDialog(
    initialText: String,
    isAr: Boolean,
    onImportWorking: (List<PortalEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf(initialText) }
    var isScanning by remember { mutableStateOf(false) }
    var resultSummary by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val previewBreakdown = remember(rawText) { SmartMultiParser.analyzeText(rawText) }

    TvModalDialog(
        title = if (isAr) "المحلل واللصق الذكي (Stalker / Xtream / M3U)" else "Smart Multi-Parser & Paste",
        onDismiss = onDismiss,
        maxWidth = 640.dp,
        scrollableBody = true,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isAr) "إغلاق" else "Close",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            enabled = !isScanning,
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                scope.launch {
                                    isScanning = true
                                    val breakdown = SmartMultiParser.analyzeText(rawText)
                                    val working = SmartMultiParser.filterWorkingAccounts(breakdown.allExtracted)
                                    isScanning = false
                                    if (working.isNotEmpty()) {
                                        val stkOk = working.count { it.protocol == "STALKER" }
                                        val xtOk = working.count { it.protocol == "XTREAM" }
                                        val m3uOk = working.count { it.protocol == "M3U" }
                                        Toast.makeText(
                                            context,
                                            if (isAr) {
                                                "تم حفظ ${working.size} شغال: Stalker($stkOk) | Xtream($xtOk) | M3U($m3uOk)"
                                            } else {
                                                "Saved ${working.size} working: Stalker($stkOk) | Xtream($xtOk) | M3U($m3uOk)"
                                            },
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onImportWorking(working)
                                        onDismiss()
                                    } else {
                                        resultSummary = if (isAr) {
                                            "المكتشف: Stalker(${breakdown.stalkerCount}), Xtream(${breakdown.xtreamCount}), M3U(${breakdown.m3uCount}) — الشغال: 0"
                                        } else {
                                            "Detected: Stalker(${breakdown.stalkerCount}), Xtream(${breakdown.xtreamCount}), M3U(${breakdown.m3uCount}) — Working: 0"
                                        }
                                    }
                                }
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isAr) "فحص وحفظ الشغال" else "Verify & Save Working",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { firstRequester ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LtrText(
                text = "Stalker: ${previewBreakdown.stalkerCount}  |  Xtream: ${previewBreakdown.xtreamCount}  |  M3U: ${previewBreakdown.m3uCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(
                modifier = Modifier
                    .tvFocusItem(
                        focusRequester = firstRequester,
                        shape = RoundedCornerShape(8.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (clip.isNotBlank()) {
                                rawText = clip
                            } else {
                                Toast.makeText(context, if (isAr) "الحافظة فارغة" else "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ContentPaste, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAr) "لصق من الحافظة" else "Paste Clipboard",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            placeholder = { Text("Stalker (URL + MACs) / Xtream (live/user/pass or get.php) / M3U...") }
        )

        if (resultSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = resultSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private suspend fun refreshPortalStatus(portal: PortalEntity): PortalEntity {
    if (portal.url == BUILTIN_FREE_AR_M3U_URL) {
        return portal.copy(statusText = "Active (Free AR)")
    }
    return when (portal.protocol) {
        "STALKER" -> {
            val (valid, expMs) = StalkerApiClient.checkPortalAccount(portal.url, portal.mac)
            portal.copy(
                expiryTimestampMs = expMs,
                statusText = if (valid) "Active" else "Offline"
            )
        }
        "XTREAM" -> {
            val st = XtreamApiClient.checkAccount(portal.url, portal.username, portal.password)
            portal.copy(
                expiryTimestampMs = st.expiryTimestampMs,
                statusText = st.statusText
            )
        }
        else -> portal.copy(statusText = "Ready")
    }
}

private suspend fun importFromQrPayload(dao: AppDao, payload: String): Boolean {
    return runCatching {
        val trimmed = payload.trim()
        if (trimmed.startsWith("{")) {
            val json = JSONObject(trimmed)
            val rawUrl = json.optString("url")
            val rawName = json.optString("name").ifBlank { rawUrl }
            val entity = PortalEntity(
                name = SmartMultiParser.shortPortalName(rawName, "QR"),
                protocol = json.optString("protocol", "STALKER"),
                url = rawUrl,
                mac = json.optString("mac"),
                username = json.optString("username"),
                password = json.optString("password")
            )
            if (entity.url.isNotBlank()) {
                dao.insertPortal(refreshPortalStatus(entity))
                true
            } else false
        } else {
            val extracted = SmartMultiParser.extractPortalsFromText(trimmed)
            if (extracted.isNotEmpty()) {
                dao.insertPortals(extracted)
                true
            } else false
        }
    }.getOrDefault(false)
}
