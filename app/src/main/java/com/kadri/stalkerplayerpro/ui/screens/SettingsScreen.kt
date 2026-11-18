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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.kadri.stalkerplayerpro.data.local.BackupManager
import com.kadri.stalkerplayerpro.data.local.SettingsDataStore
import com.kadri.stalkerplayerpro.data.local.TvUserSettings
import com.kadri.stalkerplayerpro.data.network.SubtitleRepository
import com.kadri.stalkerplayerpro.ui.components.TvModalDialog
import com.kadri.stalkerplayerpro.ui.components.TvPinDialog
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    dao: AppDao,
    settings: TvUserSettings,
    settingsStore: SettingsDataStore,
    isAr: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showClockDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showSubApiDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val anyDialogOpen = showThemeDialog || showFontDialog || showClockDialog ||
            showPinDialog || showSubApiDialog || showAboutDialog

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = BackupManager.exportBackupToUri(context, uri, dao, settingsStore)
                Toast.makeText(
                    context,
                    if (ok) {
                        if (isAr) "تم تصدير النسخة الاحتياطية بنجاح" else "Backup exported successfully"
                    } else {
                        if (isAr) "فشل تصدير النسخة الاحتياطية" else "Backup export failed"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = BackupManager.importBackupFromUri(context, uri, dao, settingsStore)
                Toast.makeText(
                    context,
                    if (ok) {
                        if (isAr) "تم استعادة النسخة الاحتياطية بنجاح" else "Backup restored successfully"
                    } else {
                        if (isAr) "ملف النسخة الاحتياطية غير صالح" else "Invalid backup file"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val currentTheme = AppThemeId.fromId(settings.themeId)
    val currentFontScale = TvFontScale.fromId(settings.fontScaleId)
    val clockCornerLabel = when (settings.clockCorner) {
        0 -> if (isAr) "أعلى اليمين" else "Top Right"
        1 -> if (isAr) "أعلى اليسار" else "Top Left"
        2 -> if (isAr) "أسفل اليمين" else "Bottom Right"
        else -> if (isAr) "أسفل اليسار" else "Bottom Left"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blockFocusWhen(anyDialogOpen)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = if (isAr) "الإعدادات" else "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // قائمة إعدادات عمودية كلاسيكية منظمة بالأيقونات والأسهم
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "set_theme") {
                    ClassicSettingsRow(
                        icon = Icons.Default.ColorLens,
                        title = if (isAr) "ثيمات الواجهة (6 ثيمات)" else "Interface Themes (6 Themes)",
                        valueText = if (isAr) currentTheme.titleAr else currentTheme.titleEn,
                        isAr = isAr,
                        onClick = { showThemeDialog = true }
                    )
                }

                item(key = "set_lang") {
                    ClassicSettingsRow(
                        icon = Icons.Default.Language,
                        title = if (isAr) "لغة الواجهة (العربية / English)" else "Interface Language",
                        valueText = if (isAr) "العربية" else "English",
                        isAr = isAr,
                        onClick = {
                            scope.launch {
                                settingsStore.updateLanguage(if (isAr) "en" else "ar")
                            }
                        }
                    )
                }

                item(key = "set_font") {
                    ClassicSettingsRow(
                        icon = Icons.Default.FormatSize,
                        title = if (isAr) "حجم خط الواجهة للتلفاز" else "TV Interface Font Size",
                        valueText = if (isAr) currentFontScale.labelAr else currentFontScale.labelEn,
                        isAr = isAr,
                        onClick = { showFontDialog = true }
                    )
                }

                item(key = "set_pin") {
                    ClassicSettingsRow(
                        icon = Icons.Default.Lock,
                        title = if (isAr) "رمز PIN للرقابة الأبوية" else "Parental Control PIN",
                        valueText = "****",
                        isAr = isAr,
                        onClick = { showPinDialog = true }
                    )
                }

                item(key = "set_clock") {
                    ClassicSettingsRow(
                        icon = Icons.Default.AccessTime,
                        title = if (isAr) "الساعة الرقمية وزاوية العرض" else "Digital Clock & Corner",
                        valueText = if (settings.showClock) clockCornerLabel else (if (isAr) "مخفية" else "Hidden"),
                        isAr = isAr,
                        onClick = { showClockDialog = true }
                    )
                }

                item(key = "set_sub_api") {
                    ClassicSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = if (isAr) "حسابات الترجمة الخارجية (OpenSubtitles / Subdl)" else "External Subtitles APIs",
                        valueText = if (settings.openSubApiKey.isNotBlank() || settings.subdlApiKey.isNotBlank()) {
                            if (isAr) "مُفعّل" else "Configured"
                        } else {
                            if (isAr) "غير مضبوط" else "Not Set"
                        },
                        isAr = isAr,
                        onClick = { showSubApiDialog = true }
                    )
                }

                item(key = "set_export") {
                    ClassicSettingsRow(
                        icon = Icons.Default.Backup,
                        title = if (isAr) "تصدير نسخة احتياطية شاملة (JSON)" else "Export Full Backup (JSON)",
                        valueText = "JSON",
                        isAr = isAr,
                        onClick = {
                            scope.launch { val p = BackupManager.exportToDefaultFolder(context, dao, settingsStore); Toast.makeText(context, if (p != null) (if (isAr) "تم الحفظ في: $p" else "Saved: $p") else "Error", Toast.LENGTH_LONG).show(); runCatching { exportBackupLauncher.launch("stalker_player_pro_backup.json") } }
                        }
                    )
                }

                item(key = "set_import") {
                    ClassicSettingsRow(
                        icon = Icons.Default.Restore,
                        title = if (isAr) "استيراد نسخة احتياطية شاملة (JSON)" else "Import Full Backup (JSON)",
                        valueText = "JSON",
                        isAr = isAr,
                        onClick = {
                            scope.launch { val okLocal = BackupManager.importFromDefaultFolder(context, dao, settingsStore); if (okLocal) { Toast.makeText(context, if (isAr) "تمت استعادة النسخة من مجلد Download بنجاح" else "Restored from Download folder", Toast.LENGTH_SHORT).show() } else { val opened = runCatching { importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }.isSuccess; if (!opened) Toast.makeText(context, if (isAr) "ضع ملف stalker_player_pro_backup.json في مجلد Download" else "Place stalker_player_pro_backup.json in Download", Toast.LENGTH_LONG).show() } }
                        }
                    )
                }

                item(key = "set_about") {
                    ClassicSettingsRow(
                        icon = Icons.Default.Info,
                        title = if (isAr) "حول التطبيق ومعلومات المطور" else "About App & Developer",
                        valueText = "v1.0.0",
                        isAr = isAr,
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }

        // نافذة اختيار الثيم من الثيمات الـ 6
        if (showThemeDialog) {
            TvModalDialog(
                title = if (isAr) "اختر ثيم الواجهة" else "Select Interface Theme",
                onDismiss = { showThemeDialog = false },
                maxWidth = 500.dp
            ) { firstRequester ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(
                        items = AppThemeId.entries,
                        key = { idx, t -> "theme_opt_${t.id}_$idx" }
                    ) { index, theme ->
                        val active = theme.id == settings.themeId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusItem(
                                    focusRequester = if (index == 0) firstRequester else null,
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = if (active) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    onClick = {
                                        scope.launch { settingsStore.updateTheme(theme.id) }
                                        showThemeDialog = false
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(theme.colorScheme.primary, CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isAr) theme.titleAr else theme.titleEn,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (active) {
                                LtrText(text = "✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // نافذة اختيار حجم خط الواجهة (كبير، كبير جداً، ضخم، ضخم جداً)
        if (showFontDialog) {
            TvModalDialog(
                title = if (isAr) "حجم خط الواجهة للتلفاز" else "TV Interface Font Size",
                onDismiss = { showFontDialog = false },
                maxWidth = 460.dp
            ) { firstRequester ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvFontScale.entries.forEachIndexed { index, scale ->
                        val active = scale.id == settings.fontScaleId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusItem(
                                    focusRequester = if (index == 0) firstRequester else null,
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = if (active) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    onClick = {
                                        scope.launch { settingsStore.updateFontScale(scale.id) }
                                        showFontDialog = false
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isAr) scale.labelAr else scale.labelEn,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (active) {
                                LtrText(text = "✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // نافذة إعدادات الساعة الرقمية
        if (showClockDialog) {
            TvModalDialog(
                title = if (isAr) "إعدادات الساعة الرقمية" else "Digital Clock Settings",
                onDismiss = { showClockDialog = false },
                maxWidth = 460.dp
            ) { firstRequester ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusItem(
                                focusRequester = firstRequester,
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    scope.launch { settingsStore.updateShowClock(!settings.showClock) }
                                }
                            )
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isAr) "إظهار الساعة أثناء البث" else "Show Clock During Playback",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.showClock) (if (isAr) "مفعّل" else "ON") else (if (isAr) "معطّل" else "OFF"),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    val corners = listOf(
                        0 to (if (isAr) "أعلى اليمين" else "Top Right"),
                        1 to (if (isAr) "أعلى اليسار" else "Top Left"),
                        2 to (if (isAr) "أسفل اليمين" else "Bottom Right"),
                        3 to (if (isAr) "أسفل اليسار" else "Bottom Left")
                    )
                    corners.forEach { (cId, cLabel) ->
                        val active = settings.clockCorner == cId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusItem(
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBackgroundColor = if (active) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    onClick = {
                                        scope.launch { settingsStore.updateClockCorner(cId) }
                                        showClockDialog = false
                                    }
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = cLabel, color = MaterialTheme.colorScheme.onSurface)
                            if (active) LtrText(text = "✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // نافذة تعيين رمز PIN جديد
        if (showPinDialog) {
            TvPinDialog(
                title = if (isAr) "تعيين رمز PIN جديد (4 أرقام)" else "Set New 4-Digit PIN",
                isAr = isAr,
                onPinSubmitted = { newPin ->
                    scope.launch { settingsStore.updateParentalPin(newPin) }
                    showPinDialog = false
                    Toast.makeText(
                        context,
                        if (isAr) "تم تحديث رمز PIN بنجاح" else "PIN updated",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onDismiss = { showPinDialog = false }
            )
        }

        // نافذة إعدادات OpenSubtitles v1 و Subdl
        if (showSubApiDialog) {
            var osKey by remember { mutableStateOf(settings.openSubApiKey) }
            var osUser by remember { mutableStateOf(settings.openSubUsername) }
            var osPass by remember { mutableStateOf(settings.openSubPassword) }
            var subdlKey by remember { mutableStateOf(settings.subdlApiKey) }
            var isLoggingIn by remember { mutableStateOf(false) }

            TvModalDialog(
                title = if (isAr) "إعدادات الترجمة (OpenSubtitles v1 & Subdl)" else "Subtitles API Settings",
                onDismiss = { showSubApiDialog = false },
                maxWidth = 580.dp
            ) { firstRequester ->
                OutlinedTextField(
                    value = osKey,
                    onValueChange = { osKey = it },
                    label = { Text("OpenSubtitles.com Api-Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        modifier = Modifier
                            .tvFocusItem(
                                focusRequester = firstRequester,
                                enabled = !isLoggingIn,
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    scope.launch {
                                        isLoggingIn = true
                                        val token = SubtitleRepository.loginOpenSubtitles(osKey, osUser, osPass).orEmpty()
                                        settingsStore.updateSubtitleAccounts(osKey, osUser, osPass, token, subdlKey)
                                        isLoggingIn = false
                                        showSubApiDialog = false
                                        Toast.makeText(
                                            context,
                                            if (isAr) "تم حفظ إعدادات الترجمة" else "Subtitle settings saved",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isAr) "حفظ وتسجيل الدخول" else "Save & Login",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // نافذة «حول التطبيق» بمعلومات المطور
        if (showAboutDialog) {
            TvModalDialog(
                title = if (isAr) "حول التطبيق" else "About Application",
                onDismiss = { showAboutDialog = false },
                maxWidth = 500.dp
            ) { firstRequester ->
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
                        text = "- ولاية غرداية - الجزائر",
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

                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .tvFocusItem(
                                focusRequester = firstRequester,
                                shape = RoundedCornerShape(10.dp),
                                unfocusedBackgroundColor = MaterialTheme.colorScheme.primary,
                                onClick = { showAboutDialog = false }
                            )
                            .padding(horizontal = 28.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isAr) "إغلاق" else "Close",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusItem(
                shape = RoundedCornerShape(12.dp),
                unfocusedBackgroundColor = MaterialTheme.colorScheme.surface,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (isAr) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
