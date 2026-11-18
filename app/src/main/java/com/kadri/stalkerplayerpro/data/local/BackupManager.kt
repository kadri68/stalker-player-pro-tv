package com.kadri.stalkerplayerpro.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * مدير النسخ الاحتياطي الشامل بصيغة JSON:
 * يشمل جميع الحسابات (مع حالة التفضيل والترتيب)، المفضلة، والإعدادات.
 */
object BackupManager {

    suspend fun exportBackupToUri(
        context: Context,
        uri: Uri,
        dao: AppDao,
        settingsStore: SettingsDataStore
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val portals = dao.getAllPortals()
            val favorites = dao.getAllFavorites()
            val settings = settingsStore.settingsFlow.first()

            val portalsArr = JSONArray()
            portals.forEach { p ->
                portalsArr.put(
                    JSONObject().apply {
                        put("name", p.name)
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

            val favsArr = JSONArray()
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

            val settingsObj = JSONObject().apply {
                put("themeId", settings.themeId)
                put("fontScaleId", settings.fontScaleId)
                put("language", settings.language)
                put("parentalPin", settings.parentalPin)
                put("clockCorner", settings.clockCorner)
                put("showClock", settings.showClock)
                put("preferredAudioLang", settings.preferredAudioLang)
                put("openSubApiKey", settings.openSubApiKey)
                put("openSubUsername", settings.openSubUsername)
                put("openSubPassword", settings.openSubPassword)
                put("subdlApiKey", settings.subdlApiKey)
                put("subTextColor", settings.subTextColor)
                put("subFontSizeSp", settings.subFontSizeSp.toDouble())
                put("subBgAlpha", settings.subBgAlpha.toDouble())
            }

            val root = JSONObject().apply {
                put("app", "stalker-player pro")
                put("version", 1)
                put("exportedAtMs", System.currentTimeMillis())
                put("portals", portalsArr)
                put("favorites", favsArr)
                put("settings", settingsObj)
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    suspend fun importBackupFromUri(
        context: Context,
        uri: Uri,
        dao: AppDao,
        settingsStore: SettingsDataStore
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext false
            val root = JSONObject(raw)

            val portalsArr = root.optJSONArray("portals")
            if (portalsArr != null) {
                val importedPortals = ArrayList<PortalEntity>(portalsArr.length())
                for (i in 0 until portalsArr.length()) {
                    val obj = portalsArr.optJSONObject(i) ?: continue
                    val url = obj.optString("url")
                    if (url.isNotBlank()) {
                        importedPortals.add(
                            PortalEntity(
                                name = obj.optString("name", "Portal ${i + 1}"),
                                protocol = obj.optString("protocol", "STALKER"),
                                url = url,
                                mac = obj.optString("mac"),
                                username = obj.optString("username"),
                                password = obj.optString("password"),
                                expiryTimestampMs = obj.optLong("expiryTimestampMs", 0L),
                                isFavorite = obj.optBoolean("isFavorite", false),
                                orderIndex = obj.optInt("orderIndex", i),
                                isActive = obj.optBoolean("isActive", false)
                            )
                        )
                    }
                }
                if (importedPortals.isNotEmpty()) {
                    dao.insertPortals(importedPortals)
                }
            }

            val favsArr = root.optJSONArray("favorites")
            if (favsArr != null) {
                for (i in 0 until favsArr.length()) {
                    val obj = favsArr.optJSONObject(i) ?: continue
                    val itemId = obj.optString("itemId")
                    if (itemId.isNotBlank()) {
                        dao.addFavorite(
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

            val s = root.optJSONObject("settings")
            if (s != null) {
                settingsStore.updateTheme(s.optInt("themeId", 0))
                settingsStore.updateFontScale(s.optInt("fontScaleId", 0))
                settingsStore.updateLanguage(s.optString("language", "ar"))
                settingsStore.updateParentalPin(s.optString("parentalPin", "0000"))
                settingsStore.updateClockCorner(s.optInt("clockCorner", 0))
                settingsStore.updateShowClock(s.optBoolean("showClock", true))
                settingsStore.updatePreferredAudioLang(s.optString("preferredAudioLang", "ar"))
                settingsStore.updateSubtitleAccounts(
                    osApiKey = s.optString("openSubApiKey"),
                    osUser = s.optString("openSubUsername"),
                    osPass = s.optString("openSubPassword"),
                    osToken = "",
                    subdlApiKey = s.optString("subdlApiKey")
                )
                settingsStore.updateSubtitleStyle(
                    color = s.optLong("subTextColor", 0xFFFFFFFF),
                    sizeSp = s.optDouble("subFontSizeSp", 24.0).toFloat(),
                    bgAlpha = s.optDouble("subBgAlpha", 0.65).toFloat()
                )
            }
            true
        }.getOrDefault(false)
    }
}
