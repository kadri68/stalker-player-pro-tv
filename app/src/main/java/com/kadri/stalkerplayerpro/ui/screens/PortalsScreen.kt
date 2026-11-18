package com.kadri.stalkerplayerpro.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kadri.stalkerplayerpro.core.theme.LocalTvExtraColors
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
    onPortalActivated: (PortalEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extraColors = LocalTvExtraColors.current
    val portals by dao.observePortals().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var showSmartParserDialog by remember { mutableStateOf(false) }
    var selectedForMenu by remember { mutableStateOf<PortalEntity?>(null) }
    var selectedForQr by remember { mutableStateOf<PortalEntity?>(null) }
    var selectedForRename by remember { mutableStateOf<PortalEntity?>(null) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isCheckingAll by remember { mutableStateOf(false) }

    val anyDialogOpen = showAddDialog || showSmartParserDialog || selectedForMenu != null ||
            selectedForQr != null || selectedForRename != null

    // استيراد ملف .txt للمحلل الذكي
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
                val parsed = SmartMultiParser.extractPortalsFromText(rawText)
                val working = SmartMultiParser.filterWorkingAccounts(parsed)
                if (working.isNotEmpty()) {
                    dao.insertPortals(working)
                    Toast.makeText(
                        context,
                        if (isAr) "تم استيراد ${working.size} حساب شغال" else "Imported ${working.size} working accounts",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        if (isAr) "لم يتم العثور على حسابات شغالة في الملف" else "No working accounts found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                isCheckingAll = false
            }
        }
    }

    // استيراد حساب من صورة QR Code
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
                .padding(18.dp)
        ) {
            // شريط الأدوات العلوي لإدارة البوابات
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isAr) "إدارة البوابات والحسابات" else "Portals & Accounts",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                if (isCheckingAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // إضافة يدوية
                ActionChipButton(
                    label = if (isAr) "إضافة حساب" else "Add Account",
                    icon = Icons.Default.Add,
                    onClick = { showAddDialog = true }
                )

                // المحلل الذكي (نص)
                ActionChipButton(
                    label = if (isAr) "المحلل الذكي" else "Smart Parser",
                    icon = Icons.Default.AutoFixHigh,
                    onClick = { showSmartParserDialog = true }
                )

                // استيراد ملف TXT
                ActionChipButton(
                    label = if (isAr) "ملف TXT" else "Import TXT",
                    icon = Icons.Default.FileOpen,
                    onClick = { txtImportLauncher.launch(arrayOf("text/plain", "*/*")) }
                )

                // استيراد QR
                ActionChipButton(
                    label = "QR",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = { qrImportLauncher.launch(arrayOf("image/*")) }
                )

                // فحص صلاحية الكل
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

                // تحديد متعدد للحذف
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

            Spacer(modifier = Modifier.height(14.dp))

            if (portals.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isAr) {
                            "لا توجد حسابات مضافة بعد. اضغط على «إضافة حساب» أو «المحلل الذكي» للبدء."
                        } else {
                            "No accounts added yet. Select 'Add Account' or 'Smart Parser' to start."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = portals,
                        key = { idx, portal -> "portal_row_${portal.id}_$idx" }
                    ) { index, portal ->
                        val isSelected = portal.id in selectedIds
                        PortalCardItem(
                            portal = portal,
                            isAr = isAr,
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

        // نافذة إضافة حساب يدوي
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

        // نافذة المحلل الذكي متعدد الصيغ
        if (showSmartParserDialog) {
            SmartParserDialog(
                isAr = isAr,
                onImportWorking = { workingList ->
                    scope.launch {
                        dao.insertPortals(workingList)
                    }
                },
                onDismiss = { showSmartParserDialog = false }
            )
        }

        // قائمة خيارات الحساب (بالضغط المطوّل أو MENU)
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
    isAr: Boolean,
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // زر التفعيل الرئيسي للبطاقة
        Row(
            modifier = Modifier
                .weight(1f)
                .tvFocusItem(
                    shape = RoundedCornerShape(10.dp),
                    onLongClick = onOpenMenu,
                    onMenuClick = onOpenMenu,
                    onClick = onActivate
                )
                .padding(8.dp),
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
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (portal.isFavorite) {
                        Text(
                            text = "★ ",
                            color = Color(0xFFFFD600),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = portal.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    LtrText(
                        text = "[${portal.protocol}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    }
                    if (portal.expiryTimestampMs > 0L) {
                        val expStr = com.kadri.stalkerplayerpro.core.tv.LocaleUtils.formatDate(portal.expiryTimestampMs)
                        LtrText(
                            text = "EXP: $expStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = extraColors.badgeSuccess
                        )
                    }
                }
            }
        }

        // زر التفضيل المباشر ★
        SmallIconTvButton(
            icon = if (portal.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
            tint = if (portal.isFavorite) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onToggleFavorite
        )

        // زر التحريك للأعلى ▲
        SmallIconTvButton(
            icon = Icons.Default.ArrowUpward,
            enabled = !isFirst,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onMoveUp
        )

        // زر التحريك للأسفل ▼
        SmallIconTvButton(
            icon = Icons.Default.ArrowDownward,
            enabled = !isLast,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onMoveDown
        )

        // زر الخيارات
        SmallIconTvButton(
            icon = Icons.Default.MoreVert,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = onOpenMenu
        )
    }
}

@Composable
private fun SmallIconTvButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ActionChipButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .tvFocusItem(
                shape = RoundedCornerShape(10.dp),
                unfocusedBackgroundColor = bg,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
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
        maxWidth = 580.dp
    ) { firstRequester ->
        // اختيار البروتوكول
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        .padding(vertical = 10.dp),
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

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(if (isAr) "اسم الحساب (اختياري)" else "Account Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        when (selectedProtocol) {
            ProtocolType.STALKER -> {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("MAC Address (00:1A:79:XX:XX:XX)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ProtocolType.XTREAM -> {
                Spacer(modifier = Modifier.height(8.dp))
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

        Spacer(modifier = Modifier.height(14.dp))
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
                    .padding(horizontal = 24.dp, vertical = 10.dp)
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
    isAr: Boolean,
    onImportWorking: (List<PortalEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var resultSummary by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    TvModalDialog(
        title = if (isAr) "المحلل الذكي متعدد الصيغ" else "Smart Multi-Parser",
        onDismiss = onDismiss,
        maxWidth = 620.dp
    ) { firstRequester ->
        Text(
            text = if (isAr) {
                "الصق هنا أي نص يحتوي على روابط Stalker + MAC أو روابط Xtream أو M3U وسيتم استخراجها وفحص الشغال منها تلقائياً:"
            } else {
                "Paste mixed text containing Stalker + MAC, Xtream URLs, or M3U links to auto-extract and verify working accounts:"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = { Text("http://portal.example.com/c/  00:1A:79:XX:XX:XX ...") }
        )

        if (resultSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = resultSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
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
                                val extracted = SmartMultiParser.extractPortalsFromText(rawText)
                                val working = SmartMultiParser.filterWorkingAccounts(extracted)
                                isScanning = false
                                if (working.isNotEmpty()) {
                                    onImportWorking(working)
                                    onDismiss()
                                } else {
                                    resultSummary = if (isAr) {
                                        "تم استخراج ${extracted.size} حساب، الشغال منها: 0"
                                    } else {
                                        "Extracted ${extracted.size} accounts, Working: 0"
                                    }
                                }
                            }
                        }
                    )
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isAr) "تحليل وفحص وإضافة الشغال" else "Parse, Verify & Import",
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
