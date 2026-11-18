package com.kadri.stalkerplayerpro.data.local

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import com.kadri.stalkerplayerpro.data.model.CategoryItem
import com.kadri.stalkerplayerpro.data.model.ChannelItem
import com.kadri.stalkerplayerpro.data.model.VodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * مخزن محلي دائم وسريع (Streaming JsonReader / JsonWriter)
 * يحتفظ بالقنوات والأفلام والمسلسلات المحملة لكل بوابة لمنع إعادة السحب من السيرفر في كل مرة
 */
object PortalCacheStore {

    private fun cacheDir(context: Context): File {
        val dir = File(context.filesDir, "portal_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun liveFile(context: Context, portalId: Long): File =
        File(cacheDir(context), "live_$portalId.json")

    private fun vodFile(context: Context, portalId: Long, isSeries: Boolean, categoryId: String): File {
        val safeCat = categoryId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val prefix = if (isSeries) "series" else "vod"
        return File(cacheDir(context), "${prefix}_${portalId}_$safeCat.json")
    }

    suspend fun clearPortalCache(context: Context, portalId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            cacheDir(context).listFiles()?.forEach { file ->
                if (file.name.contains("_$portalId.") || file.name.contains("_${portalId}_")) {
                    file.delete()
                }
            }
        }
    }

    suspend fun saveLiveCache(
        context: Context,
        portalId: Long,
        categories: List<CategoryItem>,
        channels: List<ChannelItem>
    ) = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext
        runCatching {
            val file = liveFile(context, portalId)
            JsonWriter(OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8)).use { writer ->
                writer.beginObject()
                writer.name("categories").beginArray()
                for (c in categories) {
                    writer.beginObject()
                    writer.name("id").value(c.id)
                    writer.name("name").value(c.name)
                    writer.endObject()
                }
                writer.endArray()

                writer.name("channels").beginArray()
                for (ch in channels) {
                    writer.beginObject()
                    writer.name("uk").value(ch.uniqueKey)
                    writer.name("id").value(ch.id)
                    writer.name("num").value(ch.number)
                    writer.name("name").value(ch.name)
                    writer.name("logo").value(ch.logoUrl)
                    writer.name("catId").value(ch.categoryId)
                    writer.name("cmd").value(ch.streamCmdOrUrl)
                    writer.name("epg").value(ch.epgNowTitle)
                    writer.endObject()
                }
                writer.endArray()
                writer.endObject()
            }
        }
    }

    suspend fun loadLiveCache(
        context: Context,
        portalId: Long
    ): Pair<List<CategoryItem>, List<ChannelItem>>? = withContext(Dispatchers.IO) {
        val file = liveFile(context, portalId)
        if (!file.exists() || file.length() == 0L) return@withContext null
        runCatching {
            val cats = ArrayList<CategoryItem>()
            val chs = ArrayList<ChannelItem>()
            JsonReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "categories" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                var id = ""
                                var name = ""
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> id = reader.nextString()
                                        "name" -> name = reader.nextString()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (id.isNotEmpty()) cats.add(CategoryItem(id, name))
                            }
                            reader.endArray()
                        }
                        "channels" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                var uk = ""
                                var id = ""
                                var num = 0
                                var name = ""
                                var logo = ""
                                var catId = ""
                                var cmd = ""
                                var epg = ""
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "uk" -> uk = reader.nextString()
                                        "id" -> id = reader.nextString()
                                        "num" -> num = reader.nextInt()
                                        "name" -> name = reader.nextString()
                                        "logo" -> logo = reader.nextString()
                                        "catId" -> catId = reader.nextString()
                                        "cmd" -> cmd = reader.nextString()
                                        "epg" -> epg = reader.nextString()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (id.isNotEmpty()) {
                                    chs.add(
                                        ChannelItem(
                                            uniqueKey = uk.ifBlank { "${portalId}_cached_$id" },
                                            id = id,
                                            number = num,
                                            name = name,
                                            logoUrl = logo,
                                            categoryId = catId,
                                            streamCmdOrUrl = cmd,
                                            epgNowTitle = epg
                                        )
                                    )
                                }
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            if (chs.isEmpty()) null else (cats to chs)
        }.getOrNull()
    }

    suspend fun saveVodCache(
        context: Context,
        portalId: Long,
        isSeries: Boolean,
        categoryId: String,
        categories: List<CategoryItem>,
        items: List<VodItem>
    ) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        runCatching {
            val file = vodFile(context, portalId, isSeries, categoryId)
            JsonWriter(OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8)).use { writer ->
                writer.beginObject()
                writer.name("categories").beginArray()
                for (c in categories) {
                    writer.beginObject()
                    writer.name("id").value(c.id)
                    writer.name("name").value(c.name)
                    writer.endObject()
                }
                writer.endArray()

                writer.name("items").beginArray()
                for (it in items) {
                    writer.beginObject()
                    writer.name("uk").value(it.uniqueKey)
                    writer.name("id").value(it.id)
                    writer.name("name").value(it.name)
                    writer.name("poster").value(it.posterUrl)
                    writer.name("catId").value(it.categoryId)
                    writer.name("year").value(it.year)
                    writer.name("rating").value(it.rating)
                    writer.name("ext").value(it.containerExtension)
                    writer.name("cmd").value(it.streamCmdOrUrl)
                    writer.name("ser").value(it.isSeries)
                    writer.endObject()
                }
                writer.endArray()
                writer.endObject()
            }
        }
    }

    suspend fun loadVodCache(
        context: Context,
        portalId: Long,
        isSeries: Boolean,
        categoryId: String
    ): Pair<List<CategoryItem>, List<VodItem>>? = withContext(Dispatchers.IO) {
        val file = vodFile(context, portalId, isSeries, categoryId)
        if (!file.exists() || file.length() == 0L) return@withContext null
        runCatching {
            val cats = ArrayList<CategoryItem>()
            val items = ArrayList<VodItem>()
            JsonReader(InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "categories" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                var id = ""
                                var name = ""
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> id = reader.nextString()
                                        "name" -> name = reader.nextString()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (id.isNotEmpty()) cats.add(CategoryItem(id, name))
                            }
                            reader.endArray()
                        }
                        "items" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                var uk = ""
                                var id = ""
                                var name = ""
                                var poster = ""
                                var catId = ""
                                var year = ""
                                var rating = ""
                                var ext = "mp4"
                                var cmd = ""
                                var ser = isSeries
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "uk" -> uk = reader.nextString()
                                        "id" -> id = reader.nextString()
                                        "name" -> name = reader.nextString()
                                        "poster" -> poster = reader.nextString()
                                        "catId" -> catId = reader.nextString()
                                        "year" -> year = reader.nextString()
                                        "rating" -> rating = reader.nextString()
                                        "ext" -> ext = reader.nextString()
                                        "cmd" -> cmd = reader.nextString()
                                        "ser" -> ser = reader.nextBoolean()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (id.isNotEmpty()) {
                                    items.add(
                                        VodItem(
                                            uniqueKey = uk.ifBlank { "${portalId}_vod_$id" },
                                            id = id,
                                            name = name,
                                            posterUrl = poster,
                                            categoryId = catId,
                                            year = year,
                                            rating = rating,
                                            containerExtension = ext,
                                            streamCmdOrUrl = cmd,
                                            isSeries = ser
                                        )
                                    )
                                }
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            if (items.isEmpty()) null else (cats to items)
        }.getOrNull()
    }
}
