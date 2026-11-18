package com.kadri.stalkerplayerpro.data.network

import android.util.JsonReader
import android.util.JsonToken
import com.kadri.stalkerplayerpro.data.model.CategoryItem
import com.kadri.stalkerplayerpro.data.model.ChannelItem
import com.kadri.stalkerplayerpro.data.model.VodItem
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * قارئ التدفق المباشر (android.util.JsonReader) لقراءة قوائم Xtream الضخمة
 * عنصراً تلو الآخر مباشرة من الشبكة دون تحميل ملف JSON الكامل في ذاكرة RAM
 */
object StreamingJsonParser {

    private fun JsonReader.nextSafeString(): String {
        return when (peek()) {
            JsonToken.NULL -> {
                nextNull()
                ""
            }
            JsonToken.STRING -> nextString()
            JsonToken.NUMBER -> nextString()
            JsonToken.BOOLEAN -> nextBoolean().toString()
            else -> {
                skipValue()
                ""
            }
        }
    }

    private fun JsonReader.nextSafeInt(): Int {
        return when (peek()) {
            JsonToken.NULL -> {
                nextNull()
                0
            }
            JsonToken.NUMBER -> runCatching { nextInt() }.getOrElse { nextString().toIntOrNull() ?: 0 }
            JsonToken.STRING -> nextString().trim().toIntOrNull() ?: 0
            else -> {
                skipValue()
                0
            }
        }
    }

    fun parseXtreamCategories(inputStream: InputStream): List<CategoryItem> {
        val list = ArrayList<CategoryItem>(64)
        JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.isLenient = true
            if (reader.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
            reader.beginArray()
            while (reader.hasNext()) {
                var id = ""
                var name = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "category_id" -> id = reader.nextSafeString()
                        "category_name" -> name = reader.nextSafeString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (id.isNotEmpty() && name.isNotEmpty()) {
                    list.add(CategoryItem(id = id, name = name))
                }
            }
            reader.endArray()
        }
        return list
    }

    fun parseXtreamLiveStreams(
        inputStream: InputStream,
        portalId: Long,
        baseUrl: String,
        username: String,
        password: String
    ): List<ChannelItem> {
        val channels = ArrayList<ChannelItem>(512)
        val cleanHost = baseUrl.trimEnd('/')
        var index = 0

        JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.isLenient = true
            if (reader.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
            reader.beginArray()
            while (reader.hasNext()) {
                var num = 0
                var name = ""
                var streamId = ""
                var icon = ""
                var epgId = ""
                var catId = ""

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "num" -> num = reader.nextSafeInt()
                        "name" -> name = reader.nextSafeString()
                        "stream_id" -> streamId = reader.nextSafeString()
                        "stream_icon" -> icon = reader.nextSafeString()
                        "epg_channel_id" -> epgId = reader.nextSafeString()
                        "category_id" -> catId = reader.nextSafeString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                if (streamId.isNotEmpty() && name.isNotEmpty()) {
                    index++
                    val channelNum = if (num > 0) num else index
                    val url = "$cleanHost/live/$username/$password/$streamId.ts"
                    channels.add(
                        ChannelItem(
                            uniqueKey = "${portalId}_xt_live_${streamId}_$index",
                            id = streamId,
                            number = channelNum,
                            name = name,
                            logoUrl = icon,
                            categoryId = catId,
                            streamCmdOrUrl = url,
                            epgChannelId = epgId
                        )
                    )
                }
            }
            reader.endArray()
        }
        return channels
    }

    fun parseXtreamVodStreams(
        inputStream: InputStream,
        portalId: Long,
        baseUrl: String,
        username: String,
        password: String
    ): List<VodItem> {
        val movies = ArrayList<VodItem>(512)
        val cleanHost = baseUrl.trimEnd('/')
        var index = 0

        JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.isLenient = true
            if (reader.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
            reader.beginArray()
            while (reader.hasNext()) {
                var name = ""
                var streamId = ""
                var icon = ""
                var rating = ""
                var catId = ""
                var ext = "mp4"

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> name = reader.nextSafeString()
                        "stream_id" -> streamId = reader.nextSafeString()
                        "stream_icon" -> icon = reader.nextSafeString()
                        "rating" -> rating = reader.nextSafeString()
                        "category_id" -> catId = reader.nextSafeString()
                        "container_extension" -> {
                            val parsedExt = reader.nextSafeString()
                            if (parsedExt.isNotBlank()) ext = parsedExt
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                if (streamId.isNotEmpty() && name.isNotEmpty()) {
                    index++
                    val url = "$cleanHost/movie/$username/$password/$streamId.$ext"
                    movies.add(
                        VodItem(
                            uniqueKey = "${portalId}_xt_vod_${streamId}_$index",
                            id = streamId,
                            name = name,
                            posterUrl = icon,
                            categoryId = catId,
                            rating = rating,
                            containerExtension = ext,
                            streamCmdOrUrl = url,
                            isSeries = false
                        )
                    )
                }
            }
            reader.endArray()
        }
        return movies
    }

    fun parseXtreamSeriesList(inputStream: InputStream, portalId: Long): List<VodItem> {
        val seriesList = ArrayList<VodItem>(256)
        var index = 0

        JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            reader.isLenient = true
            if (reader.peek() != JsonToken.BEGIN_ARRAY) return emptyList()
            reader.beginArray()
            while (reader.hasNext()) {
                var name = ""
                var seriesId = ""
                var cover = ""
                var rating = ""
                var catId = ""
                var releaseDate = ""

                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> name = reader.nextSafeString()
                        "series_id" -> seriesId = reader.nextSafeString()
                        "cover" -> cover = reader.nextSafeString()
                        "rating" -> rating = reader.nextSafeString()
                        "category_id" -> catId = reader.nextSafeString()
                        "releaseDate" -> releaseDate = reader.nextSafeString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                if (seriesId.isNotEmpty() && name.isNotEmpty()) {
                    index++
                    seriesList.add(
                        VodItem(
                            uniqueKey = "${portalId}_xt_ser_${seriesId}_$index",
                            id = seriesId,
                            name = name,
                            posterUrl = cover,
                            categoryId = catId,
                            year = releaseDate.take(4),
                            rating = rating,
                            isSeries = true
                        )
                    )
                }
            }
            reader.endArray()
        }
        return seriesList
    }
}
