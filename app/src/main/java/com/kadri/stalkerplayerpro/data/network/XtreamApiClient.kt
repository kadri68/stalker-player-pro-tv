package com.kadri.stalkerplayerpro.data.network

import android.util.Base64
import com.kadri.stalkerplayerpro.data.local.PortalEntity
import com.kadri.stalkerplayerpro.data.model.CategoryItem
import com.kadri.stalkerplayerpro.data.model.ChannelItem
import com.kadri.stalkerplayerpro.data.model.EpgProgram
import com.kadri.stalkerplayerpro.data.model.EpisodeItem
import com.kadri.stalkerplayerpro.data.model.VodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class XtreamAccountStatus(
    val isValid: Boolean,
    val expiryTimestampMs: Long,
    val statusText: String
)

object XtreamApiClient {

    suspend fun checkAccount(baseUrl: String, username: String, password: String): XtreamAccountStatus =
        withContext(Dispatchers.IO) {
            runCatching {
                val host = baseUrl.trimEnd('/')
                val url = "$host/player_api.php?username=$username&password=$password"
                val request = Request.Builder().url(url).build()
                HttpClientProvider.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext XtreamAccountStatus(false, 0L, "HTTP ${response.code}")
                    }
                    val bodyStr = response.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val userInfo = json.optJSONObject("user_info")
                        ?: return@withContext XtreamAccountStatus(false, 0L, "Invalid Response")

                    val auth = userInfo.optInt("auth", 0)
                    val status = userInfo.optString("status", "")
                    val expSec = userInfo.optString("exp_date", "0").toLongOrNull() ?: 0L
                    val expMs = if (expSec > 0L) expSec * 1000L else 0L
                    val valid = auth == 1 && status.equals("Active", ignoreCase = true)
                    XtreamAccountStatus(valid, expMs, status.ifBlank { if (valid) "Active" else "Inactive" })
                }
            }.getOrElse {
                XtreamAccountStatus(false, 0L, it.message ?: "Error")
            }
        }

    suspend fun getCategories(portal: PortalEntity, action: String): List<CategoryItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val host = portal.url.trimEnd('/')
                val url = "$host/player_api.php?username=${portal.username}&password=${portal.password}&action=$action"
                val request = Request.Builder().url(url).build()
                HttpClientProvider.client.newCall(request).execute().use { response ->
                    val stream = response.body?.byteStream() ?: return@withContext emptyList()
                    StreamingJsonParser.parseXtreamCategories(stream)
                }
            }.getOrDefault(emptyList())
        }

    suspend fun getLiveStreams(portal: PortalEntity): List<ChannelItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val host = portal.url.trimEnd('/')
                val url = "$host/player_api.php?username=${portal.username}&password=${portal.password}&action=get_live_streams"
                val request = Request.Builder().url(url).build()
                HttpClientProvider.client.newCall(request).execute().use { response ->
                    val stream = response.body?.byteStream() ?: return@withContext emptyList()
                    StreamingJsonParser.parseXtreamLiveStreams(
                        inputStream = stream,
                        portalId = portal.id,
                        baseUrl = host,
                        username = portal.username,
                        password = portal.password
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun getVodStreams(portal: PortalEntity, categoryId: String? = null): List<VodItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val host = portal.url.trimEnd('/')
                val catParam = if (!categoryId.isNullOrBlank() && categoryId != "ALL") "&category_id=$categoryId" else ""
                val url = "$host/player_api.php?username=${portal.username}&password=${portal.password}&action=get_vod_streams$catParam"
                val request = Request.Builder().url(url).build()
                HttpClientProvider.client.newCall(request).execute().use { response ->
                    val stream = response.body?.byteStream() ?: return@withContext emptyList()
                    StreamingJsonParser.parseXtreamVodStreams(
                        inputStream = stream,
                        portalId = portal.id,
                        baseUrl = host,
                        username = portal.username,
                        password = portal.password
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun getSeriesList(portal: PortalEntity, categoryId: String? = null): List<VodItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val host = portal.url.trimEnd('/')
                val catParam = if (!categoryId.isNullOrBlank() && categoryId != "ALL") "&category_id=$categoryId" else ""
                val url = "$host/player_api.php?username=${portal.username}&password=${portal.password}&action=get_series$catParam"
                val request = Request.Builder().url(url).build()
                HttpClientProvider.client.newCall(request).execute().use { response ->
                    val stream = response.body?.byteStream() ?: return@withContext emptyList()
                    StreamingJsonParser.parseXtreamSeriesList(stream, portal.id)
                }
            }.getOrDefault(emptyList())
        }

    suspend fun getSeriesEpisodes(portal: PortalEntity, seriesId: String): Map<Int, List<EpisodeItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val host = portal.url.trimEnd('/')
                val url = "$host/player_api.php?username=${portal.username}&password=${portal.password}&action=get_series_info&series_id=$seriesId"
                val request = Request.Builder().url(url).build()
                HttpClientProvider.client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val episodesObj = json.optJSONObject("episodes") ?: return@withContext emptyMap()
                    val result = sortedMapOf<Int, List<EpisodeItem>>()

                    val keys = episodesObj.keys()
                    while (keys.hasNext()) {
                        val seasonKey = keys.next()
                        val seasonNum = seasonKey.toIntOrNull() ?: 1
                        val arr = episodesObj.optJSONArray(seasonKey) ?: continue
                        val list = ArrayList<EpisodeItem>(arr.length())
                        for (i in 0 until arr.length()) {
                            val ep = arr.optJSONObject(i) ?: continue
                            val epId = ep.optString("id")
                            val epNum = ep.optInt("episode_num", i + 1)
                            val title = ep.optString("title", "Episode $epNum")
                            val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                            val streamUrl = "$host/series/${portal.username}/${portal.password}/$epId.$ext"
                            list.add(
                                EpisodeItem(
                                    uniqueKey = "${portal.id}_xt_ep_${epId}_${seasonNum}_$i",
                                    id = epId,
                                    seasonNumber = seasonNum,
                                    episodeNumber = epNum,
                                    title = title,
                                    containerExtension = ext,
                                    streamCmdOrUrl = streamUrl
                                )
                            )
                        }
                        result[seasonNum] = list
                    }
                    result
                }
            }.getOrDefault(emptyMap())
        }

    suspend fun getShortEpg(portal: PortalEntity, streamId: String): List<EpgProgram> =
        withContext(Dispatchers.IO) {
            runCatching {
                val host = portal.url.trimEnd('/')
                val url = "$host/player_api.php?username=${portal.username}&password=${portal.password}&action=get_short_epg&stream_id=$streamId&limit=6"
                val request = Request.Builder().url(url).build()
                HttpClientProvider.client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val arr = json.optJSONArray("epg_listings") ?: return@withContext emptyList()
                    val programs = ArrayList<EpgProgram>(arr.length())
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val rawTitle = item.optString("title")
                        val decodedTitle = decodeBase64Safe(rawTitle)
                        val startTs = item.optLong("start_timestamp", 0L) * 1000L
                        val stopTs = item.optLong("stop_timestamp", 0L) * 1000L
                        val desc = decodeBase64Safe(item.optString("description"))
                        if (decodedTitle.isNotBlank()) {
                            programs.add(EpgProgram(decodedTitle, startTs, stopTs, desc))
                        }
                    }
                    programs
                }
            }.getOrDefault(emptyList())
        }

    private fun decodeBase64Safe(input: String): String {
        if (input.isBlank()) return ""
        return runCatching {
            val bytes = Base64.decode(input, Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8)
        }.getOrDefault(input)
    }
}
