package com.kadri.stalkerplayerpro.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kadri.stalkerplayerpro.core.theme.AppThemeId
import com.kadri.stalkerplayerpro.core.theme.TvFontScale
import com.kadri.stalkerplayerpro.core.tv.LtrText
import com.kadri.stalkerplayerpro.core.tv.blockFocusWhen
import com.kadri.stalkerplayerpro.core.tv.tvFocusItem
import com.kadri.stalkerplayerpro.data.local.AppDao
import com.kadri.stalkerplayerpro.data.local.BackupInspectionResult
import com.kadri.stalkerplayerpro.data.local.BackupManager
import com.kadri.stalkerplayerpro.data.local.BackupPortalPreview
import com.kadri.stalkerplayerpro.data.local.BackupSelectionOptions
import com.kadri.stalkerplayerpro.data.local.SettingsDataStore
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import com.kadri.stalkerplayerpro.ui.components.TvPinDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    dao: AppDao,
    settings: TvUserSettings,
    settingsStore: SettingsDataStore,
    isAr: Boolean,
    isTv: Boolean,
    initialContentFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPinDialog by remember { mutableStateOf(false) }
    var showSubtitleAccountsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportPortalsList by remember { mutableStateOf<List<BackupPortalPreview>>(emptyList()) }

    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportRawJson by remember { mutableStateOf("") }
    var importInspection by remember { mutableStateOf<BackupInspectionResult?>(null) }

    val anyDialog = showPinDialog || showSubtitleAccountsDialog || showAboutDialog ||
            showExportDialog || showImportDialog

    val pendingExportJson = remember { mutableStateOf("") }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingExportJson.value.isNotBlank()) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(pendingExportJson.value.toByteArray(Charsets.UTF_8))
                        }
                        true
                    }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    if (ok) {
                        if (isAr) "تم حفظ النسخة الاحتياطية المخصصة بنجاح" else "Custom backup exported"
                    } else {
                        if (isAr) "تعذر حفظ الملف" else "Export failed"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    }.getOrDefault("")
                }
                val inspected = BackupManager.inspectBackupJson(raw)
                if (inspected != null) {
                    pendingImportRawJson = raw
                    importInspection = inspected
                    showImportDialog = true
                } else {
                    Toast.makeText(
                        context,
                        if (isAr) "ملف النسخة الاحتياطية غير صالح" else "Invalid backup file",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val currentTheme = AppThemeId.fromId(settings.themeId)
    val currentFont = TvFontScale.fromId(settings.fontScaleId, isTvDefault = isTv)

    val subtitleEngineLabel = when (settings.subtitleEngine) {
        "OPENSUBTITLES" -> "OpenSubtitles"
        "SUBDL" -> "Subdl.com"
        else -> if (isAr) "كلاهما (OpenSubtitles + Subdl)" else "Both (OpenSubtitles + Subdl)"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .blockFocusWhen(anyDialog)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
                ClassicSettingsRow(
                    icon = Icons.Default.ColorLens,
                    title = if (isAr) "مظهر التطبيق والألوان" else "Application Theme",
                    valueText = if (isAr) currentTheme.titleAr else currentTheme.titleEn,
                    isAr = isAr,
                    focusRequester = initialContentFocusRequester,
                    onClick = {
                        val next = (currentTheme.id + 1) % AppThemeId.entries.size
                        scope.launch { settingsStore.updateTheme(next) }
                    }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.FormatSize,
                    title = if (isAr) "حجم خط الواجهة" else "UI Font Scale",
                    valueText = if (isAr) currentFont.titleAr else currentFont.titleEn,
                    isAr = isAr,
                    onClick = {
                        val next = (currentFont.id + 1) % TvFontScale.entries.size
                        scope.launch { settingsStore.updateFontScale(next) }
                    }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.Language,
                    title = if (isAr) "لغة الواجهة" else "Interface Language",
                    valueText = if (isAr) "العربية" else "English",
                    isAr = isAr,
                    onClick = {
                        val nextLang = if (isAr) "en" else "ar"
                        scope.launch { settingsStore.updateLanguage(nextLang) }
                    }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.AccessTime,
                    title = if (isAr) "الساعة الرقمية في المشغل" else "Player Digital Clock",
                    valueText = if (settings.showClock) (if (isAr) "مفعّلة" else "Enabled") else (if (isAr) "مخفية" else "Hidden"),
                    isAr = isAr,
                    onClick = {
                        scope.launch { settingsStore.updateShowClock(!settings.showClock) }
                    }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.Lock,
                    title = if (isAr) "رمز الرقابة الأبوية (PIN)" else "Parental Control PIN",
                    valueText = "****",
                    isAr = isAr,
                    onClick = { showPinDialog = true }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.Search,
                    title = if (isAr) "محرك البحث عن الترجمة" else "Subtitle Search Engine",
                    valueText = subtitleEngineLabel,
                    isAr = isAr,
                    onClick = {
                        val nextEngine = when (settings.subtitleEngine) {
                            "BOTH" -> "OPENSUBTITLES"
                            "OPENSUBTITLES" -> "SUBDL"
                            else -> "BOTH"
                        }
                        scope.launch { settingsStore.updateSubtitleEngine(nextEngine) }
                    }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.Subtitles,
                    title = if (isAr) "حسابات الترجمة (OpenSubtitles / Subdl)" else "Subtitle API Accounts",
                    valueText = if (isAr) "إعداد المفاتيح" else "Configure Keys",
                    isAr = isAr,
                    onClick = { showSubtitleAccountsDialog = true }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.Backup,
                    title = if (isAr) "تصدير نسخة احتياطية مخصصة" else "Export Custom Backup",
                    valueText = if (isAr) "تحديد البوابات / المفضلة / الإعدادات" else "Select Items",
                    isAr = isAr,
                    onClick = {
                        scope.launch {
                            val all = dao.getAllPortals().filter { it.url != BUILTIN_FREE_AR_M3U_URL }
                            exportPortalsList = all.map {
                                BackupPortalPreview(
                                    uniqueKey = BackupManager.portalBackupKey(it.url, it.mac, it.username),
                                    name = it.name.take(10),
                                    protocol = it.protocol,
                                    url = it.url
                                )
                            }
                            showExportDialog = true
                        }
                    }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.Restore,
                    title = if (isAr) "استيراد نسخة احتياطية مخصصة" else "Import Custom Backup",
                    valueText = if (isAr) "انتقاء العناصر للاستعادة" else "Selective Restore",
                    isAr = isAr,
                    onClick = {
                        if (isTv) {
                            scope.launch {
                                val file = BackupManager.getDefaultBackupFile(context)
                                val raw = withContext(Dispatchers.IO) {
                                    if (file.exists()) runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("") else ""
                                }
                                val inspected = BackupManager.inspectBackupJson(raw)
                                if (inspected != null) {
                                    pendingImportRawJson = raw
                                    importInspection = inspected
                                    showImportDialog = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        if (isAr) "لم يتم العثور على ملف باكب في Download" else "No backup file found in Download",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            val ok = runCatching { importFileLauncher.launch(arrayOf("application/json", "*/*")) }.isSuccess
                            if (!ok) {
                                Toast.makeText(context, if (isAr) "متصفح الملفات غير متوفر" else "File picker unavailable", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.Info,
                    title = if (isAr) "حول التطبيق والمطور" else "About Application",
                    valueText = "v1.9.0",
                    isAr = isAr,
                    onClick = { showAboutDialog = true }
                )
            }
        }

        if (showExportDialog) {
            SelectiveBackupDialog(
                title = if (isAr) "تصدير نسخة احتياطية مخصصة (مستثنى AR-TV)" else "Export Custom Backup (AR-TV Excluded)",
                confirmLabel = if (isAr) "تصدير المحدد" else "Export Selected",
                portals = exportPortalsList,
                showFavoritesOption = true,
                showSubtitlesOption = true,
                showSettingsOption = true,
                isAr = isAr,
                onConfirm = { options ->
                    showExportDialog = false
                    scope.launch {
                        if (isTv) {
                            val savedPath = BackupManager.exportToDefaultFolder(context, dao, settingsStore, options)
                            Toast.makeText(
                                context,
                                if (savedPath != null) {
                                    if (isAr) "تم الحفظ في: $savedPath" else "Saved to: $savedPath"
                                } else {
                                    if (isAr) "تعذر التصدير" else "Export failed"
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            pendingExportJson.value = BackupManager.buildBackupJsonString(dao, settingsStore, options)
                            exportFileLauncher.launch("stalker_player_pro_backup.json")
                        }
                    }
                },
                onDismiss = { showExportDialog = false }
            )
        }

        if (showImportDialog && importInspection != null) {
            val info = importInspection!!
            SelectiveBackupDialog(
                title = if (isAr) "استعادة انتقائية من النسخة الاحتياطية" else "Selective Backup Restore",
                confirmLabel = if (isAr) "استعادة المحدد" else "Restore Selected",
                portals = info.portals,
                showFavoritesOption = info.favoritesCount > 0,
                favoritesLabelSuffix = " (${info.favoritesCount})",
                showSubtitlesOption = info.hasSubtitleAccounts,
                showSettingsOption = info.hasAppSettings,
                isAr = isAr,
                onConfirm = { options ->
                    showImportDialog = false
                    scope.launch {
                        val ok = BackupManager.parseAndApplyBackupJson(pendingImportRawJson, dao, settingsStore, options)
                        Toast.makeText(
                            context,
                            if (ok) {
                                if (isAr) "تمت استعادة العناصر المحددة بنجاح" else "Selected items restored"
                            } else {
                                if (isAr) "فشلت الاستعادة" else "Restore failed"
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onDismiss = { showImportDialog = false }
            )
        }

        if (showPinDialog) {
            TvPinDialog(
                title = if (isAr) "تعيين رمز PIN جديد" else "Set New Parental PIN",
                isAr = isAr,
                onPinSubmitted = { newPin ->
                    scope.launch { settingsStore.updateParentalPin(newPin) }
                    showPinDialog = false
                    Toast.makeText(context, if (isAr) "تم تحديث رمز القفل" else "PIN Updated", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showPinDialog = false }
            )
        }

        if (showSubtitleAccountsDialog) {
            var osKey by remember { mutableStateOf(settings.openSubApiKey) }
            var osUser by remember { mutableStateOf(settings.openSubUsername) }
            var osPass by remember { mutableStateOf(settings.openSubPassword) }
            var subdlKey by remember { mutableStateOf(settings.subdlApiKey) }

            TvModalDialog(
                title = if (isAr) "حسابات الترجمة الخارجية" else "External Subtitle Accounts",
                onDismiss = { showSubtitleAccountsDialog = false },
                maxWidth = 580.dp,
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
                                    onClick = { showSubtitleAccountsDialog = false }
                                )
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(if (isAr) "إلغاء" else "Cancel", color = MaterialTheme.colorScheme.onSurface)
                        }
                        Box(
                            modifier = Modifier
                                .tvFocusItem(
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        scope.launch {
                                            settingsStore.updateSubtitleAccounts(
                                                osApiKey = osKey.trim(),
                                                osUser = osUser.trim(),
                                                osPass = osPass.trim(),
                                                osToken = "",
                                                subdlApiKey = subdlKey.trim()
                                            )
                                        }
                                        showSubtitleAccountsDialog = false
                                        Toast.makeText(context, if (isAr) "تم الحفظ" else "Saved", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                .padding(horizontal = 24.dp, vertical = 9.dp)
                        ) {
                            Text(if (isAr) "حفظ" else "Save", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            ) { _ ->
                OutlinedTextField(
                    value = osKey,
                    onValueChange = { osKey = it },
                    label = { Text("OpenSubtitles.com API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = osUser,
                        onValueChange = { osUser = it },
                        label = { Text("OpenSubtitles Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = osPass,
                        onValueChange = { osPass = it },
                        label = { Text("OpenSubtitles Password") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = subdlKey,
                    onValueChange = { subdlKey = it },
                    label = { Text("Subdl.com API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showAboutDialog) {
            TvModalDialog(
                title = if (isAr) "حول التطبيق" else "About Application",
                onDismiss = { showAboutDialog = false },
                maxWidth = 500.dp,
                scrollableBody = true,
                footer = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Box(
                            modifier = Modifier
                                .tvFocusItem(
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                                    onClick = { showAboutDialog = false }
                                )
                                .padding(horizontal = 28.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = if (isAr) "إغلاق" else "Close",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            ) { _ ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LtrText(
                        text = "stalker-player pro (IPTV STALKER)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "الأستاذ محمد قادري",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ثانوية محمد الأخضر فيلالي - ولاية غرداية - الجزائر",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LtrText(
                        text = "Email: kadri68@gmail.com",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectiveBackupDialog(
    title: String,
    confirmLabel: String,
    portals: List<BackupPortalPreview>,
    showFavoritesOption: Boolean,
    favoritesLabelSuffix: String = "",
    showSubtitlesOption: Boolean,
    showSettingsOption: Boolean,
    isAr: Boolean,
    onConfirm: (BackupSelectionOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPortalKeys by remember(portals) {
        mutableStateOf(portals.map { it.uniqueKey }.toSet())
    }
    var includeFavorites by remember { mutableStateOf(showFavoritesOption) }
    var includeSubtitles by remember { mutableStateOf(showSubtitlesOption) }
    var includeSettings by remember { mutableStateOf(showSettingsOption) }

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
                    Text(if (isAr) "إلغاء" else "Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                onConfirm(
                                    BackupSelectionOptions(
                                        includePortals = selectedPortalKeys.isNotEmpty(),
                                        selectedPortalKeys = selectedPortalKeys,
                                        includeFavorites = includeFavorites,
                                        includeSubtitleAccounts = includeSubtitles,
                                        includeAppSettings = includeSettings
                                    )
                                )
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 9.dp)
                ) {
                    Text(confirmLabel, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { firstRequester ->
        var assignedFirst = false

        if (portals.isNotEmpty()) {
            Text(
                text = if (isAr) "البوابات المضافة (اختر ما تريد):" else "Added Portals (Select):",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            portals.forEach { p ->
                val checked = p.uniqueKey in selectedPortalKeys
                val req = if (!assignedFirst) {
                    assignedFirst = true
                    firstRequester
                } else null
                BackupCheckRow(
                    label = "${p.name} [${p.protocol}]",
                    checked = checked,
                    focusRequester = req,
                    onToggle = {
                        selectedPortalKeys = if (checked) {
                            selectedPortalKeys - p.uniqueKey
                        } else {
                            selectedPortalKeys + p.uniqueKey
                        }
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showFavoritesOption) {
            val req = if (!assignedFirst) {
                assignedFirst = true
                firstRequester
            } else null
            BackupCheckRow(
                label = (if (isAr) "قائمة المفضلة (Favorites)" else "Favorites List") + favoritesLabelSuffix,
                checked = includeFavorites,
                focusRequester = req,
                onToggle = { includeFavorites = !includeFavorites }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (showSubtitlesOption) {
            val req = if (!assignedFirst) {
                assignedFirst = true
                firstRequester
            } else null
            BackupCheckRow(
                label = if (isAr) "حسابات ومفاتيح الترجمة (OpenSubtitles / Subdl)" else "Subtitle Accounts & Keys",
                checked = includeSubtitles,
                focusRequester = req,
                onToggle = { includeSubtitles = !includeSubtitles }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (showSettingsOption) {
            val req = if (!assignedFirst) {
                assignedFirst = true
                firstRequester
            } else null
            BackupCheckRow(
                label = if (isAr) "إعدادات التطبيق (المظهر، الخط، الرقابة الأبوية)" else "Application Settings",
                checked = includeSettings,
                focusRequester = req,
                onToggle = { includeSettings = !includeSettings }
            )
        }
    }
}

@Composable
private fun BackupCheckRow(
    label: String,
    checked: Boolean,
    focusRequester: FocusRequester? = null,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusItem(
                focusRequester = focusRequester,
                shape = RoundedCornerShape(8.dp),
                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                onClick = onToggle
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ClassicSettingsRow(
    icon: ImageVector,
    title: String,
    valueText: String,
    isAr: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusItem(
                focusRequester = focusRequester,
                shape = RoundedCornerShape(12.dp),
                unfocusedBackgroundColor = MaterialTheme.colorScheme.surface,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (isAr) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
