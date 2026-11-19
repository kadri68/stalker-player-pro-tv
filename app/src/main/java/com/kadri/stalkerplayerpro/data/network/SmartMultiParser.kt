package com.kadri.stalkerplayerpro.data.network

import android.net.Uri
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    fun shortPortalName(rawHostOrName: String, fallback: String = "PORTL"): String {
        val cleaned = rawHostOrName
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore(':')
            .substringBefore('/')
            .trim()
        val chosen = cleaned.ifBlank { fallback }
        return chosen.take(10)
    }

    private fun mergeBrokenUrlLines(rawText: String): List<String> {
        val rawLines = rawText.lines()
        val merged = ArrayList<String>(rawLines.size)
        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                merged.add("")
                continue
            }
            if (merged.isNotEmpty() &&
                (trimmed.startsWith("/") || trimmed.startsWith("?") || trimmed.startsWith("&")) &&
                !trimmed.contains(" ")
            ) {
                val prev = merged.last()
                if (prev.contains("http://", true) || prev.contains("https://", true)) {
                    merged[merged.lastIndex] = prev.trimEnd('/') + trimmed
                    continue
                }
            }
            merged.add(trimmed)
        }
        return merged
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

        val lines = mergeBrokenUrlLines(rawText)
        val normalizedFullText = lines.joinToString("\n")

        // 1. صيغ Xtream ذات التسميات الصريحة (Host + User + Pass)
        val hostMatches = hostLabelRegex.findAll(normalizedFullText).toList()
        val userMatches = userLabelRegex.findAll(normalizedFullText).toList()
        val passMatches = passLabelRegex.findAll(normalizedFullText).toList()

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

        // 2. صيغة http://host:port:username:password
        for (m in colonCredsRegex.findAll(normalizedFullText)) {
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

        // 3. تحليل سطري مع دعم تعدد MAC لنفس الهوست والتقاط Xtream من كافة الروابط
        var pendingStalkerUrl: String? = null
        var pendingStalkerLineIdx = -10
        val pendingMacs = mutableListOf<String>()
        var pendingMacLineIdx = -10

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

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
                val qUser = uri?.getQueryParameter("username") ?: uri?.getQueryParameter("user")
                val qPass = uri?.getQueryParameter("password") ?: uri?.getQueryParameter("pass")

                // أ) التقاط Xtream من أي رابط يحتوي username و password (يشمل get.php?...&type=m3u_plus)
                if (!qUser.isNullOrBlank() && !qPass.isNullOrBlank() && uri?.host != null) {
                    pendingStalkerUrl = null
                    val cleanHost = normalizeXtreamHost(rawUrl)
                    addUnique(
                        PortalEntity(
                            name = shortPortalName(uri?.host ?: "Xtream"),
                            protocol = "XTREAM",
                            url = cleanHost,
                            username = qUser.trim(),
                            password = qPass.trim()
                        )
                    )
                    continue
                }

                // ب) التقاط Xtream من روابط المسار (/live/user/pass/123.ts أو /user/pass)
                val pathSegments = uri?.pathSegments.orEmpty()
                if (uri?.host != null && pathSegments.size >= 2 &&
                    !rawUrl.contains("portal.php", true) &&
                    !rawUrl.endsWith("/c", true) &&
                    !rawUrl.endsWith("/c/", true)
                ) {
                    val firstSeg = pathSegments[0].lowercase()
                    val candidateUser = if (firstSeg in listOf("live", "movie", "series") && pathSegments.size >= 3) pathSegments[1] else pathSegments[0]
                    val candidatePass = if (firstSeg in listOf("live", "movie", "series") && pathSegments.size >= 3) pathSegments[2] else pathSegments[1]
                    if (candidateUser.isNotBlank() && candidatePass.isNotBlank() &&
                        !candidateUser.contains(".") && !candidatePass.contains(".") &&
                        candidateUser.lowercase() !in listOf("c", "stalker_portal", "portal.php", "player_api.php", "get.php", "xmltv.php")
                    ) {
                        pendingStalkerUrl = null
                        val cleanHost = normalizeXtreamHost(rawUrl)
                        addUnique(
                            PortalEntity(
                                name = shortPortalName(uri?.host ?: "Xtream"),
                                protocol = "XTREAM",
                                url = cleanHost,
                                username = candidateUser,
                                password = candidatePass
                            )
                        )
                        continue
                    }
                }

                // ج) روابط M3U / M3U8 المباشرة
                if (rawUrl.contains(".m3u", ignoreCase = true) || rawUrl.contains("type=m3u", ignoreCase = true)) {
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

                // د) رابط Stalker Portal (مع دعم تعدد عناوين MAC)
                pendingStalkerUrl = rawUrl.trim()
                pendingStalkerLineIdx = i
                val hostLabel = uri?.host ?: "Stalker"

                if (macsInLine.isNotEmpty()) {
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
                } else if (pendingMacs.isNotEmpty() && (i - pendingMacLineIdx) <= 4) {
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
                }
            }

            if (urlsInLine.isEmpty() && macsInLine.isNotEmpty()) {
                val activeUrl = pendingStalkerUrl
                if (activeUrl != null && (i - pendingStalkerLineIdx) <= 8) {
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
        val host = uri?.host ?: return rawUrl.trim().trimEnd('/')
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
