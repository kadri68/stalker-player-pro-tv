package com.kadri.stalkerplayerpro.data.model

import com.kadri.stalkerplayerpro.data.local.PortalEntity

data class PlaybackRequest(
    val portal: PortalEntity,
    val contentType: String, // "LIVE", "MOVIE", "EPISODE"
    val itemId: String,
    val seriesId: String = "",
    val title: String,
    val subtitleQueryHint: String = "",
    val posterOrLogo: String = "",
    val streamCmdOrUrl: String,
    val containerExtension: String = "mp4",
    val resumePositionMs: Long = 0L,
    val channelList: List<ChannelItem> = emptyList(),
    val currentChannelIndex: Int = -1
)
