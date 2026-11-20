package com.kadri.stalkerplayerpro.data.network

import com.kadri.stalkerplayerpro.data.model.SubtitleCue
import com.kadri.stalkerplayerpro.data.model.SubtitleSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

object SubtitleRepository {

    private const val OS_BASE = "https://api.opensubtitles.com/api/v1"
    private const val SUBDL_BASE = "https://api.subdl.com/api/v1/subtitles"
    private const val USER_AGENT = "StalkerPlayerProTV v2.0"

    @Volatile
    var lastSearchDiagnostic: String = ""

    suspend fun loginOpenSubtitles(apiKey: String, username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || username.isBlank() || password.isBlank()) return@withContext null
            runCatching {
                val payload = JSONObject().apply {
                    put("username", username.trim())
                    put("password", password.trim())
                }.toString()

                val req = Request.Builder()
                    .url("$OS_BASE/login")
                    .header("Api-Key", apiKey.trim())
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) return@use null
                    JSONObject(body).optString("token").takeIf { it.isNotBlank() }
                }
            }.getOrNull()
        }

    suspend fun searchOpenSubtitles(
        apiKey: String,
        jwtToken: String,
        rawQuery: String,
        year: Int? = null,
        languages: String = "ar,en"
    ): List<SubtitleSearchResult> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || rawQuery.isBlank()) return@withContext emptyList()
        val normalizedQuery = rawQuery.trim().lowercase(Locale.US)

        val firstAttempt = executeOsSearch(cleanKey, jwtToken, normalizedQuery, year, languages)
        if (firstAttempt.isNotEmpty()) return@withContext firstAttempt
        if (year != null) {
            val noYearAttempt = executeOsSearch(cleanKey, jwtToken, normalizedQuery, null, languages)
            if (noYearAttempt.isNotEmpty()) return@withContext noYearAttempt
        }
        if (normalizedQuery.contains(":")) {
            val afterColon = normalizedQuery.substringAfterLast(":").trim()
            if (afterColon.length >= 3) {
                val colonAttempt = executeOsSearch(cleanKey, jwtToken, afterColon, year, languages)
                if (colonAttempt.isNotEmpty()) return@withContext colonAttempt
            }
        }
        emptyList()
    }

    private fun executeOsSearch(
        apiKey: String,
        jwtToken: String,
        lowerQuery: String,
        year: Int?,
        languages: String
    ): List<SubtitleSearchResult> {
        return runCatching {
            val sortedLangs = languages.lowercase(Locale.US)
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString(",")
                .ifBlank { "ar,en" }

            val encodedQuery = URLEncoder.encode(lowerQuery, "UTF-8")
            val encodedLangs = URLEncoder.encode(sortedLangs, "UTF-8")
            val fullUrl = buildString {
                append("$OS_BASE/subtitles?languages=$encodedLangs&query=$encodedQuery")
                if (year != null && year > 1900) {
                    append("&year=$year")
                }
            }

            val reqBuilder = Request.Builder()
                .url(fullUrl)
                .header("Api-Key", apiKey)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")

            if (jwtToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${jwtToken.trim()}")
            }

            HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    lastSearchDiagnostic = "OpenSubtitles HTTP ${resp.code}"
                    return emptyList()
                }
                val dataArr = JSONObject(body).optJSONArray("data") ?: return emptyList()
                val results = ArrayList<SubtitleSearchResult>(dataArr.length())

                for (i in 0 until dataArr.length()) {
                    val item = dataArr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val lang = attrs.optString("language", "ar")
                    val files = attrs.optJSONArray("files") ?: continue
                    if (files.length() == 0) continue
                    val firstFile = files.optJSONObject(0) ?: continue
                    val fileId = firstFile.optString("file_id")
                    val fileName = firstFile.optString("file_name", "subtitle_$id.srt")

                    if (fileId.isNotBlank()) {
                        results.add(
                            SubtitleSearchResult(
                                id = id,
                                fileId = fileId,
                                downloadUrl = "",
                                fileName = fileName,
                                language = lang.uppercase(Locale.US),
                                source = "OpenSubtitles"
                            )
                        )
                    }
                }
                results
            }
        }.onFailure {
            lastSearchDiagnostic = "OpenSubtitles: ${it.message ?: "Network error"}"
        }.getOrDefault(emptyList())
    }

    suspend fun searchSubdl(
        apiKey: String,
        rawQuery: String,
        year: Int? = null,
        languages: String = "AR,EN"
    ): List<SubtitleSearchResult> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || rawQuery.isBlank()) return@withContext emptyList()
        val cleanQuery = rawQuery.trim()
        val firstAttempt = executeSubdlSearch(cleanKey, cleanQuery, year, languages)
        if (firstAttempt.isNotEmpty()) return@withContext firstAttempt
        if (year != null) {
            val noYearAttempt = executeSubdlSearch(cleanKey, cleanQuery, null, languages)
            if (noYearAttempt.isNotEmpty()) return@withContext noYearAttempt
        }
        if (cleanQuery.contains(":")) {
            val afterColon = cleanQuery.substringAfterLast(":").trim()
            if (afterColon.length >= 3) {
                val colonAttempt = executeSubdlSearch(cleanKey, afterColon, year, languages)
                if (colonAttempt.isNotEmpty()) return@withContext colonAttempt
            }
        }
        emptyList()
    }

    private fun executeSubdlSearch(
        apiKey: String,
        query: String,
        year: Int?,
        languages: String
    ): List<SubtitleSearchResult> {
        return runCatching {
            val urlBuilder = SUBDL_BASE.toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("film_name", query)
                .addQueryParameter("languages", languages.uppercase(Locale.US))
                .addQueryParameter("subs_per_page", "30")
            if (year != null && year > 1900) {
                urlBuilder.addQueryParameter("year", year.toString())
            }

            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            HttpClientProvider.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    lastSearchDiagnostic = "Subdl HTTP ${resp.code}"
                    return emptyList()
                }
                val json = JSONObject(body)
                if (json.has("status") && !json.optBoolean("status", true)) {
                    val err = json.optString("error", json.optString("message", ""))
                    if (err.isNotBlank()) lastSearchDiagnostic = "Subdl: $err"
                }
                val subsArr = json.optJSONArray("subtitles") ?: return emptyList()
                val results = ArrayList<SubtitleSearchResult>(subsArr.length())

                for (i in 0 until subsArr.length()) {
                    val sub = subsArr.optJSONObject(i) ?: continue
                    val rawUrl = sub.optString("url")
                    val fullUrl = when {
                        rawUrl.startsWith("http") -> rawUrl
                        rawUrl.startsWith("/") -> "https://dl.subdl.com$rawUrl"
                        rawUrl.isNotBlank() -> "https://dl.subdl.com/$rawUrl"
                        else -> ""
                    }
                    val releaseName = sub.optString("release_name")
                        .ifBlank { sub.optString("name", "Subdl_${i + 1}.srt") }
                    val lang = sub.optString("lang", sub.optString("language", "AR"))

                    if (fullUrl.isNotBlank()) {
                        results.add(
                            SubtitleSearchResult(
                                id = "subdl_$i",
                                fileId = "",
                                downloadUrl = fullUrl,
                                fileName = releaseName,
                                language = lang.uppercase(Locale.US),
                                source = "Subdl"
                            )
                        )
                    }
                }
                results
            }
        }.onFailure {
            lastSearchDiagnostic = "Subdl: ${it.message ?: "Network error"}"
        }.getOrDefault(emptyList())
    }

    suspend fun downloadAndParseSubtitle(
        item: SubtitleSearchResult,
        osApiKey: String,
        osJwtToken: String
    ): List<SubtitleCue> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadLink = if (item.source == "OpenSubtitles") {
                resolveOpenSubtitlesDownloadLink(osApiKey.trim(), osJwtToken.trim(), item.fileId)
                    ?: return@withContext emptyList()
            } else {
                item.downloadUrl
            }

            val req = Request.Builder().url(downloadLink).header("User-Agent", USER_AGENT).build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: return@withContext emptyList()
                if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && (entry.name.endsWith(".srt", true) || entry.name.endsWith(".vtt", true))) {
                                return@withContext parseSrtOrVttStream(zis)
                            }
                            entry = zis.nextEntry
                        }
                    }
                    emptyList()
                } else {
                    parseSrtOrVttStream(ByteArrayInputStream(bytes))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun resolveOpenSubtitlesDownloadLink(apiKey: String, jwtToken: String, fileId: String): String? {
        val payload = JSONObject().apply {
            put("file_id", fileId.toIntOrNull() ?: fileId)
        }.toString()

        val reqBuilder = Request.Builder()
            .url("$OS_BASE/download")
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (jwtToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $jwtToken")
        }

        return HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            JSONObject(body).optString("link").takeIf { it.isNotBlank() }
        }
    }

    fun parseSrtOrVttStream(inputStream: InputStream): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>(500)
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val textBuffer = StringBuilder()
        var currentStart = -1L
        var currentEnd = -1L

        reader.forEachLine { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.contains("-->")) {
                if (currentStart >= 0L && textBuffer.isNotEmpty()) {
                    cues.add(SubtitleCue(currentStart, currentEnd, textBuffer.toString().trim()))
                    textBuffer.clear()
                }
                val parts = line.split("-->")
                if (parts.size == 2) {
                    currentStart = parseTimestampToMs(parts[0].trim())
                    currentEnd = parseTimestampToMs(parts[1].trim().substringBefore(" "))
                }
            } else if (line.isEmpty()) {
                if (currentStart >= 0L && textBuffer.isNotEmpty()) {
                    cues.add(SubtitleCue(currentStart, currentEnd, textBuffer.toString().trim()))
                    textBuffer.clear()
                    currentStart = -1L
                    currentEnd = -1L
                }
            } else if (currentStart >= 0L) {
                val cleanText = line.replace(Regex("<[^>]*>"), "")
                if (cleanText.isNotBlank()) {
                    if (textBuffer.isNotEmpty()) textBuffer.append('\n')
                    textBuffer.append(cleanText)
                }
            }
        }
        if (currentStart >= 0L && textBuffer.isNotEmpty()) {
            cues.add(SubtitleCue(currentStart, currentEnd, textBuffer.toString().trim()))
        }
        return cues
    }

    private fun parseTimestampToMs(ts: String): Long {
        val normalized = ts.replace(',', '.')
        val parts = normalized.split(':')
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val s = ((parts[2].toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
                h * 3_600_000L + m * 60_000L + s
            }
            2 -> {
                val m = parts[0].toLongOrNull() ?: 0L
                val s = ((parts[1].toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
                m * 60_000L + s
            }
            else -> 0L
        }
    }
}
