package com.kadri.stalkerplayerpro.data.network

import android.net.Uri
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * المحلل الذكي متعدد الصيغ (Smart Multi-Parser):
 * يستخرج حسابات Stalker و Xtream و M3U تلقائياً من النصوص المختلطة أو ملفات .txt
 * ويفحص صلاحيتها دفعة واحدة بالتوازي
 */
object SmartMultiParser {

    private val urlRegex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val macRegex = Regex("""\b([0-9A-Fa-f]{2}[:\-]){5}[0-9A-Fa-f]{2}\b""")

    fun extractPortalsFromText(rawText: String): List<PortalEntity> {
        val extracted = ArrayList<PortalEntity>()
        val seenKeys = HashSet<String>()

        // 1. تقسيم النص إلى كتل أو أسطر لفحص كل جزء بدقة
        val lines = rawText.lines()
        var lastDetectedUrl: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val urlsInLine = urlRegex.findAll(line).map { it.value }.toList()
            val macsInLine = macRegex.findAll(line).map { it.value.replace('-', ':').uppercase() }.toList()

            // فحص روابط Xtream الكاملة (get.php أو player_api.php)
            for (u in urlsInLine) {
                val uri = runCatching { Uri.parse(u) }.getOrNull()
                val user = uri?.getQueryParameter("username")
                val pass = uri?.getQueryParameter("password")

                if (!user.isNullOrBlank() && !pass.isNullOrBlank() && uri.host != null) {
                    val portPart = if (uri.port != -1) ":${uri.port}" else ""
                    val hostUrl = "${uri.scheme}://${uri.host}$portPart"
                    val key = "XT|$hostUrl|$user|$pass"
                    if (seenKeys.add(key)) {
                        extracted.add(
                            PortalEntity(
                                name = "Xtream (${uri.host})",
                                protocol = "XTREAM",
                                url = hostUrl,
                                username = user,
                                password = pass
                            )
                        )
                    }
                } else if (u.contains(".m3u", ignoreCase = true) || u.contains("type=m3u", ignoreCase = true)) {
                    val key = "M3U|$u"
                    if (seenKeys.add(key)) {
                        val hostLabel = uri?.host ?: "Playlist"
                        extracted.add(
                            PortalEntity(
                                name = "M3U ($hostLabel)",
                                protocol = "M3U",
                                url = u
                            )
                        )
                    }
                } else {
                    lastDetectedUrl = u
                }
            }

            // ربط عنوان MAC مع الرابط في نفس السطر أو السطر السابق
            if (macsInLine.isNotEmpty() && lastDetectedUrl != null) {
                val hostLabel = runCatching { Uri.parse(lastDetectedUrl).host }.getOrNull() ?: "Stalker"
                for (mac in macsInLine) {
                    val key = "STK|$lastDetectedUrl|$mac"
                    if (seenKeys.add(key)) {
                        extracted.add(
                            PortalEntity(
                                name = "Stalker ($hostLabel)",
                                protocol = "STALKER",
                                url = lastDetectedUrl,
                                mac = mac
                            )
                        )
                    }
                }
            }
        }

        // 2. دعم الصيغ النصية المنفصلة (Host: ... User: ... Pass: ...)
        val hostMatch = Regex("""(?:host|server|url)\s*[:=]\s*(https?://[^\s]+)""", RegexOption.IGNORE_CASE).find(rawText)
        val userMatch = Regex("""(?:user|username)\s*[:=]\s*([^\s]+)""", RegexOption.IGNORE_CASE).find(rawText)
        val passMatch = Regex("""(?:pass|password)\s*[:=]\s*([^\s]+)""", RegexOption.IGNORE_CASE).find(rawText)
        if (hostMatch != null && userMatch != null && passMatch != null) {
            val h = hostMatch.groupValues[1].trimEnd('/')
            val u = userMatch.groupValues[1]
            val p = passMatch.groupValues[1]
            val key = "XT|$h|$u|$p"
            if (seenKeys.add(key)) {
                extracted.add(
                    PortalEntity(
                        name = "Xtream (${runCatching { Uri.parse(h).host }.getOrNull() ?: "Server"})",
                        protocol = "XTREAM",
                        url = h,
                        username = u,
                        password = p
                    )
                )
            }
        }

        return extracted
    }

    /**
     * فحص صلاحية قائمة الحسابات دفعة واحدة بالتوازي وإرجاع الشغالة منها فقط
     */
    suspend fun filterWorkingAccounts(portals: List<PortalEntity>): List<PortalEntity> = coroutineScope {
        val semaphore = Semaphore(4)
        portals.map { portal ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    when (portal.protocol) {
                        "STALKER" -> {
                            val (valid, expMs) = StalkerApiClient.checkPortalAccount(portal.url, portal.mac)
                            if (valid) portal.copy(expiryTimestampMs = expMs, statusText = "Active") else null
                        }
                        "XTREAM" -> {
                            val status = XtreamApiClient.checkAccount(portal.url, portal.username, portal.password)
                            if (status.isValid) portal.copy(expiryTimestampMs = status.expiryTimestampMs, statusText = status.statusText) else null
                        }
                        "M3U" -> {
                            val ok = runCatching {
                                val req = Request.Builder().url(portal.url).head().build()
                                HttpClientProvider.client.newCall(req).execute().use { it.isSuccessful }
                            }.getOrDefault(false)
                            if (ok) portal.copy(statusText = "Active") else null
                        }
                        else -> null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }
}
