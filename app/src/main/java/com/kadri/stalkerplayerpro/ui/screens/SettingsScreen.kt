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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
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
import kotlin.math.roundToInt

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
    var showUnifiedAudioSubDialog by remember { mutableStateOf(false) }
    var showAssistantDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportPortalsList by remember { mutableStateOf<List<BackupPortalPreview>>(emptyList()) }

    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportRawJson by remember { mutableStateOf("") }
    var importInspection by remember { mutableStateOf<BackupInspectionResult?>(null) }
    var availableBackupFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var showBackupFilePickerDialog by remember { mutableStateOf(false) }
    var isImportingBackup by remember { mutableStateOf(false) }

    val anyDialog = showPinDialog || showUnifiedAudioSubDialog || showAssistantDialog || showAboutDialog ||
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
                    valueText = if (isAr) currentFont.labelAr else currentFont.labelEn,
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
                val audCode = settings.preferredAudioLang.uppercase()
                val subCode = settings.preferredSubtitleLang.uppercase()
                ClassicSettingsRow(
                    icon = Icons.Default.Subtitles,
                    title = if (isAr) "إعدادات الصوت والترجمة والحسابات" else "Audio, Subtitles & Accounts",
                    valueText = if (isAr) "الصوت: $audCode | الترجمة: $subCode | ${settings.subFontSizeSp.roundToInt()}sp" else "Audio: $audCode | Sub: $subCode | ${settings.subFontSizeSp.roundToInt()}sp",
                    isAr = isAr,
                    onClick = { showUnifiedAudioSubDialog = true }
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
                        if (!isImportingBackup) {
                            scope.launch {
                                isImportingBackup = true
                                val files = BackupManager.findBackupFilesOnDevice(context)
                                if (files.size == 1) {
                                    val raw = withContext(Dispatchers.IO) {
                                        runCatching { files.first().readText(Charsets.UTF_8) }.getOrDefault("")
                                    }
                                    val inspected = BackupManager.inspectBackupJsonAsync(raw)
                                    isImportingBackup = false
                                    if (inspected != null) {
                                        pendingImportRawJson = raw
                                        importInspection = inspected
                                        showImportDialog = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            if (isAr) "ملف الباكب غير صالح أو تالف" else "Backup file is invalid or corrupted",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else if (files.size > 1) {
                                    isImportingBackup = false
                                    availableBackupFiles = files
                                    showBackupFilePickerDialog = true
                                } else {
                                    isImportingBackup = false
                                    val ok = runCatching {
                                        com.kadri.stalkerplayerpro.MainActivity.isLaunchingExternalPicker = true
                                        importFileLauncher.launch(arrayOf("application/json", "*/*"))
                                    }.isSuccess
                                    if (!ok) {
                                        Toast.makeText(
                                            context,
                                            if (isAr) "ضع ملف stalker_player_pro_backup.json في مجلد Download" else "Place stalker_player_pro_backup.json in Download folder",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.Info,
                    title = if (isAr) "مساعد التطبيق ودليل الخدمات" else "App Assistant & Services Guide",
                    valueText = if (isAr) "شرح الميزات" else "All Features",
                    isAr = isAr,
                    onClick = { showAssistantDialog = true }
                )
            }

            item {
                ClassicSettingsRow(
                    icon = Icons.Default.Info,
                    title = if (isAr) "حول التطبيق والمطور" else "About Application",
                    valueText = "v${com.kadri.stalkerplayerpro.BuildConfig.VERSION_NAME}",
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
                            com.kadri.stalkerplayerpro.MainActivity.isLaunchingExternalPicker = true; exportFileLauncher.launch("stalker_player_pro_backup.json")
                        }
                    }
                },
                onDismiss = { showExportDialog = false }
            )
        }

        if (showBackupFilePickerDialog) {
            TvModalDialog(
                title = if (isAr) "اختر ملف النسخة الاحتياطية" else "Select Backup File",
                onDismiss = { showBackupFilePickerDialog = false },
                maxWidth = 540.dp,
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
                                    onClick = {
                                        showBackupFilePickerDialog = false
                                        val ok = runCatching {
                                            com.kadri.stalkerplayerpro.MainActivity.isLaunchingExternalPicker = true
                                            importFileLauncher.launch(arrayOf("application/json", "*/*"))
                                        }.isSuccess
                                        if (!ok) {
                                            Toast.makeText(context, if (isAr) "متصفح الملفات غير متوفر على هذا التلفاز" else "System file picker unavailable", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(if (isAr) "تصفح الجهاز" else "Browse Device", color = MaterialTheme.colorScheme.onSurface)
                        }
                        Box(
                            modifier = Modifier
                                .tvFocusItem(
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                                    onClick = { showBackupFilePickerDialog = false }
                                )
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(if (isAr) "إغلاق" else "Close", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            ) { firstReq ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableBackupFiles.forEachIndexed { fIdx, bFile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusItem(
                                    focusRequester = if (fIdx == 0) firstReq else null,
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = {
                                        showBackupFilePickerDialog = false
                                        scope.launch {
                                            val raw = withContext(Dispatchers.IO) {
                                                runCatching { bFile.readText(Charsets.UTF_8) }.getOrDefault("")
                                            }
                                            val inspected = BackupManager.inspectBackupJsonAsync(raw)
                                            if (inspected != null) {
                                                pendingImportRawJson = raw
                                                importInspection = inspected
                                                showImportDialog = true
                                            } else {
                                                Toast.makeText(context, if (isAr) "ملف باكب غير صالح" else "Invalid backup file", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = bFile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = bFile.parentFile?.name.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
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
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            BackupManager.parseAndApplyBackupJson(pendingImportRawJson, dao, settingsStore, options)
                        }
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

        if (showUnifiedAudioSubDialog) {
            UnifiedAudioSubtitleSettingsDialog(
                settings = settings,
                settingsStore = settingsStore,
                isAr = isAr,
                onDismiss = { showUnifiedAudioSubDialog = false }
            )
        }

        if (showAssistantDialog) {
            AppServicesAssistantDialog(
                isAr = isAr,
                onDismiss = { showAssistantDialog = false }
            )
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
                    Spacer(modifier = Modifier.height(6.dp))
                    LtrText(
                        text = "Version: v${com.kadri.stalkerplayerpro.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
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
private fun UnifiedAudioSubtitleSettingsDialog(
    settings: TvUserSettings,
    settingsStore: SettingsDataStore,
    isAr: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedAudioLang by remember { mutableStateOf(settings.preferredAudioLang) }
    var selectedSubLang by remember { mutableStateOf(settings.preferredSubtitleLang) }
    var selectedEngine by remember { mutableStateOf(settings.subtitleEngine) }

    var osKey by remember { mutableStateOf(settings.openSubApiKey) }
    var osUser by remember { mutableStateOf(settings.openSubUsername) }
    var osPass by remember { mutableStateOf(settings.openSubPassword) }
    var osToken by remember { mutableStateOf(settings.openSubJwtToken) }
    var osBaseUrl by remember { mutableStateOf(settings.openSubBaseUrl) }
    var isVerifyingLogin by remember { mutableStateOf(false) }
    var loginStatusMsg by remember {
        mutableStateOf(
            if (settings.openSubJwtToken.isNotBlank()) {
                if (isAr) "✓ الجلسة نشطة ومسجلة الدخول" else "✓ Active logged-in session"
            } else ""
        )
    }
    var loginStatusSuccess by remember { mutableStateOf(settings.openSubJwtToken.isNotBlank()) }
    var subdlKey by remember { mutableStateOf(settings.subdlApiKey) }

    var selectedColor by remember { mutableStateOf(settings.subTextColor) }
    var selectedSize by remember { mutableStateOf(settings.subFontSizeSp) }
    var selectedAlpha by remember { mutableStateOf(settings.subBgAlpha) }

    val audioLangOptions = listOf(
        "auto" to (if (isAr) "تلقائي" else "Auto"),
        "ar" to (if (isAr) "العربية" else "Arabic"),
        "en" to "English",
        "fr" to "Français",
        "tr" to "Türkçe",
        "es" to "Español"
    )

    val subLangOptions = listOf(
        "ar" to (if (isAr) "العربية" else "Arabic"),
        "en" to "English",
        "fr" to "Français",
        "tr" to "Türkçe",
        "es" to "Español",
        "off" to (if (isAr) "إيقاف" else "Off")
    )

    val engineOptions = listOf(
        "BOTH" to (if (isAr) "كلاهما (Both)" else "Both"),
        "OPENSUBTITLES" to "OpenSubtitles",
        "SUBDL" to "Subdl"
    )

    val colorChoices = listOf(
        0xFFFFFFFFL to (if (isAr) "أبيض" else "White"),
        0xFFFFD600L to (if (isAr) "أصفر" else "Yellow"),
        0xFF00E5FFL to (if (isAr) "سماوي" else "Cyan"),
        0xFF00E676L to (if (isAr) "أخضر" else "Green"),
        0xFFFF9100L to (if (isAr) "برتقالي" else "Orange")
    )

    val sizePresets = listOf(
        24f to "24sp",
        36f to "36sp",
        48f to "48sp",
        64f to "64sp",
        78f to "78sp",
        90f to "90sp"
    )

    TvModalDialog(
        title = if (isAr) "إعدادات الصوت والترجمة والحسابات" else "Audio, Subtitles & Accounts",
        onDismiss = onDismiss,
        maxWidth = 640.dp,
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
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                        settingsStore.updateUnifiedAudioSubtitleSettings(
                                            audioLang = selectedAudioLang,
                                            subtitleLang = selectedSubLang,
                                            subtitleEngine = selectedEngine,
                                            osApiKey = osKey.trim(),
                                            osUser = osUser.trim(),
                                            osPass = osPass.trim(),
                                            osToken = osToken.trim(),
                                            osBaseUrl = osBaseUrl.trim(),
                                            subdlApiKey = subdlKey.trim(),
                                            subColor = selectedColor,
                                            subSizeSp = selectedSize,
                                            subBgAlpha = selectedAlpha
                                        )
                                    }
                                    Toast.makeText(
                                        context,
                                        if (isAr) "تم حفظ جميع إعدادات الصوت والترجمة" else "All audio & subtitle settings saved",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isAr) "حفظ" else "Save",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    ) { firstRequester ->
        Text(
            text = if (isAr) "1. لغة الصوت المدمج المفضلة:" else "1. Preferred Embedded Audio Language:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            audioLangOptions.forEachIndexed { idx, (code, label) ->
                val active = selectedAudioLang.equals(code, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
                            focusRequester = if (idx == 0) firstRequester else null,
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedAudioLang = code }
                        )
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isAr) "2. لغة الترجمة الموحدة (المدمجة والخارجية):" else "2. Unified Subtitle Language (Embedded & External):",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            subLangOptions.forEach { (code, label) ->
                val active = selectedSubLang.equals(code, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedSubLang = code }
                        )
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isAr) "3. محرك البحث عن الترجمة:" else "3. Subtitle Search Engine:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            engineOptions.forEach { (engCode, engLabel) ->
                val active = selectedEngine.equals(engCode, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedEngine = engCode }
                        )
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = engLabel,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isAr) "4. حسابات الترجمة (OpenSubtitles / Subdl):" else "4. Subtitle Accounts (OpenSubtitles / Subdl):",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .tvFocusItem(
                        shape = RoundedCornerShape(8.dp),
                        unfocusedBackgroundColor = Color(0xFF00897B),
                        onClick = {
                            if (!isVerifyingLogin) {
                                scope.launch {
                                    isVerifyingLogin = true
                                    loginStatusMsg = if (isAr) "جارٍ تأكيد الدخول إلى OpenSubtitles..." else "Verifying OpenSubtitles login..."
                                    val res = com.kadri.stalkerplayerpro.data.network.SubtitleRepository.loginOpenSubtitlesDetailed(
                                        apiKey = osKey,
                                        username = osUser,
                                        password = osPass
                                    )
                                    loginStatusSuccess = res.success
                                    loginStatusMsg = res.message
                                    if (res.success && res.token.isNotBlank()) {
                                        osToken = res.token
                                        osBaseUrl = res.baseUrl
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                            settingsStore.updateOpenSubSession(res.token, res.baseUrl)
                                        }
                                    }
                                    isVerifyingLogin = false
                                }
                            }
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isVerifyingLogin) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                    Text(
                        text = if (isAr) "تأكيد الدخول OpenSubtitles" else "Confirm OpenSubtitles Login",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (loginStatusMsg.isNotBlank()) {
                Text(
                    text = loginStatusMsg,
                    color = if (loginStatusSuccess) Color(0xFF00E676) else Color(0xFFFFB300),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = subdlKey,
            onValueChange = { subdlKey = it },
            label = { Text("Subdl.com API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isAr) "5. لون نص الترجمة:" else "5. Subtitle Text Color:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            colorChoices.forEach { (hex, label) ->
                val active = selectedColor == hex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedColor = hex }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else Color(hex),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isAr) "6. حجم الخط (${selectedSize.roundToInt()}sp حتى 90sp):" else "6. Font Size (${selectedSize.roundToInt()}sp up to 90sp):",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .tvFocusItem(
                        shape = RoundedCornerShape(8.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { selectedSize = (selectedSize - 4f).coerceAtLeast(18f) }
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) { LtrText("A-", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
            Box(
                modifier = Modifier
                    .tvFocusItem(
                        shape = RoundedCornerShape(8.dp),
                        unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { selectedSize = (selectedSize + 4f).coerceAtMost(90f) }
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) { LtrText("A+", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sizePresets.forEach { (spVal, lbl) ->
                val active = kotlin.math.abs(selectedSize - spVal) < 2f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedSize = spVal }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LtrText(
                        text = lbl,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isAr) "7. شفافية خلفية الترجمة:" else "7. Background Opacity:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0f to "0%", 0.45f to "45%", 0.65f to "65%", 0.9f to "90%").forEach { (alphaVal, lbl) ->
                val active = kotlin.math.abs(selectedAlpha - alphaVal) < 0.08f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusItem(
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBackgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { selectedAlpha = alphaVal }
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LtrText(
                        text = lbl,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
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

@Composable
private fun AppServicesAssistantDialog(
    isAr: Boolean,
    onDismiss: () -> Unit
) {
    val sections = if (isAr) listOf(
        Triple("1. اللصق الذكي والمحلل الشامل (Smart Paste)", "انسخ أي نص يحتوي على روابط Stalker مع عناوين MAC متعددة، أو روابط Xtream Codes، أو روابط قوائم M3U، ثم اضغط (لصق ذكي). يقوم التطبيق باستخلاصها جميعاً، فحص الشغال منها تلقائياً، وحفظ الحسابات العاملة بضغطة واحدة.", Icons.Default.Search),
        Triple("2. الاستيراد والتصدير متعدد الصيغ (QR / M3U / TXT)", "• QR Code: مسح رمز QR بالكاميرا أو من صورة في المعرض، وتوليد رمز QR لأي بوابة لمشاركتها فوراً بين الهاتف والتلفاز.\n• ملفات M3U و TXT: استيراد قوائم القنوات أو ملفات الحسابات المجمعة مباشرة.\n• النسخ الاحتياطي المخصص: تصدير واستيراد انتقائي للبوابات والمفضلة والترجمات المحفوظة والإعدادات.", Icons.Default.Backup),
        Triple("3. المشغل المزدوج الذكي واستئناف البث", "• يدعم محركين (ExoPlayer و LibVLC) مع التبديل التلقائي أو اليدوي لدعم كافة الصيغ والصوت المحيطي (AC3/DTS).\n• استئناف ذكي للبث: عند توقف الفيلم أو القناة فجأة، اضغط زر التشغيل وسيتم توليد رابط جلسة جديد واستئناف المشاهدة من نفس اللحظة.", Icons.Default.Restore),
        Triple("4. إدارة الترجمة المتقدمة والحفظ التلقائي", "• البحث المباشر في OpenSubtitles و Subdl مع فك الضغط التلقائي.\n• حفظ ترجمة كل فيلم: تُحفظ الترجمة المعتمدة لكل فيلم أو حلقة تلقائياً وتُستعاد عند العودة.\n• توحيد التنسيق وتنظيف رموز ASS، مع ضبط الحجم واللون والخلفية ومزامنة التوقيت (+ / -).", Icons.Default.Subtitles),
        Triple("5. اختصارات الريموت واللمس ووضع PiP", "• الريموت: اكتب رقم القناة مباشرة، اضغط (0) للعودة للقناة السابقة، (CH+/CH-) للتبديل، و(OK) لإظهار الشريط.\n• اللمس: اسحب عمودياً يمين الشاشة للصوت ويسارها للسطوع.\n• وضع PiP: تصغير الفيديو في نافذة عائمة أثناء تصفح التطبيق.", Icons.Default.Palette)
    ) else listOf(
        Triple("1. Smart Paste & Multi-Parser", "Paste any raw text containing Stalker portals + multiple MACs, Xtream Codes links, or M3U URLs. The parser extracts, tests live connectivity, and saves only working portals automatically.", Icons.Default.Search),
        Triple("2. Multi-Format Import & Export (QR / M3U / TXT)", "• QR Code: Scan from camera or gallery, or generate a QR code for any portal to share between phone and TV.\n• M3U & TXT Files: Import playlists or bulk account text files directly.\n• Custom Backup: Selectively export and restore portals, favorites, saved subtitles, and settings.", Icons.Default.Backup),
        Triple("3. Dual Engine & Smart Stream Recovery", "• Seamless switching between ExoPlayer and LibVLC for full codec and surround audio (AC3/DTS) support.\n• Smart Resume: Pressing Play after an unexpected stream drop fetches a fresh session URL and resumes from the exact position.", Icons.Default.Restore),
        Triple("4. Advanced Subtitles & Per-Movie Memory", "• Search OpenSubtitles & Subdl directly with automatic ZIP extraction.\n• Auto-Save per Movie: Approved subtitles are saved per movie/episode and restored automatically.\n• Unified clean styling with ASS tag stripping, preset sizes/colors, and +/- sync adjustment.", Icons.Default.Subtitles),
        Triple("5. Remote Shortcuts, Touch Gestures & PiP", "• Remote: Direct channel number entry, (0) for previous channel, (CH+/CH-) to zap, and (OK) for controls.\n• Touch: Vertical swipe right for volume, left for brightness.\n• PiP Mode: Watch in a floating picture-in-picture window.", Icons.Default.Palette)
    )
    TvModalDialog(
        title = if (isAr) "مساعد التطبيق ودليل الخدمات" else "App Assistant & Services Guide",
        onDismiss = onDismiss,
        maxWidth = 640.dp,
        scrollableBody = true,
        footer = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier
                        .tvFocusItem(
                            shape = RoundedCornerShape(10.dp),
                            unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 28.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = if (isAr) "فهمت / إغلاق" else "Got It / Close",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    ) { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sections.forEach { (heading, body, icon) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = heading, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
