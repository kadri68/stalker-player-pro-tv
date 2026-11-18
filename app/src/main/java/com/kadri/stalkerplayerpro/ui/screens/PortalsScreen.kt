package com.kadri.stalkerplayerpro.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kadri.stalkerplayerpro.core.theme.LocalTvExtraColors
import com.kadri.stalkerplayerpro.core.tv.LocaleUtils
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.blockFocusWhen
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import com.kadri.stalkerplayerpro.data.model.ProtocolType
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun PortalsScreen(
    dao: AppDao,
    isAr: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() },
    onPortalActivated: (PortalEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val portals by dao.observePortals().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var showSmartParserDialog by remember { mutableStateOf(false) }
    var smartInitialText by remember { mutableStateOf("") }
    var selectedForMenu by remember { mutableStateOf<PortalEntity?>(null) }
    var selectedForQr by remember { mutableStateOf<PortalEntity?>(null) }
    var selectedForRename by remember { mutableStateOf<PortalEntity?>(null) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isCheckingAll by remember { mutableStateOf(false) }

    val anyDialogOpen = showAddDialog || showSmartParserDialog || selectedForMenu != null ||
            selectedForQr != null || selectedForRename != null

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
                    if (portals.isEmpty()) {
                        dao.getAllPortals().firstOrNull()?.let { dao.setActivePortal(it.id) }
                    }
                    val stkOk = working.count { it.protocol == "STALKER" }
                    val xtOk = working.count { it.protocol == "XTREAM" }
                    val m3uOk = working.count { it.protocol == "M3U" }
                    Toast.makeText(
                        context,
                        if (isAr) {
                            "تم استيراد ${working.size} شغال (Stalker: $stkOk | Xtream: $xtOk | M3U: $m3uOk)"
                        } else {
                            "Imported ${working.size} working (Stalker: $stkOk | Xtream: $xtOk | M3U: $m3uOk)"
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
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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
                    focusRequester = initialContentFocusRequester,
                    onClick = { showAddDialog = true }
                )

                ActionChipButton(
                    label = if (isAr) "لصق ذكي" else "Smart Paste",
                    icon = Icons.Default.ContentPaste,
                    onClick = {
                        smartInitialText = readClipboardText()
                        showSmartParserDialog = true
                    }
                )

                ActionChipButton(
                    label = if (isAr) "المحلل الذكي" else "Smart Parser",
                    icon = Icons.Default.AutoFixHigh,
                    onClick = {
                        smartInitialText = ""
                        showSmartParserDialog = true
                    }
                )

                ActionChipButton(
                    label = if (isAr) "ملف TXT" else "Import TXT",
                    icon = Icons.Default.FileOpen,
                    onClick = {
                        val ok = runCatching { txtImportLauncher.launch(arrayOf("text/plain", "*/*")) }.isSuccess
                        if (!ok) {
                            Toast.makeText(context, if (isAr) "استخدم اللصق الذكي" else "Use Smart Paste", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ActionChipButton(
                    label = "QR",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = {
                        val ok = runCatching { qrImportLauncher.launch(arrayOf("image/*")) }.isSuccess
                        if (!ok) {
                            Toast.makeText(context, if (isAr) "متصفح الصور غير متوفر" else "Gallery unavailable", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ActionChipButton(
                    label = if (isAr) "فحص الكل" else "Check All",
                    icon = Icons.Default.CheckCircle,
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
                    isDestructive = multiSelectMode && selectedIds.isNotEmpty(),
                    onClick = {
                        if (!multiSelectMode) {
                            multiSelectMode = true
                            selectedIds = emptySet()
                        } else {
                            if (selectedIds.isNotEmpty()) {
                                val toDelete = selectedIds.toList()
                                scope.launch { dao.deletePortalsByIds(toDelete) }
                            }
                            multiSelectMode = false
                            selectedIds = emptySet()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (portals.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isAr) "لا توجد حسابات مضافة بعد" else "No accounts added yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = portals,
                        key = { idx, portal -> "portal_row_${portal.id}_$idx" }
                    ) { index, portal ->
                        val isSelected = portal.id in selectedIds
                        PortalCardItem(
                            portal = portal,
                            isFirst = index == 0,
                            isLast = index == portals.lastIndex,
                            multiSelectMode = multiSelectMode,
                            isSelected = isSelected,
                            onActivate = {
                                if (multiSelectMode) {
                                    selectedIds = if (isSelected) selectedIds - portal.id else selectedIds + portal.id
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
            AddPortalDialog(
                isAr = isAr,
                onSave = { newPortal ->
                    scope.launch {
                        val checked = refreshPortalStatus(newPortal)
                        val newId = dao.insertPortal(checked)
                        if (portals.isEmpty()) {
                            dao.setActivePortal(newId)
                        }
                    }
                },
                onDismiss = { showAddDialog = false }
            )
        }

        if (showSmartParserDialog) {
            SmartParserDialog(
                initialText = smartInitialText,
                isAr = isAr,
                onImportWorking = { workingList ->
                    scope.launch {
                        dao.insertPortals(workingList)
                        if (portals.isEmpty()) {
                            dao.getAllPortals().firstOrNull()?.let { dao.setActivePortal(it.id) }
                        }
                    }
                },
                onDismiss = { showSmartParserDialog = false }
            )
        }

        selectedForMenu?.let { portal ->
            val opts = listOf(
                TvMenuOption(
                    id = "fav",
                    label = if (portal.isFavorite) {
                        if (isAr) "إزالة من المفضلة" else "Unfavorite Account"
                    } else {
                        if (isAr) "تفضيل الحساب ★" else "Favorite Account ★"
                    },
                    icon = if (portal.isFavorite) Icons.Default.StarOutline else Icons.Default.Star
                ),
                TvMenuOption(
                    id = "up",
                    label = if (isAr) "تحريك للأعلى ▲" else "Move Up ▲",
                    icon = Icons.Default.ArrowUpward
                ),
                TvMenuOption(
                    id = "down",
                    label = if (isAr) "تحريك للأسفل ▼" else "Move Down ▼",
                    icon = Icons.Default.ArrowDownward
                ),
                TvMenuOption(
                    id = "qr",
                    label = if (isAr) "عرض رمز QR" else "Show QR Code",
                    icon = Icons.Default.QrCode
                ),
                TvMenuOption(
                    id = "rename",
                    label = if (isAr) "إعادة تسمية" else "Rename",
                    icon = Icons.Default.Edit
                ),
                TvMenuOption(
                    id = "delete",
                    label = if (isAr) "حذف الحساب" else "Delete Account",
                    icon = Icons.Default.Delete,
                    isDestructive = true
                )
            )
            TvActionMenuDialog(
                title = portal.name,
                options = opts,
                onSelect = { opt ->
                    when (opt.id) {
                        "fav" -> scope.launch { dao.setPortalFavorite(portal.id, !portal.isFavorite) }
                        "up" -> scope.launch { dao.movePortal(portal.id, moveUp = true) }
                        "down" -> scope.launch { dao.movePortal(portal.id, moveUp = false) }
                        "qr" -> selectedForQr = portal
                        "rename" -> selectedForRename = portal
                        "delete" -> scope.launch { dao.deletePortalsByIds(listOf(portal.id)) }
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
                title = if (isAr) "تعديل اسم الحساب" else "Rename Account",
                initialValue = portal.name,
                confirmLabel = if (isAr) "حفظ" else "Save",
                onConfirm = { newName ->
                    scope.launch { dao.updatePortal(portal.copy(name = newName)) }
                },
                onDismiss = { selectedForRename = null }
            )
        }
    }
}

@Composable
private fun PortalCardItem(
    portal: PortalEntity,
    isFirst: Boolean,
    isLast: Boolean,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    onActivate: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val extraColors = LocalTvExtraColors.current
    val bg = if (portal.isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .tvFocusItem(
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
                    if (portal.isFavorite) {
                        Text(
                            text = "★ ",
                            color = Color(0xFFFFD600),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = portal.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LtrText(
                        text = "[${portal.protocol}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LtrText(
                        text = portal.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (portal.mac.isNotBlank()) {
                        LtrText(
                            text = portal.mac,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else if (portal.username.isNotBlank()) {
                        LtrText(
                            text = "U: ${portal.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (portal.expiryTimestampMs > 0L) {
                        val expStr = LocaleUtils.formatDate(portal.expiryTimestampMs)
                        LtrText(
                            text = "EXP: $expStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = extraColors.badgeSuccess
                        )
                    }
                }
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
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
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
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
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
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun AddPortalDialog(
    isAr: Boolean,
    onSave: (PortalEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProtocol by remember { mutableStateOf(ProtocolType.STALKER) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("http://") }
    var mac by remember { mutableStateOf("00:1A:79:") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    TvModalDialog(
        title = if (isAr) "إضافة حساب جديد" else "Add New Account",
        onDismiss = onDismiss,
        maxWidth = 560.dp
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
            label = { Text(if (isAr) "اسم الحساب (اختياري)" else "Account Name") },
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

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .tvFocusItem(
                        shape = RoundedCornerShape(10.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            if (url.isNotBlank() && url != "http://") {
                                val finalName = name.ifBlank { "${selectedProtocol.label} Server" }
                                onSave(
                                    PortalEntity(
                                        name = finalName.trim(),
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
                    .padding(horizontal = 22.dp, vertical = 9.dp)
            ) {
                Text(
                    text = if (isAr) "حفظ وفحص" else "Save & Check",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
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
        maxWidth = 640.dp
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
                .height(120.dp),
            placeholder = { Text("Stalker (URL + MAC) / Xtream (Host, User, Pass) / M3U...") }
        )

        if (resultSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = resultSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Box(
                modifier = Modifier
                    .tvFocusItem(
                        focusRequester = firstRequester,
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
                                            "تمت إضافة ${working.size} شغال (Stalker: $stkOk | Xtream: $xtOk | M3U: $m3uOk)"
                                        } else {
                                            "Added ${working.size} working (Stalker: $stkOk | Xtream: $xtOk | M3U: $m3uOk)"
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
                    text = if (isAr) "فحص وإضافة الحسابات الشغالة" else "Verify & Import Working",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

private suspend fun refreshPortalStatus(portal: PortalEntity): PortalEntity {
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
            val entity = PortalEntity(
                name = json.optString("name", "QR Account"),
                protocol = json.optString("protocol", "STALKER"),
                url = json.optString("url"),
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
