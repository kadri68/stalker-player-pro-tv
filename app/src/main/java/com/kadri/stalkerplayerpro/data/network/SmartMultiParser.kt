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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class SmartParseBreakdown(
    val allExtracted: List<PortalEntity>,
    val stalkerCount: Int,
    val xtreamCount: Int,
    val m3uCount: Int
)

object SmartMultiParser {

    private val urlRegex = Regex("""https?://[^\s"'<>|]+""", RegexOption.IGNORE_CASE)
    private val macRegex = Regex("""\b([0-9A-Fa-f]{2}[:\-]){5}[0-9A-Fa-f]{2}\b""")
    private val hostLabelRegex = Regex("""(?:host|server|url|portal|xtream)\s*[:=]\s*(https?://[^\s"'<>|]+)""", RegexOption.IGNORE_CASE)
    private val userLabelRegex = Regex("""(?:user|username|login|usr)\s*[:=]\s*([^\s"'<>|]+)""", RegexOption.IGNORE_CASE)
    private val passLabelRegex = Regex("""(?:pass|password|pwd)\s*[:=]\s*([^\s"'<>|]+)""", RegexOption.IGNORE_CASE)
    private val colonCredsRegex = Regex("""(https?://[^:\s]+(?::\d+)?):([^:\s/]+):([^:\s/]+)""", RegexOption.IGNORE_CASE)

    /**
     * استخراج اسم البوابة بأول 5 حروف فقط كما هو مطلوب
     */
    fun shortPortalName(rawHostOrName: String, fallback: String = "PORTL"): String {
        val cleaned = rawHostOrName
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore(':')
            .substringBefore('/')
            .trim()
        val chosen = cleaned.ifBlank { fallback }
        return chosen.take(5)
    }

    fun analyzeText(rawText: String): SmartParseBreakdown {
        val extracted = ArrayList<PortalEntity>()
        val seenKeys = HashSet<String>()

        fun addUnique(entity: PortalEntity) {
            val key = when (entity.protocol) {
                "STALKER" -> "STK|${entity.url.trimEnd('/').lowercase()}|${entity.mac.uppercase()}"
                "XTREAM" -> "XT|${entity.url.trimEnd('/').lowercase()}|${entity.username}|${entity.password}"
                else -> "M3U|${entity.url.trim()}"
            }
            if (seenKeys.add(key)) {
                extracted.add(entity.copy(name = shortPortalName(entity.name)))
            }
        }

        // 1. استخراج صيغ Xtream ذات التسميات الصريحة (Host + User + Pass)
        val hostMatches = hostLabelRegex.findAll(rawText).toList()
        val userMatches = userLabelRegex.findAll(rawText).toList()
        val passMatches = passLabelRegex.findAll(rawText).toList()

        if (hostMatches.isNotEmpty() && userMatches.isNotEmpty() && passMatches.isNotEmpty()) {
            for (hMatch in hostMatches) {
                val hPos = hMatch.range.first
                val nearUser = userMatches.minByOrNull { kotlin.math.abs(it.range.first - hPos) }
                val nearPass = passMatches.minByOrNull { kotlin.math.abs(it.range.first - hPos) }
                if (nearUser != null && nearPass != null &&
                    kotlin.math.abs(nearUser.range.first - hPos) < 350 &&
                    kotlin.math.abs(nearPass.range.first - hPos) < 350
                ) {
                    val cleanHost = normalizeXtreamHost(hMatch.groupValues[1])
                    val u = nearUser.groupValues[1].trim()
                    val p = nearPass.groupValues[1].trim()
                    if (cleanHost.isNotBlank() && u.isNotBlank() && p.isNotBlank()) {
                        val hostLabel = runCatching { Uri.parse(cleanHost).host }.getOrNull() ?: "Xtream"
                        addUnique(
                            PortalEntity(
                                name = shortPortalName(hostLabel),
                                protocol = "XTREAM",
                                url = cleanHost,
                                username = u,
                                password = p
                            )
                        )
                    }
                }
            }
        }

        // 2. استخراج صيغة http://host:port:username:password
        for (m in colonCredsRegex.findAll(rawText)) {
            val h = normalizeXtreamHost(m.groupValues[1])
            val u = m.groupValues[2].trim()
            val p = m.groupValues[3].trim()
            if (u.isNotBlank() && p.isNotBlank() && !macRegex.matches(u)) {
                val hostLabel = runCatching { Uri.parse(h).host }.getOrNull() ?: "Xtream"
                addUnique(
                    PortalEntity(
                        name = shortPortalName(hostLabel),
                        protocol = "XTREAM",
                        url = h,
                        username = u,
                        password = p
                    )
                )
            }
        }

        // 3. تحليل سطري مع عزل السياق للتمييز الدقيق بين M3U و Xtream و Stalker
        val lines = rawText.lines()
        var pendingStalkerUrl: String? = null
        var pendingStalkerLineIdx = -10
        val pendingMacs = mutableListOf<String>()
        var pendingMacLineIdx = -10

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                if (i - pendingStalkerLineIdx > 2) pendingStalkerUrl = null
                if (i - pendingMacLineIdx > 2) pendingMacs.clear()
                continue
            }

            val lineUser = userLabelRegex.find(line)?.groupValues?.getOrNull(1)
            val linePass = passLabelRegex.find(line)?.groupValues?.getOrNull(1)
            if (lineUser != null || linePass != null) {
                pendingStalkerUrl = null
                val windowText = lines.subList(maxOf(0, i - 2), minOf(lines.size, i + 3)).joinToString("\n")
                val wUrl = urlRegex.find(windowText)?.value
                val wUser = userLabelRegex.find(windowText)?.groupValues?.getOrNull(1)
                val wPass = passLabelRegex.find(windowText)?.groupValues?.getOrNull(1)
                if (!wUrl.isNullOrBlank() && !wUser.isNullOrBlank() && !wPass.isNullOrBlank()) {
                    val cleanHost = normalizeXtreamHost(wUrl)
                    val hostLabel = runCatching { Uri.parse(cleanHost).host }.getOrNull() ?: "Xtream"
                    addUnique(
                        PortalEntity(
                            name = shortPortalName(hostLabel),
                            protocol = "XTREAM",
                            url = cleanHost,
                            username = wUser.trim(),
                            password = wPass.trim()
                        )
                    )
                }
                continue
            }

            val urlsInLine = urlRegex.findAll(line).map { it.value }.toList()
            val macsInLine = macRegex.findAll(line).map { it.value.replace('-', ':').uppercase() }.toList()

            for (rawUrl in urlsInLine) {
                val uri = runCatching { Uri.parse(rawUrl) }.getOrNull()
                val qUser = uri?.getQueryParameter("username")
                val qPass = uri?.getQueryParameter("password")
                val isExplicitM3u = rawUrl.contains(".m3u", ignoreCase = true) ||
                        rawUrl.contains("type=m3u", ignoreCase = true) ||
                        rawUrl.contains("output=m3u", ignoreCase = true)

                // أ) تصنيف M3U المباشر (يشمل روابط .m3u / .m3u8 وروابط get.php?...&type=m3u_plus)
                if (isExplicitM3u) {
                    pendingStalkerUrl = null
                    val hostLabel = uri?.host ?: "M3U"
                    addUnique(
                        PortalEntity(
                            name = shortPortalName(hostLabel),
                            protocol = "M3U",
                            url = rawUrl.trim()
                        )
                    )
                    continue
                }

                // ب) تصنيف Xtream عبر Query Parameters (player_api.php أو get.php بدون type=m3u)
                if (!qUser.isNullOrBlank() && !qPass.isNullOrBlank() && uri.host != null) {
                    pendingStalkerUrl = null
                    val cleanHost = normalizeXtreamHost(rawUrl)
                    addUnique(
                        PortalEntity(
                            name = shortPortalName(uri.host!!),
                            protocol = "XTREAM",
                            url = cleanHost,
                            username = qUser.trim(),
                            password = qPass.trim()
                        )
                    )
                    continue
                }

                // ج) تصنيف Xtream عبر المسار (مثل http://host:port/live/user/pass/123.ts أو http://host:port/user/pass)
                val pathSegments = uri?.pathSegments.orEmpty()
                if (uri?.host != null && pathSegments.size >= 2 &&
                    !rawUrl.contains("portal.php", true) &&
                    !rawUrl.contains("/c", true)
                ) {
                    val firstSeg = pathSegments[0].lowercase()
                    val candidateUser = if (firstSeg in listOf("live", "movie", "series") && pathSegments.size >= 3) pathSegments[1] else pathSegments[0]
                    val candidatePass = if (firstSeg in listOf("live", "movie", "series") && pathSegments.size >= 3) pathSegments[2] else pathSegments[1]
                    if (candidateUser.isNotBlank() && candidatePass.isNotBlank() &&
                        !candidateUser.contains(".") && !candidatePass.contains(".") &&
                        candidateUser !in listOf("c", "stalker_portal", "portal.php", "player_api.php", "get.php")
                    ) {
                        pendingStalkerUrl = null
                        val cleanHost = normalizeXtreamHost(rawUrl)
                        addUnique(
                            PortalEntity(
                                name = shortPortalName(uri.host!!),
                                protocol = "XTREAM",
                                url = cleanHost,
                                username = candidateUser,
                                password = candidatePass
                            )
                        )
                        continue
                    }
                }

                // د) تصنيف Stalker Portal (مقترن بعنوان MAC في نفس السطر أو ضمن 3 أسطر)
                if (macsInLine.isNotEmpty()) {
                    val hostLabel = uri?.host ?: "Stalker"
                    for (mac in macsInLine) {
                        addUnique(
                            PortalEntity(
                                name = shortPortalName(hostLabel),
                                protocol = "STALKER",
                                url = rawUrl.trim(),
                                mac = mac
                            )
                        )
                    }
                    pendingStalkerUrl = rawUrl.trim()
                    pendingStalkerLineIdx = i
                } else if (pendingMacs.isNotEmpty() && (i - pendingMacLineIdx) <= 3) {
                    val hostLabel = uri?.host ?: "Stalker"
                    for (mac in pendingMacs) {
                        addUnique(
                            PortalEntity(
                                name = shortPortalName(hostLabel),
                                protocol = "STALKER",
                                url = rawUrl.trim(),
                                mac = mac
                            )
                        )
                    }
                    pendingMacs.clear()
                    pendingStalkerUrl = rawUrl.trim()
                    pendingStalkerLineIdx = i
                } else {
                    pendingStalkerUrl = rawUrl.trim()
                    pendingStalkerLineIdx = i
                }
            }

            if (urlsInLine.isEmpty() && macsInLine.isNotEmpty()) {
                val activeUrl = pendingStalkerUrl
                if (activeUrl != null && (i - pendingStalkerLineIdx) <= 4) {
                    val hostLabel = runCatching { Uri.parse(activeUrl).host }.getOrNull() ?: "Stalker"
                    for (mac in macsInLine) {
                        addUnique(
                            PortalEntity(
                                name = shortPortalName(hostLabel),
                                protocol = "STALKER",
                                url = activeUrl,
                                mac = mac
                            )
                        )
                    }
                    pendingStalkerLineIdx = i
                } else {
                    pendingMacs.addAll(macsInLine)
                    pendingMacLineIdx = i
                }
            }
        }

        val stkCount = extracted.count { it.protocol == "STALKER" }
        val xtCount = extracted.count { it.protocol == "XTREAM" }
        val m3uCount = extracted.count { it.protocol == "M3U" }
        return SmartParseBreakdown(extracted, stkCount, xtCount, m3uCount)
    }

    fun extractPortalsFromText(rawText: String): List<PortalEntity> =
        analyzeText(rawText).allExtracted

    private fun normalizeXtreamHost(rawUrl: String): String {
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return rawUrl.trim().trimEnd('/')
        val scheme = uri.scheme ?: "http"
        val host = uri.host ?: return rawUrl.trim().trimEnd('/')
        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        return "$scheme://$host$portPart"
    }

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
                            if (status.isValid) {
                                portal.copy(expiryTimestampMs = status.expiryTimestampMs, statusText = status.statusText)
                            } else null
                        }
                        "M3U" -> {
                            val validM3u = verifyM3uStreamHeader(portal.url)
                            if (validM3u) portal.copy(statusText = "Active") else null
                        }
                        else -> null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun verifyM3uStreamHeader(url: String): Boolean {
        return runCatching {
            val req = Request.Builder().url(url.trim()).get().build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val stream = resp.body?.byteStream() ?: return false
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                    for (lineIdx in 0 until 15) {
                        val line = reader.readLine()?.trim() ?: break
                        if (line.startsWith("#EXTM3U", true) || line.startsWith("#EXTINF", true)) {
                            return true
                        }
                    }
                    false
                }
            }
        }.getOrDefault(false)
    }
}
