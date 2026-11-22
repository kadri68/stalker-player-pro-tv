package com.kadri.stalkerplayerpro.data.local

import android.content.Context
import android.os.Environment
import com.kadri.stalkerplayerpro.ui.screens.BUILTIN_FREE_AR_M3U_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupSelectionOptions(
    val includePortals: Boolean = true,
    val selectedPortalKeys: Set<String>? = null,
    val includeFavorites: Boolean = true,
    val includeSubtitleAccounts: Boolean = true,
    val includeAppSettings: Boolean = true
)

data class BackupPortalPreview(
    val uniqueKey: String,
    val name: String,
    val protocol: String,
    val url: String
)

data class BackupInspectionResult(
    val portals: List<BackupPortalPreview>,
    val favoritesCount: Int,
    val hasSubtitleAccounts: Boolean,
    val hasAppSettings: Boolean
)

object BackupManager {

    private const val DEFAULT_BACKUP_FILENAME = "stalker_player_pro_backup.json"

    fun portalBackupKey(url: String, mac: String, username: String): String =
        "${url.trim()}|${mac.trim().uppercase()}|${username.trim()}"

    fun getDefaultBackupFile(context: Context): File {
        val publicDownloads = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }.getOrNull()
        if (publicDownloads != null && (publicDownloads.exists() || publicDownloads.mkdirs()) && publicDownloads.canWrite()) {
            return File(publicDownloads, DEFAULT_BACKUP_FILENAME)
        }
        val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!appDir.exists()) appDir.mkdirs()
        return File(appDir, DEFAULT_BACKUP_FILENAME)
    }

    suspend fun findBackupFilesOnDevice(context: Context): List<File> = withContext(Dispatchers.IO) {
        val candidateDirs = linkedSetOf<File>()
        runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { candidateDirs.add(it) }
        }
        runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { candidateDirs.add(it) }
        }
        runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { candidateDirs.add(it) }
        }
        runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { candidateDirs.add(it) }
        }
        runCatching {
            context.getExternalFilesDir(null)?.let { candidateDirs.add(it) }
        }
        candidateDirs.add(context.filesDir)

        val foundFiles = linkedMapOf<String, File>()
        for (dir in candidateDirs) {
            runCatching {
                if (dir.exists() && dir.isDirectory && dir.canRead()) {
                    val children = dir.listFiles() ?: return@runCatching
                    for (f in children) {
                        if (f.isFile && f.canRead() && f.length() in 10L..15_000_000L &&
                            f.name.endsWith(".json", ignoreCase = true)
                        ) {
                            foundFiles.putIfAbsent(f.absolutePath, f)
                        }
                    }
                }
            }
        }

        foundFiles.values.sortedWith(
            compareByDescending<File> {
                it.name.contains("stalker", ignoreCase = true) || it.name.contains("backup", ignoreCase = true)
            }.thenByDescending { it.lastModified() }
        )
    }

    suspend fun inspectBackupJsonAsync(rawJson: String): BackupInspectionResult? = withContext(Dispatchers.IO) {
        inspectBackupJson(rawJson)
    }

    fun inspectBackupJson(rawJson: String): BackupInspectionResult? {
        return runCatching {
            val clean = rawJson.trim().removePrefix("\uFEFF")
            if (clean.isBlank()) return@runCatching null
            val root = JSONObject(clean)
            val previews = mutableListOf<BackupPortalPreview>()
            val portalsArr = root.optJSONArray("portals")
            if (portalsArr != null) {
                for (i in 0 until portalsArr.length()) {
                    val obj = portalsArr.optJSONObject(i) ?: continue
                    val url = obj.optString("url").trim()
                    if (url.isBlank() || url == BUILTIN_FREE_AR_M3U_URL) continue
                    val mac = obj.optString("mac")
                    val user = obj.optString("username")
                    previews.add(
                        BackupPortalPreview(
                            uniqueKey = portalBackupKey(url, mac, user),
                            name = obj.optString("name", "Portal ${i + 1}").take(10),
                            protocol = obj.optString("protocol", "STALKER"),
                            url = url
                        )
                    )
                }
            }
            val favCount = root.optJSONArray("favorites")?.length() ?: 0
            val s = root.optJSONObject("settings")
            val hasSubs = s != null && (
                s.optString("openSubApiKey").isNotBlank() ||
                s.optString("openSubUsername").isNotBlank() ||
                s.optString("subdlApiKey").isNotBlank() ||
                s.has("subtitleEngine")
            )
            if (previews.isEmpty() && favCount == 0 && s == null) return@runCatching null
            BackupInspectionResult(
                portals = previews,
                favoritesCount = favCount,
                hasSubtitleAccounts = hasSubs,
                hasAppSettings = s != null
            )
        }.getOrNull()
    }

    suspend fun buildBackupJsonString(
        dao: AppDao,
        settingsStore: SettingsDataStore,
        options: BackupSelectionOptions = BackupSelectionOptions()
    ): String = withContext(Dispatchers.IO) {
        val portals = dao.getAllPortals().filter { it.url != BUILTIN_FREE_AR_M3U_URL }
        val favorites = dao.getAllFavorites()
        val settings = settingsStore.settingsFlow.first()

        val portalsArr = JSONArray()
        if (options.includePortals) {
            portals.forEach { p ->
                val key = portalBackupKey(p.url, p.mac, p.username)
                if (options.selectedPortalKeys == null || key in options.selectedPortalKeys) {
                    portalsArr.put(
                        JSONObject().apply {
                            put("name", p.name.take(10))
                            put("protocol", p.protocol)
                            put("url", p.url)
                            put("mac", p.mac)
                            put("username", p.username)
                            put("password", p.password)
                            put("expiryTimestampMs", p.expiryTimestampMs)
                            put("isFavorite", p.isFavorite)
                            put("orderIndex", p.orderIndex)
                            put("isActive", p.isActive)
                        }
                    )
                }
            }
        }

        val favsArr = JSONArray()
        if (options.includeFavorites) {
            favorites.forEach { f ->
                favsArr.put(
                    JSONObject().apply {
                        put("portalId", f.portalId)
                        put("contentType", f.contentType)
                        put("itemId", f.itemId)
                        put("title", f.title)
                        put("logoOrPoster", f.logoOrPoster)
                        put("streamCmdOrUrl", f.streamCmdOrUrl)
                        put("categoryId", f.categoryId)
                    }
                )
            }
        }

        val settingsObj = JSONObject().apply {
            if (options.includeAppSettings) {
                put("themeId", settings.themeId)
                put("fontScaleId", settings.fontScaleId)
                put("language", settings.language)
                put("parentalPin", settings.parentalPin)
                put("clockCorner", settings.clockCorner)
                put("showClock", settings.showClock)
                put("preferredAudioLang", settings.preferredAudioLang)
                put("preferredSubtitleLang", settings.preferredSubtitleLang)
                put("subTextColor", settings.subTextColor)
                put("subFontSizeSp", settings.subFontSizeSp.toDouble())
                put("subBgAlpha", settings.subBgAlpha.toDouble())
            }
            if (options.includeSubtitleAccounts) {
                put("subtitleEngine", settings.subtitleEngine)
                put("openSubApiKey", settings.openSubApiKey)
                put("openSubUsername", settings.openSubUsername)
                put("openSubPassword", settings.openSubPassword)
                put("subdlApiKey", settings.subdlApiKey)
            }
        }

        JSONObject().apply {
            put("app", "stalker-player pro")
            put("version", 2)
            put("exportedAtMs", System.currentTimeMillis())
            put("portals", portalsArr)
            put("favorites", favsArr)
            put("settings", settingsObj)
        }.toString(2)
    }

    suspend fun parseAndApplyBackupJson(
        rawJson: String,
        dao: AppDao,
        settingsStore: SettingsDataStore,
        options: BackupSelectionOptions = BackupSelectionOptions()
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val clean = rawJson.trim().removePrefix("\uFEFF")
            if (clean.isBlank()) return@withContext false
            val root = JSONObject(clean)

            val importedPortals = ArrayList<PortalEntity>()
            if (options.includePortals) {
                val portalsArr = root.optJSONArray("portals")
                if (portalsArr != null) {
                    val existingKeys = dao.getAllPortals()
                        .map { portalBackupKey(it.url, it.mac, it.username) }
                        .toHashSet()
                    for (i in 0 until portalsArr.length()) {
                        val obj = portalsArr.optJSONObject(i) ?: continue
                        val url = obj.optString("url").trim()
                        if (url.isBlank() || url == BUILTIN_FREE_AR_M3U_URL) continue
                        val mac = obj.optString("mac")
                        val user = obj.optString("username")
                        val key = portalBackupKey(url, mac, user)
                        if (options.selectedPortalKeys != null && key !in options.selectedPortalKeys) continue
                        if (key in existingKeys) continue

                        importedPortals.add(
                            PortalEntity(
                                name = obj.optString("name", "Portal ${i + 1}").take(10),
                                protocol = obj.optString("protocol", "STALKER"),
                                url = url,
                                mac = mac,
                                username = user,
                                password = obj.optString("password"),
                                expiryTimestampMs = obj.optLong("expiryTimestampMs", 0L),
                                isFavorite = obj.optBoolean("isFavorite", false),
                                orderIndex = obj.optInt("orderIndex", i),
                                isActive = false
                            )
                        )
                    }
                }
            }

            val importedFavorites = ArrayList<FavoriteEntity>()
            if (options.includeFavorites) {
                val favsArr = root.optJSONArray("favorites")
                if (favsArr != null) {
                    for (i in 0 until favsArr.length()) {
                        val obj = favsArr.optJSONObject(i) ?: continue
                        val itemId = obj.optString("itemId").trim()
                        if (itemId.isNotBlank()) {
                            importedFavorites.add(
                                FavoriteEntity(
                                    portalId = obj.optLong("portalId", 1L),
                                    contentType = obj.optString("contentType", "LIVE"),
                                    itemId = itemId,
                                    title = obj.optString("title"),
                                    logoOrPoster = obj.optString("logoOrPoster"),
                                    streamCmdOrUrl = obj.optString("streamCmdOrUrl"),
                                    categoryId = obj.optString("categoryId")
                                )
                            )
                        }
                    }
                }
            }

            // إدخال جميع البوابات والمفضلات في معاملة واحدة خاطفة لمنع تجمّد التلفاز
            dao.applyBackupBatch(importedPortals, importedFavorites)

            val s = root.optJSONObject("settings")
            if (s != null) {
                if (options.includeAppSettings && s.has("themeId")) {
                    settingsStore.updateTheme(s.optInt("themeId", 0))
                    settingsStore.updateFontScale(s.optInt("fontScaleId", -1))
                    settingsStore.updateLanguage(s.optString("language", "ar"))
                    settingsStore.updateParentalPin(s.optString("parentalPin", "0000"))
                    settingsStore.updateClockCorner(s.optInt("clockCorner", 0))
                    settingsStore.updateShowClock(s.optBoolean("showClock", true))
                    settingsStore.updatePreferredAudioLang(s.optString("preferredAudioLang", "en"))
                    if (s.has("preferredSubtitleLang")) {
                        settingsStore.updatePreferredSubtitleLang(s.optString("preferredSubtitleLang", "ar"))
                    }
                    settingsStore.updateSubtitleStyle(
                        color = s.optLong("subTextColor", 0xFFFFD600),
                        sizeSp = s.optDouble("subFontSizeSp", 38.0).toFloat(),
                        bgAlpha = s.optDouble("subBgAlpha", 0.65).toFloat()
                    )
                }
                if (options.includeSubtitleAccounts && (s.has("openSubApiKey") || s.has("subdlApiKey") || s.has("subtitleEngine"))) {
                    if (s.has("subtitleEngine")) {
                        settingsStore.updateSubtitleEngine(s.optString("subtitleEngine", "BOTH"))
                    }
                    settingsStore.updateSubtitleAccounts(
                        osApiKey = s.optString("openSubApiKey"),
                        osUser = s.optString("openSubUsername"),
                        osPass = s.optString("openSubPassword"),
                        osToken = "",
                        subdlApiKey = s.optString("subdlApiKey")
                    )
                }
            }
            true
        }.getOrDefault(false)
    }

    suspend fun exportToDefaultFolder(
        context: Context,
        dao: AppDao,
        settingsStore: SettingsDataStore,
        options: BackupSelectionOptions = BackupSelectionOptions()
    ): String? = withContext(Dispatchers.IO) {
        val json = buildBackupJsonString(dao, settingsStore, options)
        val candidateDirs = linkedSetOf<File>()

        // 1. مجلد التنزيلات العام
        runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { candidateDirs.add(it) }
        }
        // 2. مجلد المستندات العام
        runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let { candidateDirs.add(it) }
        }
        // 3. مجلد التنزيلات الخاص بالتطبيق على التخزين الخارجي (مضمون الكتابة على Android TV و Android 10+)
        runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { candidateDirs.add(it) }
        }
        // 4. مجلد المستندات الخاص بالتطبيق
        runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { candidateDirs.add(it) }
        }
        // 5. مجلد التطبيق الخارجي الأساسي
        runCatching {
            context.getExternalFilesDir(null)?.let { candidateDirs.add(it) }
        }
        // 6. التخزين الداخلي للتطبيق (لا يفشل إطلاقاً كخيار أخير)
        candidateDirs.add(context.filesDir)

        for (dir in candidateDirs) {
            val success = runCatching {
                if (!dir.exists()) dir.mkdirs()
                val targetFile = File(dir, DEFAULT_BACKUP_FILENAME)
                targetFile.writeText(json, Charsets.UTF_8)
                targetFile.absolutePath
            }.getOrNull()

            if (success != null) {
                return@withContext success
            }
        }
        null
    }
}
