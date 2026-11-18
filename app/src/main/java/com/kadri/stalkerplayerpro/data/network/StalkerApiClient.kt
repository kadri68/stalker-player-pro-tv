package com.kadri.stalkerplayerpro.data.network

import com.kadri.stalkerplayerpro.data.local.PortalEntity
import com.kadri.stalkerplayerpro.data.model.CategoryItem
import com.kadri.stalkerplayerpro.data.model.ChannelItem
import com.kadri.stalkerplayerpro.data.model.EpgProgram
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
                                uniqueKey = "${portal.id}_stk_live_${id}_$i",
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
                                isSeries = (type == "series")
                            )
                        )
                    }
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    suspend fun createLink(portal: PortalEntity, cmd: String, type: String = "itv"): String =
        withContext(Dispatchers.IO) {
            val cleanCmd = cmd.removePrefix("ffmpeg ").trim()
            if (cleanCmd.startsWith("http://") || cleanCmd.startsWith("https://")) {
                if (!cleanCmd.contains("localhost") && !cleanCmd.contains("/ch/")) {
                    return@withContext cleanCmd
                }
            }
            val token = getOrFetchToken(portal) ?: return@withContext cleanCmd
            runCatching {
                val endpoint = resolveEndpoint(portal.url)
                val encodedCmd = URLEncoder.encode(cmd, "UTF-8")
                val q = "type=$type&action=create_link&cmd=$encodedCmd&JsHttpRequest=1-xml"
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
