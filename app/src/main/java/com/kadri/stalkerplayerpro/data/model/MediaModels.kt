package com.kadri.stalkerplayerpro.data.model

enum class ProtocolType(val code: String, val label: String) {
    STALKER("STALKER", "Stalker Portal"),
    XTREAM("XTREAM", "Xtream Codes"),
    M3U("M3U", "M3U / M3U8");

    companion object {
        fun fromCode(code: String): ProtocolType =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: STALKER
    }
}

data class CategoryItem(
    val id: String,
    val name: String,
    val isLocked: Boolean = false
)

data class ChannelItem(
    val uniqueKey: String,
    val id: String,
    val number: Int,
    val name: String,
    val logoUrl: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val streamCmdOrUrl: String = "",
    val epgChannelId: String = "",
    val epgNowTitle: String = "",
    val epgNextTitle: String = "",
    val isHidden: Boolean = false
)

data class VodItem(
    val uniqueKey: String,
    val id: String,
    val name: String,
    val posterUrl: String = "",
    val categoryId: String = "",
    val year: String = "",
    val rating: String = "",
    val containerExtension: String = "mp4",
    val streamCmdOrUrl: String = "",
    val isSeries: Boolean = false
)

data class EpisodeItem(
    val uniqueKey: String,
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String = "mp4",
    val streamCmdOrUrl: String = ""
)

data class EpgProgram(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val description: String = ""
)

data class SubtitleSearchResult(
    val id: String,
    val fileId: String,
    val downloadUrl: String,
    val fileName: String,
    val language: String,
    val source: String // "OpenSubtitles" or "Subdl"
)

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
