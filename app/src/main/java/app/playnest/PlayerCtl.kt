@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.playnest

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Everything about the currently playing video: stream resolution, quality list, speed,
 * SponsorBlock skipping, resume position, queue/autoplay, related videos and comments.
 */
class PlayerCtl(
    private val app: Application,
    private val store: Store,
    private val scope: CoroutineScope,
    private val onWatched: (VideoItem) -> Unit,
) {
    val player = PlayerHolder.get(app)

    // UI state
    var current by mutableStateOf<VideoItem?>(null); private set
    var info by mutableStateOf<StreamInfo?>(null); private set
    var related by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var expanded by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var inPip by mutableStateOf(false)
    var options by mutableStateOf<List<QOption>>(emptyList()); private set
    var selected by mutableStateOf<QOption?>(null); private set
    var speed by mutableFloatStateOf(1f); private set
    var boosting by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set
    var posMs by mutableLongStateOf(0L); private set
    var durMs by mutableLongStateOf(0L); private set
    var toast by mutableStateOf<String?>(null); private set

    // comments
    var comments by mutableStateOf<List<Comment>>(emptyList()); private set
    var commentsLoading by mutableStateOf(false); private set
    var commentsDisabled by mutableStateOf(false); private set
    var commentsHasMore by mutableStateOf(false); private set
    private var commentsInfo: CommentsInfo? = null
    private var commentsNext: Page? = null

    private var queue: List<VideoItem> = emptyList()
    private var qIdx = -1
    private var segs: List<SponsorBlock.Seg> = emptyList()
    private var job: Job? = null

    private val androidUa by lazy { runCatching { YoutubeParsingHelper.getAndroidUserAgent(null) }.getOrDefault(Net.WEB_UA) }
    private val iosUa by lazy { runCatching { YoutubeParsingHelper.getIosUserAgent(null) }.getOrDefault(Net.WEB_UA) }

    /** YouTube stream URLs are picky about the User-Agent matching the client that produced them. */
    private val dataSourceFactory = ResolvingDataSource.Factory(
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent(Net.WEB_UA),
    ) { spec ->
        val ua = when (spec.uri.getQueryParameter("c")) {
            "ANDROID" -> androidUa
            "IOS" -> iosUa
            else -> Net.WEB_UA
        }
        spec.withAdditionalHeaders(mapOf("User-Agent" to ua))
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_ENDED) onEnded()
        }
        override fun onPlayerError(e: PlaybackException) {
            loading = false
            error = "Playback error (${e.errorCodeName}). Try another quality or retry."
        }
    }

    init {
        player.addListener(listener)
        playing = player.isPlaying
        scope.launch {
            var tick = 0
            while (true) {
                delay(500)
                val cur = current ?: continue
                posMs = player.currentPosition.coerceAtLeast(0)
                durMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                if (player.isPlaying) {
                    if (store.sponsorBlock) {
                        segs.firstOrNull { posMs >= it.start && posMs < it.end - 400 }?.let {
                            player.seekTo(it.end)
                            flash("Skipped ${it.category}")
                        }
                    }
                    tick++
                    if (tick % 10 == 0 && durMs > 0) store.saveResume(cur.url, posMs)
                }
            }
        }
    }

    fun release() { player.removeListener(listener) }

    // ---------- playback ----------

    fun play(v: VideoItem, queue: List<VideoItem> = emptyList(), idx: Int = -1) {
        job?.cancel()
        this.queue = queue
        qIdx = idx
        current = v
        info = null
        related = emptyList()
        options = emptyList()
        selected = null
        segs = emptyList()
        error = null
        loading = true
        expanded = true
        posMs = 0; durMs = 0
        resetComments()
        player.stop()
        player.clearMediaItems()

        job = scope.launch {
            try {
                val si = Repo.stream(v.url)
                info = si
                val full = v.copy(
                    title = si.name ?: v.title,
                    uploader = si.uploaderName ?: v.uploader,
                    uploaderUrl = si.uploaderUrl ?: v.uploaderUrl,
                    thumb = si.thumbnails.pick() ?: v.thumb,
                    duration = si.duration,
                )
                current = full
                related = si.relatedItems.filterIsInstance<StreamInfoItem>().map(Repo::toVideo)
                val opts = buildOptions(si)
                if (opts.isEmpty()) throw IllegalStateException("No playable stream found for this video")
                options = opts
                val sel = pickDefault(opts)
                selected = sel
                val saved = store.resume(v.url)
                val dur = si.duration * 1000
                val start = if (saved > 10_000 && (dur <= 0 || dur - saved > 15_000)) saved else 0L
                prepare(si, sel, start)
                loading = false
                onWatched(full)
                if (store.sponsorBlock && si.streamType == StreamType.VIDEO_STREAM) {
                    launch { segs = SponsorBlock.fetch(si.id) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loading = false
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun retry() { current?.let { play(it, queue, qIdx) } }

    fun stop() {
        job?.cancel()
        player.stop()
        player.clearMediaItems()
        current = null
        info = null
        expanded = false
        fullscreen = false
        loading = false
        error = null
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause()
        else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun seekTo(ms: Long) { player.seekTo(ms.coerceAtLeast(0)); posMs = ms }

    fun seekBy(delta: Long) {
        val d = player.duration
        val t = player.currentPosition + delta
        seekTo(if (d != C.TIME_UNSET) t.coerceIn(0, d) else t)
        flash(if (delta > 0) "+${delta / 1000}s" else "${delta / 1000}s")
    }

    fun setSpeed(s: Float) { speed = s; if (!boosting) player.setPlaybackSpeed(s) }

    /** Hold-to-2x gesture. */
    fun boost(on: Boolean) {
        if (boosting == on) return
        boosting = on
        player.setPlaybackSpeed(if (on) 2f else speed)
    }

    fun setQuality(opt: QOption) {
        val si = info ?: return
        val pos = player.currentPosition
        selected = opt
        loading = true
        prepare(si, opt, pos)
        loading = false
    }

    private fun prepare(si: StreamInfo, opt: QOption, startMs: Long) {
        player.setMediaSource(buildSource(si, opt), startMs)
        player.setPlaybackSpeed(if (boosting) 2f else speed)
        player.prepare()
        player.playWhenReady = true
    }

    private fun onEnded() {
        current?.let { store.saveResume(it.url, 0) }
        if (qIdx >= 0 && qIdx + 1 < queue.size) {
            play(queue[qIdx + 1], queue, qIdx + 1)
        } else if (store.autoplay) {
            related.firstOrNull()?.let { play(it) }
        }
    }

    private fun flash(msg: String) {
        toast = msg
        scope.launch { delay(1200); if (toast == msg) toast = null }
    }

    // ---------- streams ----------

    private fun buildOptions(si: StreamInfo): List<QOption> {
        val live = si.streamType == StreamType.LIVE_STREAM || si.streamType == StreamType.AUDIO_LIVE_STREAM
        if (live && !si.hlsUrl.isNullOrEmpty()) return listOf(QOption("Live", 0, null, si.hlsUrl))

        val prog = DeliveryMethod.PROGRESSIVE_HTTP
        val candidates = (si.videoStreams.filter { it.deliveryMethod == prog } +
            si.videoOnlyStreams.filter { it.deliveryMethod == prog }).filter { it.height > 0 }

        val opts = candidates.groupBy { it.height }.map { (h, list) ->
            val best = list.sortedWith(
                compareByDescending<VideoStream> { !it.isVideoOnly }
                    .thenByDescending { it.format == MediaFormat.MPEG_4 }
                    .thenByDescending { it.bitrate },
            ).first()
            QOption("${h}p", h, best, null)
        }.sortedByDescending { it.height }

        if (opts.isNotEmpty()) return opts
        return if (!si.hlsUrl.isNullOrEmpty()) listOf(QOption("Auto", 0, null, si.hlsUrl)) else emptyList()
    }

    private fun pickDefault(opts: List<QOption>): QOption {
        val max = store.maxQuality
        return opts.filter { it.height <= max }.maxByOrNull { it.height } ?: opts.minBy { it.height }
    }

    private fun bestAudio(si: StreamInfo): AudioStream? {
        val all = si.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val original = all.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
        val pool = original.ifEmpty { all }
        return pool.sortedWith(
            compareByDescending<AudioStream> { it.format == MediaFormat.M4A }.thenByDescending { it.averageBitrate },
        ).firstOrNull()
    }

    private fun buildSource(si: StreamInfo, opt: QOption): MediaSource {
        val meta = MediaMetadata.Builder()
            .setTitle(si.name)
            .setArtist(si.uploaderName)
            .setArtworkUri(si.thumbnails.pick()?.let { Uri.parse(it) })
            .build()

        opt.hls?.let { hls ->
            return HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.Builder().setUri(hls).setMediaMetadata(meta).build())
        }
        val v = opt.video!!
        val progressive = ProgressiveMediaSource.Factory(dataSourceFactory)
        val videoSrc = progressive.createMediaSource(
            MediaItem.Builder().setUri(v.content).setMediaMetadata(meta).build(),
        )
        if (!v.isVideoOnly) return videoSrc
        val audio = bestAudio(si) ?: return videoSrc
        val audioSrc = progressive.createMediaSource(MediaItem.fromUri(audio.content))
        return MergingMediaSource(videoSrc, audioSrc)
    }

    // ---------- comments ----------

    private fun resetComments() {
        comments = emptyList(); commentsInfo = null; commentsNext = null
        commentsDisabled = false; commentsHasMore = false; commentsLoading = false
    }

    fun loadComments() {
        val url = current?.url ?: return
        if (commentsLoading) return
        commentsLoading = true
        scope.launch {
            try {
                val info = commentsInfo
                val next = commentsNext
                val page = if (info == null || next == null) Repo.comments(url) else Repo.commentsMore(info, next)
                if (current?.url != url) return@launch
                commentsInfo = page.info
                commentsNext = page.next
                commentsHasMore = page.next != null
                commentsDisabled = page.disabled
                comments = comments + page.items
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                commentsDisabled = comments.isEmpty()
            } finally {
                commentsLoading = false
            }
        }
    }
}
