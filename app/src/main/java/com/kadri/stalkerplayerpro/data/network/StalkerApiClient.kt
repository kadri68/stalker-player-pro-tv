package com.kadri.stalkerplayerpro.data.network

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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object StalkerApiClient {

    private val tokenCache = ConcurrentHashMap<String, String>()

    private fun resolveEndpoint(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        return when {
            trimmed.endsWith(".php") -> trimmed
            trimmed.endsWith("/c") -> "${trimmed.removeSuffix("/c")}/portal.php"
            else -> "$trimmed/portal.php"
        }
    }

    private fun buildRequest(endpoint: String, query: String, mac: String, token: String? = null): Request {
        val fullUrl = "$endpoint?$query"
        val builder = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Cookie", "mac=${mac.trim()}; stb_lang=en; timezone=UTC")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    suspend fun handshake(rawUrl: String, mac: String): String? = withContext(Dispatchers.IO) {
        val cacheKey = "${rawUrl.trim()}|${mac.trim()}"
        runCatching {
            val endpoint = resolveEndpoint(rawUrl)
            val req = buildRequest(endpoint, "type=stb&action=handshake&token=&JsHttpRequest=1-xml", mac)
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val js = JSONObject(body).optJSONObject("js")
                val token = js?.optString("token").orEmpty()
                if (token.isNotBlank()) {
                    tokenCache[cacheKey] = token
                    token
                } else null
            }
        }.getOrNull()
    }

    private suspend fun getOrFetchToken(portal: PortalEntity): String? {
        val cacheKey = "${portal.url.trim()}|${portal.mac.trim()}"
        return tokenCache[cacheKey] ?: handshake(portal.url, portal.mac)
    }

    suspend fun checkPortalAccount(rawUrl: String, mac: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val token = handshake(rawUrl, mac) ?: return@withContext (false to 0L)
        runCatching {
            val endpoint = resolveEndpoint(rawUrl)
            val req = buildRequest(endpoint, "type=account_info&action=get_main_info&JsHttpRequest=1-xml", mac, token)
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val js = JSONObject(body).optJSONObject("js")
                val phoneStr = js?.optString("phone").orEmpty()
                val expMs = parseExpiryDate(phoneStr)
                (true to expMs)
            }
        }.getOrDefault(true to 0L)
    }

    private fun parseExpiryDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val formats = listOf("MMMM d, yyyy, h:mm a", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd.MM.yyyy")
        for (fmt in formats) {
            runCatching {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val parsed = sdf.parse(dateStr.trim())
                if (parsed != null) return parsed.time
            }
        }
        return 0L
    }

    suspend fun getItvGenres(portal: PortalEntity): List<CategoryItem> = withContext(Dispatchers.IO) {
        val token = getOrFetchToken(portal) ?: return@withContext emptyList()
        runCatching {
            val endpoint = resolveEndpoint(portal.url)
            val req = buildRequest(endpoint, "type=itv&action=get_genres&JsHttpRequest=1-xml", portal.mac, token)
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("js") ?: return@withContext emptyList()
                val list = ArrayList<CategoryItem>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val title = obj.optString("title")
                    if (id.isNotBlank() && title.isNotBlank() && id != "*") {
                        list.add(CategoryItem(id = id, name = title))
                    }
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    suspend fun getAllChannels(portal: PortalEntity): List<ChannelItem> = withContext(Dispatchers.IO) {
        val token = getOrFetchToken(portal) ?: return@withContext emptyList()
        runCatching {
            val endpoint = resolveEndpoint(portal.url)
            val req = buildRequest(endpoint, "type=itv&action=get_all_channels&JsHttpRequest=1-xml", portal.mac, token)
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                val data = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("js")
                    ?.optJSONArray("data") ?: return@withContext emptyList()

                val list = ArrayList<ChannelItem>(data.length())
                for (i in 0 until data.length()) {
                    val ch = data.optJSONObject(i) ?: continue
                    val id = ch.optString("id")
                    val num = ch.optString("number").toIntOrNull() ?: (i + 1)
                    val name = ch.optString("name")
                    val logo = ch.optString("logo")
                    val genreId = ch.optString("tv_genre_id")
                    val cmd = ch.optString("cmd")
                    val curProg = ch.optJSONArray("cur_playing")?.optJSONObject(0)?.optString("name").orEmpty()
                    if (id.isNotBlank() && name.isNotBlank()) {
                        list.add(
                            ChannelItem(
                                uniqueKey = "${portal.id}_stk_live_${id}_$indexOrI(i)",
                                id = id,
                                number = num,
                                name = name,
                                logoUrl = logo,
                                categoryId = genreId,
                                streamCmdOrUrl = cmd,
                                epgNowTitle = curProg
                            )
                        )
                    }
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    private fun indexOrI(i: Int): Int = i

    suspend fun getVodCategories(portal: PortalEntity, type: String = "vod"): List<CategoryItem> =
        withContext(Dispatchers.IO) {
            val token = getOrFetchToken(portal) ?: return@withContext emptyList()
            runCatching {
                val endpoint = resolveEndpoint(portal.url)
                val req = buildRequest(endpoint, "type=$type&action=get_categories&JsHttpRequest=1-xml", portal.mac, token)
                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("js") ?: return@withContext emptyList()
                    val list = ArrayList<CategoryItem>(arr.length())
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        val title = obj.optString("title")
                        if (id.isNotBlank() && title.isNotBlank() && id != "*") {
                            list.add(CategoryItem(id = id, name = title))
                        }
                    }
                    list
                }
            }.getOrDefault(emptyList())
        }

    suspend fun getVodOrderedList(
        portal: PortalEntity,
        type: String = "vod",
        categoryId: String = "*",
        page: Int = 1,
        searchQuery: String = ""
    ): List<VodItem> = withContext(Dispatchers.IO) {
        val token = getOrFetchToken(portal) ?: return@withContext emptyList()
        runCatching {
            val endpoint = resolveEndpoint(portal.url)
            val encodedSearch = URLEncoder.encode(searchQuery, "UTF-8")
            val q = "type=$type&action=get_ordered_list&category=$categoryId&p=$page&search=$encodedSearch&JsHttpRequest=1-xml"
            val req = buildRequest(endpoint, q, portal.mac, token)
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                val data = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("js")
                    ?.optJSONArray("data") ?: return@withContext emptyList()

                val list = ArrayList<VodItem>(data.length())
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name")
                    val screenshot = obj.optString("screenshot_uri")
                    val year = obj.optString("year")
                    val rating = obj.optString("rating_imdb")
                    val cmd = obj.optString("cmd")
                    val isSeriesFlag = type == "series" || obj.optInt("is_series", 0) == 1
                    if (id.isNotBlank() && name.isNotBlank()) {
                        list.add(
                            VodItem(
                                uniqueKey = "${portal.id}_stk_${type}_${id}_p${page}_$i",
                                id = id,
                                name = name,
                                posterUrl = screenshot,
                                categoryId = categoryId,
                                year = year,
                                rating = rating,
                                streamCmdOrUrl = cmd,
                                isSeries = isSeriesFlag
                            )
                        )
                    }
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    /**
     * جلب مواسم وحلقات المسلسل كاملة في Stalker Portal
     */
    suspend fun getSeriesSeasonsAndEpisodes(
        portal: PortalEntity,
        seriesItem: VodItem
    ): Map<Int, List<EpisodeItem>> = withContext(Dispatchers.IO) {
        val token = getOrFetchToken(portal) ?: return@withContext emptyMap()
        val endpoint = resolveEndpoint(portal.url)
        val result = sortedMapOf<Int, List<EpisodeItem>>()

        val typesToTry = listOf("series", "vod")
        for (stkType in typesToTry) {
            runCatching {
                val q = "type=$stkType&action=get_ordered_list&movie_id=${seriesItem.id}&p=1&JsHttpRequest=1-xml"
                val req = buildRequest(endpoint, q, portal.mac, token)
                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    val data = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("js")
                        ?.optJSONArray("data") ?: return@use

                    for (i in 0 until data.length()) {
                        val seasonObj = data.optJSONObject(i) ?: continue
                        val seasonId = seasonObj.optString("id")
                        val seasonName = seasonObj.optString("name", "Season ${i + 1}")
                        val seasonNum = Regex("""\d+""").find(seasonName)?.value?.toIntOrNull() ?: (i + 1)
                        val seasonCmd = seasonObj.optString("cmd").ifBlank { seriesItem.streamCmdOrUrl }
                        val seriesArr = seasonObj.optJSONArray("series")

                        val epList = ArrayList<EpisodeItem>()
                        if (seriesArr != null && seriesArr.length() > 0) {
                            for (eIdx in 0 until seriesArr.length()) {
                                val epNum = seriesArr.optInt(eIdx, eIdx + 1)
                                epList.add(
                                    EpisodeItem(
                                        uniqueKey = "${portal.id}_stk_ep_${seriesItem.id}_s${seasonNum}_e${epNum}_$eIdx",
                                        id = "${seasonId}_$epNum",
                                        seasonNumber = seasonNum,
                                        episodeNumber = epNum,
                                        title = "Episode $epNum",
                                        streamCmdOrUrl = "$seasonCmd||SERIES_EP:$epNum"
                                    )
                                )
                            }
                        } else if (seasonId.isNotBlank()) {
                            // جلب الحلقات كقائمة فرعية للموسم
                            val epQuery = "type=$stkType&action=get_ordered_list&movie_id=${seriesItem.id}&season_id=$seasonId&p=1&JsHttpRequest=1-xml"
                            val epReq = buildRequest(endpoint, epQuery, portal.mac, token)
                            HttpClientProvider.client.newCall(epReq).execute().use { epResp ->
                                val epData = JSONObject(epResp.body?.string().orEmpty())
                                    .optJSONObject("js")
                                    ?.optJSONArray("data")
                                if (epData != null) {
                                    for (eIdx in 0 until epData.length()) {
                                        val epObj = epData.optJSONObject(eIdx) ?: continue
                                        val epId = epObj.optString("id", "${seasonId}_$eIdx")
                                        val epTitle = epObj.optString("name", "Episode ${eIdx + 1}")
                                        val epCmd = epObj.optString("cmd").ifBlank { seasonCmd }
                                        val epNum = epObj.optInt("series_number", eIdx + 1)
                                        epList.add(
                                            EpisodeItem(
                                                uniqueKey = "${portal.id}_stk_ep_${epId}_s${seasonNum}_$eIdx",
                                                id = epId,
                                                seasonNumber = seasonNum,
                                                episodeNumber = epNum,
                                                title = epTitle,
                                                streamCmdOrUrl = "$epCmd||SERIES_EP:$epNum"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        if (epList.isNotEmpty()) {
                            result[seasonNum] = epList
                        }
                    }
                }
            }
            if (result.isNotEmpty()) break
        }

        if (result.isEmpty()) {
            result[1] = listOf(
                EpisodeItem(
                    uniqueKey = "${seriesItem.uniqueKey}_ep1",
                    id = seriesItem.id,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    title = seriesItem.name,
                    streamCmdOrUrl = seriesItem.streamCmdOrUrl
                )
            )
        }
        result
    }

    suspend fun createLink(portal: PortalEntity, cmd: String, type: String = "itv"): String =
        withContext(Dispatchers.IO) {
            val parts = cmd.split("||SERIES_EP:")
            val actualCmd = parts[0]
            val seriesEpNum = parts.getOrNull(1)?.trim()

            val cleanCmd = actualCmd.removePrefix("ffmpeg ").trim()
            if (seriesEpNum.isNullOrBlank() && (cleanCmd.startsWith("http://") || cleanCmd.startsWith("https://"))) {
                if (!cleanCmd.contains("localhost") && !cleanCmd.contains("/ch/")) {
                    return@withContext cleanCmd
                }
            }
            val token = getOrFetchToken(portal) ?: return@withContext cleanCmd
            runCatching {
                val endpoint = resolveEndpoint(portal.url)
                val encodedCmd = URLEncoder.encode(actualCmd, "UTF-8")
                val seriesParam = if (!seriesEpNum.isNullOrBlank()) "&series=$seriesEpNum" else ""
                val resolvedType = if (!seriesEpNum.isNullOrBlank()) "vod" else type
                val q = "type=$resolvedType&action=create_link&cmd=$encodedCmd$seriesParam&JsHttpRequest=1-xml"
                val req = buildRequest(endpoint, q, portal.mac, token)
                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    val rawLink = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("js")
                        ?.optString("cmd")
                        .orEmpty()
                    rawLink.removePrefix("ffmpeg ").trim().ifBlank { cleanCmd }
                }
            }.getOrDefault(cleanCmd)
        }

    suspend fun getShortEpg(portal: PortalEntity, channelId: String): List<EpgProgram> =
        withContext(Dispatchers.IO) {
            val token = getOrFetchToken(portal) ?: return@withContext emptyList()
            runCatching {
                val endpoint = resolveEndpoint(portal.url)
                val q = "type=itv&action=get_short_epg&ch_id=$channelId&size=6&JsHttpRequest=1-xml"
                val req = buildRequest(endpoint, q, portal.mac, token)
                HttpClientProvider.client.newCall(req).execute().use { resp ->
                    val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("js") ?: return@withContext emptyList()
                    val list = ArrayList<EpgProgram>(arr.length())
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val name = obj.optString("name")
                        val startTs = obj.optLong("start_timestamp", 0L) * 1000L
                        val stopTs = obj.optLong("stop_timestamp", 0L) * 1000L
                        val descr = obj.optString("descr")
                        if (name.isNotBlank()) {
                            list.add(EpgProgram(name, startTs, stopTs, descr))
                        }
                    }
                    list
                }
            }.getOrDefault(emptyList())
        }
}
