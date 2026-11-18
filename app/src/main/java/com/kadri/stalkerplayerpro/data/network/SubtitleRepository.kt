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
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * مستودع الترجمة الخارجية:
 * يدعم OpenSubtitles.com REST API v1 (مع تحويل الاستعلام لأحرف صغيرة لتفادي خطأ 301)
 * و Subdl.com API (مع فك ضغط ملفات ZIP تلقائياً وتحليل SRT / VTT)
 */
object SubtitleRepository {

    private const val OS_BASE = "https://api.opensubtitles.com/api/v1"
    private const val SUBDL_BASE = "https://api.subdl.com/api/v1/subtitles"
    private const val USER_AGENT = "StalkerPlayerProTV v1.0"

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
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
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
        if (apiKey.isBlank() || rawQuery.isBlank()) return@withContext emptyList()
        // تحويل الاستعلام لأحرف صغيرة لتجنب خطأ إعادة التوجيه 301 في OpenSubtitles v1
        val normalizedQuery = rawQuery.trim().lowercase(Locale.US)

        val firstAttempt = executeOsSearch(apiKey, jwtToken, normalizedQuery, year, languages)
        if (firstAttempt.isNotEmpty() || year == null) {
            return@withContext firstAttempt
        }
        // بحث احتياطي تلقائي بدون سنة الإنتاج إذا لم تظهر نتائج
        executeOsSearch(apiKey, jwtToken, normalizedQuery, null, languages)
    }

    private fun executeOsSearch(
        apiKey: String,
        jwtToken: String,
        lowerQuery: String,
        year: Int?,
        languages: String
    ): List<SubtitleSearchResult> {
        return runCatching {
            val urlBuilder = "$OS_BASE/subtitles".toHttpUrl().newBuilder()
                .addQueryParameter("query", lowerQuery)
                .addQueryParameter("languages", languages.lowercase(Locale.US))
            if (year != null && year > 1900) {
                urlBuilder.addQueryParameter("year", year.toString())
            }

            val reqBuilder = Request.Builder()
                .url(urlBuilder.build())
                .header("Api-Key", apiKey.trim())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")

            if (jwtToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${jwtToken.trim()}")
            }

            HttpClientProvider.client.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
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
        }.getOrDefault(emptyList())
    }

    suspend fun searchSubdl(
        apiKey: String,
        rawQuery: String,
        year: Int? = null,
        languages: String = "AR,EN"
    ): List<SubtitleSearchResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || rawQuery.isBlank()) return@withContext emptyList()
        val cleanQuery = rawQuery.trim()
        val firstAttempt = executeSubdlSearch(apiKey, cleanQuery, year, languages)
        if (firstAttempt.isNotEmpty() || year == null) {
            return@withContext firstAttempt
        }
        // بحث احتياطي تلقائي بدون سنة الإنتاج
        executeSubdlSearch(apiKey, cleanQuery, null, languages)
    }

    private fun executeSubdlSearch(
        apiKey: String,
        query: String,
        year: Int?,
        languages: String
    ): List<SubtitleSearchResult> {
        return runCatching {
            val urlBuilder = SUBDL_BASE.toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey.trim())
                .addQueryParameter("film_name", query)
                .addQueryParameter("languages", languages.uppercase(Locale.US))
            if (year != null && year > 1900) {
                urlBuilder.addQueryParameter("year", year.toString())
            }

            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", USER_AGENT)
                .build()

            HttpClientProvider.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val subsArr = JSONObject(body).optJSONArray("subtitles") ?: return emptyList()
                val results = ArrayList<SubtitleSearchResult>(subsArr.length())

                for (i in 0 until subsArr.length()) {
                    val sub = subsArr.optJSONObject(i) ?: continue
                    val rawUrl = sub.optString("url")
                    val fullUrl = if (rawUrl.startsWith("http")) rawUrl else "https://dl.subdl.com$rawUrl"
                    val releaseName = sub.optString("release_name", "Subdl_${i + 1}.srt")
                    val lang = sub.optString("lang", "AR")

                    if (rawUrl.isNotBlank()) {
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
        }.getOrDefault(emptyList())
    }

    suspend fun downloadAndParseSubtitle(
        item: SubtitleSearchResult,
        osApiKey: String,
        osJwtToken: String
    ): List<SubtitleCue> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadLink = if (item.source == "OpenSubtitles") {
                resolveOpenSubtitlesDownloadLink(osApiKey, osJwtToken, item.fileId) ?: return@withContext emptyList()
            } else {
                item.downloadUrl
            }

            val req = Request.Builder().url(downloadLink).header("User-Agent", USER_AGENT).build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: return@withContext emptyList()
                // إذا كان الملف مضغوطاً بصيغة ZIP (شائع في Subdl) نفك أول ملف SRT/VTT بداخله
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
            .header("Api-Key", apiKey.trim())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (jwtToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${jwtToken.trim()}")
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
