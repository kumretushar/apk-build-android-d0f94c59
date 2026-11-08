package app.playnest

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

sealed interface Entry

data class VideoItem(
    val url: String,
    val title: String = "",
    val uploader: String = "",
    val uploaderUrl: String = "",
    val thumb: String? = null,
    val duration: Long = 0,
    val views: Long = -1,
    val uploaded: String? = null,
    val uploadedMs: Long = -1,
) : Entry

data class ChannelItem(
    val url: String,
    val name: String,
    val avatar: String? = null,
    val subs: Long = -1,
) : Entry

data class PlaylistItem(
    val url: String,
    val name: String,
    val thumb: String?,
    val count: Long,
    val uploader: String,
) : Entry

data class Comment(
    val author: String,
    val text: String,
    val likes: String,
    val date: String,
    val avatar: String?,
    val pinned: Boolean,
)

data class Chunk<out T>(val items: List<T>, val next: Page?)

data class QOption(val label: String, val height: Int, val video: VideoStream?, val hls: String?)

sealed class Screen {
    data object Home : Screen()
    data object Search : Screen()
    data object Subs : Screen()
    data object Library : Screen()
    data object Settings : Screen()
    data class Channel(val url: String) : Screen()
    data class Playlist(val url: String) : Screen()
}

/** Pick the thumbnail closest to 360px tall (or the first one if sizes are unknown). */
fun List<Image>?.pick(): String? {
    if (this.isNullOrEmpty()) return null
    val sized = filter { it.height > 0 }
    return if (sized.isEmpty()) first().url else sized.minBy { abs(it.height - 360) }.url
}

fun fmtDur(sec: Long): String {
    if (sec <= 0) return "0:00"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun fmtCount(n: Long): String = when {
    n < 0 -> ""
    n < 1_000 -> n.toString()
    n < 1_000_000 -> "%.1fK".format(n / 1_000.0).replace(".0K", "K")
    n < 1_000_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
    else -> "%.1fB".format(n / 1_000_000_000.0).replace(".0B", "B")
}

fun VideoItem.meta(): String = listOfNotNull(
    uploader.ifBlank { null },
    fmtCount(views).takeIf { it.isNotEmpty() }?.let { "$it views" },
    uploaded,
).joinToString(" • ")
