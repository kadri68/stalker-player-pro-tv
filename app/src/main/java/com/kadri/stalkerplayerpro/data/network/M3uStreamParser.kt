package com.kadri.stalkerplayerpro.data.network

import com.kadri.stalkerplayerpro.data.model.CategoryItem
import com.kadri.stalkerplayerpro.data.model.ChannelItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * محلل قوائم M3U/M3U8 سطراً بسطر (Streaming Line-by-Line)
 * لتقليل استهلاك الذاكرة RAM إلى الحد الأدنى
 */
object M3uStreamParser {

    private val logoRegex = Regex("""tvg-logo="([^"]*)"""")
    private val groupRegex = Regex("""group-title="([^"]*)"""")
    private val idRegex = Regex("""tvg-id="([^"]*)"""")

    suspend fun parseFromUrl(url: String, portalId: Long): Pair<List<CategoryItem>, List<ChannelItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url.trim()).build()
                HttpClientProvider.client.newCall(request).execute().use { response ->
                    val stream = response.body?.byteStream() ?: return@withContext (emptyList<CategoryItem>() to emptyList())
                    parseFromStream(stream, portalId)
                }
            }.getOrDefault(emptyList<CategoryItem>() to emptyList())
        }

    fun parseFromStream(inputStream: InputStream, portalId: Long): Pair<List<CategoryItem>, List<ChannelItem>> {
        val channels = ArrayList<ChannelItem>(512)
        val groupSet = LinkedHashSet<String>()

        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            var pendingName = ""
            var pendingLogo = ""
            var pendingGroup = "General"
            var pendingEpgId = ""
            var index = 0

            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    pendingLogo = logoRegex.find(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                    pendingGroup = groupRegex.find(trimmed)?.groupValues?.getOrNull(1)?.ifBlank { "General" } ?: "General"
                    pendingEpgId = idRegex.find(trimmed)?.groupValues?.getOrNull(1).orEmpty()
                    val commaIdx = trimmed.lastIndexOf(',')
                    pendingName = if (commaIdx != -1 && commaIdx + 1 < trimmed.length) {
                        trimmed.substring(commaIdx + 1).trim()
                    } else {
                        "Channel ${index + 1}"
                    }
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    index++
                    groupSet.add(pendingGroup)
                    channels.add(
                        ChannelItem(
                            uniqueKey = "${portalId}_m3u_$index",
                            id = index.toString(),
                            number = index,
                            name = pendingName.ifBlank { "Channel $index" },
                            logoUrl = pendingLogo,
                            categoryId = pendingGroup,
                            categoryName = pendingGroup,
                            streamCmdOrUrl = trimmed,
                            epgChannelId = pendingEpgId
                        )
                    )
                    pendingName = ""
                    pendingLogo = ""
                    pendingEpgId = ""
                }
                line = reader.readLine()
            }
        }

        val categories = groupSet.map { CategoryItem(id = it, name = it) }
        return categories to channels
    }
}
