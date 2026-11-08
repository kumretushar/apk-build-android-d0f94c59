package app.playnest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class ChannelData(
    val url: String, val name: String, val avatar: String?, val banner: String?,
    val subs: Long, val desc: String, val tab: ListLinkHandler?, val first: Chunk<Entry>,
)

class PlaylistData(
    val name: String, val uploader: String, val thumb: String?, val count: Long, val first: Chunk<Entry>,
)

class CommentPage(val items: List<Comment>, val next: Page?, val disabled: Boolean, val info: CommentsInfo?)

object Repo {
    private val yt get() = ServiceList.YouTube
    private const val FALLBACK_QUERY = "trending videos"

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    fun toVideo(i: StreamInfoItem) = VideoItem(
        url = i.url,
        title = i.name ?: "",
        uploader = i.uploaderName ?: "",
        uploaderUrl = i.uploaderUrl ?: "",
        thumb = i.thumbnails.pick(),
        duration = i.duration,
        views = i.viewCount,
        uploaded = i.textualUploadDate,
        uploadedMs = runCatching { i.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() }.getOrNull() ?: -1,
    )

    fun toEntry(i: InfoItem): Entry? = when (i) {
        is StreamInfoItem -> toVideo(i)
        is ChannelInfoItem -> ChannelItem(i.url, i.name ?: "", i.thumbnails.pick(), i.subscriberCount)
        is PlaylistInfoItem -> PlaylistItem(i.url, i.name ?: "", i.thumbnails.pick(), i.streamCount, i.uploaderName ?: "")
        else -> null
    }

    private fun toComment(c: CommentsInfoItem) = Comment(
        author = c.uploaderName ?: "",
        text = c.commentText?.content ?: "",
        likes = c.textualLikeCount ?: "",
        date = c.textualUploadDate ?: "",
        avatar = c.uploaderAvatars.pick(),
        pinned = c.isPinned,
    )

    // ---------- feeds ----------

    private suspend fun kiosk(): Chunk<Entry>? = io {
        runCatching {
            val ex = yt.kioskList.defaultKioskExtractor
            ex.fetchPage()
            val vids = ex.initialPage.items.filterIsInstance<StreamInfoItem>().map(::toVideo)
            if (vids.isEmpty()) null else Chunk<Entry>(vids, null)
        }.getOrNull()
    }

    /** Home feed: YouTube trending kiosk, falling back to a search when trending is unavailable. */
    suspend fun home(page: Page?): Chunk<Entry> {
        if (page == null) kiosk()?.let { return it }
        return search(FALLBACK_QUERY, page)
    }

    suspend fun search(query: String, page: Page?): Chunk<Entry> = io {
        val handler = yt.searchQHFactory.fromQuery(query)
        if (page == null) {
            val i = SearchInfo.getInfo(yt, handler)
            Chunk(i.relatedItems.mapNotNull(::toEntry), i.nextPage)
        } else {
            val p = SearchInfo.getMoreItems(yt, handler, page)
            Chunk(p.items.mapNotNull(::toEntry), p.nextPage)
        }
    }

    suspend fun suggestions(q: String): List<String> =
        if (q.isBlank()) emptyList() else io { yt.suggestionExtractor.suggestionList(q) }

    suspend fun stream(url: String): StreamInfo = io { StreamInfo.getInfo(url) }

    // ---------- channel / playlist ----------

    suspend fun channel(url: String): ChannelData = io {
        val info = ChannelInfo.getInfo(url)
        val tab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val first = if (tab != null) {
            val t = ChannelTabInfo.getInfo(yt, tab)
            Chunk(t.relatedItems.mapNotNull(::toEntry), t.nextPage)
        } else Chunk(emptyList<Entry>(), null)
        ChannelData(
            url = url, name = info.name ?: "", avatar = info.avatars.pick(), banner = info.banners.pick(),
            subs = info.subscriberCount, desc = info.description ?: "", tab = tab, first = first,
        )
    }

    suspend fun channelMore(tab: ListLinkHandler, page: Page): Chunk<Entry> = io {
        val p = ChannelTabInfo.getMoreItems(yt, tab, page)
        Chunk(p.items.mapNotNull(::toEntry), p.nextPage)
    }

    suspend fun playlist(url: String): PlaylistData = io {
        val i = PlaylistInfo.getInfo(url)
        PlaylistData(
            name = i.name ?: "", uploader = i.uploaderName ?: "", thumb = i.thumbnails.pick(), count = i.streamCount,
            first = Chunk<Entry>(i.relatedItems.map(::toVideo), i.nextPage),
        )
    }

    suspend fun playlistMore(url: String, page: Page): Chunk<Entry> = io {
        val p = PlaylistInfo.getMoreItems(yt, url, page)
        Chunk<Entry>(p.items.map(::toVideo), p.nextPage)
    }

    /** Subscriptions feed: latest uploads of every locally-subscribed channel, newest first. */
    suspend fun subsFeed(subs: List<ChannelItem>): List<VideoItem> = coroutineScope {
        val sem = Semaphore(5)
        subs.take(30).map { c ->
            async {
                sem.withPermit {
                    runCatching { channel(c.url).first.items.filterIsInstance<VideoItem>().take(8) }
                        .getOrDefault(emptyList())
                }
            }
        }.awaitAll().flatten().sortedByDescending { it.uploadedMs }.take(80)
    }

    // ---------- comments ----------

    suspend fun comments(url: String): CommentPage = io {
        val ci = CommentsInfo.getInfo(url)
        CommentPage(ci.relatedItems.map(::toComment), ci.nextPage, ci.isCommentsDisabled, ci)
    }

    suspend fun commentsMore(info: CommentsInfo, page: Page): CommentPage = io {
        val p = CommentsInfo.getMoreItems(yt, info, page)
        CommentPage(p.items.map(::toComment), p.nextPage, false, info)
    }
}
