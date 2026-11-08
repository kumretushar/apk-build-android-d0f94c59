@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.playnest.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.playnest.AppVm
import app.playnest.ChannelItem
import app.playnest.PlayerCtl
import app.playnest.Screen
import app.playnest.fmtCount
import app.playnest.fmtDur
import app.playnest.meta
import app.playnest.pick
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private fun speedLabel(s: Float) = if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"

@Composable
private fun MenuButton(label: String, items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(label, color = Color.White, fontWeight = FontWeight.Bold) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { action(); open = false })
            }
        }
    }
}

/** The video surface with gesture handling and a custom control overlay. */
@Composable
fun VideoBox(ctl: PlayerCtl, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    var controls by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(controls, ctl.playing, dragging) {
        if (controls && ctl.playing && !dragging) { delay(3000); controls = false }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = ctl.player
                }
            },
            update = { it.player = ctl.player },
            modifier = Modifier.fillMaxSize(),
        )

        // gestures: tap = toggle controls, double tap = seek 10s, hold = 2x
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls },
                    onDoubleTap = { o -> if (o.x < size.width / 2) ctl.seekBy(-10_000) else ctl.seekBy(10_000) },
                    onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); ctl.boost(true) },
                    onPress = { tryAwaitRelease(); ctl.boost(false) },
                )
            },
        )

        if (ctl.loading || ctl.buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = Color.White)
        }

        AnimatedVisibility(visible = controls && ctl.error == null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (ctl.fullscreen) ctl.fullscreen = false else ctl.expanded = false }) {
                        Icon(Icons.Default.KeyboardArrowDown, "Minimize", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    MenuButton(speedLabel(ctl.speed), listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { s ->
                        speedLabel(s) to { ctl.setSpeed(s) }
                    })
                    if (ctl.options.size > 1) {
                        MenuButton(ctl.selected?.label ?: "Quality", ctl.options.map { o -> o.label to { ctl.setQuality(o) } })
                    }
                }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconButton(onClick = { ctl.seekBy(-10_000) }) { Icon(Icons.Default.Replay10, "Back 10s", tint = Color.White, modifier = Modifier.size(32.dp)) }
                    IconButton(onClick = { ctl.togglePlay() }, modifier = Modifier.size(64.dp)) {
                        Icon(if (ctl.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                    IconButton(onClick = { ctl.seekBy(10_000) }) { Icon(Icons.Default.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp)) }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = if (dragging) dragPos.toLong() else ctl.posMs
                        Text("${fmtDur(shown / 1000)} / ${fmtDur(ctl.durMs / 1000)}", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { ctl.fullscreen = !ctl.fullscreen }) {
                            Icon(if (ctl.fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Fullscreen", tint = Color.White)
                        }
                    }
                    if (ctl.durMs > 0) {
                        Slider(
                            value = if (dragging) dragPos else ctl.posMs.toFloat(),
                            onValueChange = { dragging = true; dragPos = it },
                            onValueChangeFinished = { ctl.seekTo(dragPos.toLong()); dragging = false },
                            valueRange = 0f..ctl.durMs.toFloat(),
                            modifier = Modifier.height(28.dp),
                        )
                    }
                }
            }
        }

        ctl.error?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { ctl.retry() }) { Text("Retry") }
            }
        }
        ctl.toast?.let {
            Text(it, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
        }
        if (ctl.boosting) {
            Text("2x ▶▶", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun PipVideo(ctl: PlayerCtl) {
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = ctl.player
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
}

@Composable
fun MiniPlayer(ctl: PlayerCtl) {
    val cur = ctl.current ?: return
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable { ctl.expanded = true }) {
        Column {
            if (ctl.durMs > 0) {
                LinearProgressIndicator(progress = { (ctl.posMs.toFloat() / ctl.durMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp))
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(cur.thumb, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(88.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(6.dp)).background(Color.DarkGray))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(cur.title.ifBlank { "Loading…" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(cur.uploader, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { ctl.togglePlay() }) {
                    Icon(if (ctl.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
                }
                IconButton(onClick = { ctl.stop() }) { Icon(Icons.Default.Close, "Close") }
            }
        }
    }
}

@Composable
fun PlayerScreen(vm: AppVm) {
    val ctl = vm.ctl
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (ctl.fullscreen) {
            VideoBox(ctl, Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize()) {
                VideoBox(ctl, Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                Details(vm, ctl)
            }
        }
    }
}

@Composable
private fun Details(vm: AppVm, ctl: PlayerCtl) {
    val cur = ctl.current ?: return
    val si = ctl.info
    val ctx = LocalContext.current
    var showDesc by remember(cur.url) { mutableStateOf(false) }
    var showComments by remember(cur.url) { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(12.dp)) {
                Text(si?.name ?: cur.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val views = si?.viewCount?.takeIf { it >= 0 }?.let { "${fmtCount(it)} views" }
                val likes = si?.likeCount?.takeIf { it >= 0 }?.let { "${fmtCount(it)} likes" }
                Text(listOfNotNull(views, si?.textualUploadDate, likes).joinToString(" • "), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.toggleLike(cur) }) {
                    Icon(if (vm.isLiked(cur.url)) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt, null, Modifier.size(18.dp))
                    Text(if (vm.isLiked(cur.url)) " Liked" else " Like")
                }
                FilledTonalButton(onClick = { vm.toggleLater(cur) }) {
                    Icon(Icons.Default.WatchLater, null, Modifier.size(18.dp))
                    Text(if (vm.isLater(cur.url)) " Saved" else " Save")
                }
                FilledTonalButton(onClick = { share(ctx, cur.url) }) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Text(" Share")
                }
            }
        }
        item {
            val chUrl = si?.uploaderUrl ?: cur.uploaderUrl
            val avatar = si?.uploaderAvatars.pick()
            val name = si?.uploaderName ?: cur.uploader
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(enabled = chUrl.isNotBlank()) {
                    ctl.expanded = false
                    vm.push(Screen.Channel(chUrl))
                }, verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(avatar, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val subs = si?.uploaderSubscriberCount ?: -1
                        if (subs >= 0) Text("${fmtCount(subs)} subscribers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (chUrl.isNotBlank()) {
                    Button(onClick = { vm.toggleSub(ChannelItem(chUrl, name, avatar, si?.uploaderSubscriberCount ?: -1)) }) {
                        Text(if (vm.isSub(chUrl)) "Subscribed" else "Subscribe")
                    }
                }
            }
        }
        item {
            val desc = si?.description?.content.orEmpty()
                .replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]*>"), "")
            if (desc.isNotBlank()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).clickable { showDesc = !showDesc }.padding(12.dp),
                ) {
                    Text(desc, maxLines = if (showDesc) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    Text(if (showDesc) "Show less" else "Show more", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item {
            TextButton(onClick = {
                showComments = !showComments
                if (showComments && ctl.comments.isEmpty()) ctl.loadComments()
            }, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(if (showComments) "Hide comments" else "Show comments", fontWeight = FontWeight.Bold)
            }
        }
        if (showComments) {
            if (ctl.commentsDisabled) item { Text("Comments are turned off or unavailable.", Modifier.padding(12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(ctl.comments) { c ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    AsyncImage(c.avatar, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(listOfNotNull(if (c.pinned) "📌" else null, c.author, c.date.ifBlank { null }).joinToString(" • "),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c.text, fontSize = 14.sp)
                        if (c.likes.isNotBlank()) Text("👍 ${c.likes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    when {
                        ctl.commentsLoading -> CircularProgressIndicator(Modifier.size(24.dp))
                        ctl.commentsHasMore -> TextButton(onClick = { ctl.loadComments() }) { Text("Load more comments") }
                    }
                }
            }
        }
        item { Text("Up next", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
        items(ctl.related) { v -> VideoCard(v, vm) }
    }
}
