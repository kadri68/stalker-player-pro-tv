package com.kadri.stalkerplayerpro.player

import android.content.Context
import com.kadri.stalkerplayerpro.data.model.SubtitleCue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class SavedMovieSubtitle(
    val cues: List<SubtitleCue>,
    val offsetMs: Long = 0L
)

object SubtitleCacheStore {

    private fun cacheFile(context: Context, key: String): File {
        val dir = File(context.filesDir, "approved_subtitles").apply { if (!exists()) mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return File(dir, "$hex.json")
    }

    fun buildKey(portalId: Long, contentType: String, itemId: String, title: String): String {
        val idPart = itemId.trim().ifBlank { title.trim().lowercase() }
        return "${portalId}_${contentType}_${idPart}"
    }

    fun saveSubtitle(context: Context, key: String, cues: List<SubtitleCue>, offsetMs: Long = 0L) {
        if (key.isBlank() || cues.isEmpty()) return
        runCatching {
            val root = JSONObject()
            root.put("offsetMs", offsetMs)
            val arr = JSONArray()
            for (c in cues) {
                val obj = JSONObject()
                obj.put("s", c.startMs)
                obj.put("e", c.endMs)
                obj.put("t", c.text)
                arr.put(obj)
            }
            root.put("cues", arr)
            cacheFile(context, key).writeText(root.toString(), Charsets.UTF_8)
        }
    }

    fun loadSubtitle(context: Context, key: String): SavedMovieSubtitle? {
        if (key.isBlank()) return null
        return runCatching {
            val file = cacheFile(context, key)
            if (!file.exists()) return null
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val offsetMs = root.optLong("offsetMs", 0L)
            val arr = root.optJSONArray("cues") ?: return null
            val list = ArrayList<SubtitleCue>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SubtitleCue(
                        startMs = obj.getLong("s"),
                        endMs = obj.getLong("e"),
                        text = obj.getString("t")
                    )
                )
            }
            if (list.isEmpty()) null else SavedMovieSubtitle(cues = list, offsetMs = offsetMs)
        }.getOrNull()
    }
}
