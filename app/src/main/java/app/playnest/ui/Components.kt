package app.playnest.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playnest.AppVm
import app.playnest.ChannelItem
import app.playnest.Entry
import app.playnest.Paged
import app.playnest.PlaylistItem
import app.playnest.Screen
import app.playnest.VideoItem
import app.playnest.fmtCount
import app.playnest.fmtDur
import app.playnest.meta
import coil.compose.AsyncImage

fun share(ctx: Context, url: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }
    ctx.startActivity(Intent.createChooser(i, null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun VideoCard(
    v: VideoItem,
    vm: AppVm,
    extra: List<Pair<String, () -> Unit>> = emptyList(),
    onClick: () -> Unit = { vm.ctl.play(v) },
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 14.dp)) {
        Box {
            AsyncImage(
                model = v.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (v.duration > 0) {
                Text(
                    fmtDur(v.duration), color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Row(Modifier.padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(v.meta(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (vm.isLater(v.url)) "Remove from Watch later" else "Save to Watch later") },
                        onClick = { vm.toggleLater(v); menu = false },
                    )
                    DropdownMenuItem(text = { Text("Share") }, onClick = { share(ctx, v.url); menu = false })
                    extra.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { action(); menu = false })
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(c: ChannelItem, vm: AppVm) {
    Row(
        Modifier.fillMaxWidth().clickable { vm.push(Screen.Channel(c.url)) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = c.avatar, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            if (c.subs >= 0) Text("${fmtCount(c.subs)} subscribers", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { vm.toggleSub(c) }) { Text(if (vm.isSub(c.url)) "Subscribed" else "Subscribe") }
    }
}

@Composable
fun PlaylistCard(p: PlaylistItem, vm: AppVm) {
    Row(Modifier.fillMaxWidth().clickable { vm.push(Screen.Playlist(p.url)) }.padding(12.dp)) {
        AsyncImage(
            model = p.thumb, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(140.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("${p.uploader} • ${p.count} videos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EntryCard(e: Entry, vm: AppVm) {
    when (e) {
        is VideoItem -> VideoCard(e, vm)
        is ChannelItem -> ChannelCard(e, vm)
        is PlaylistItem -> PlaylistCard(e, vm)
    }
}

/** Infinite-scrolling list backed by a [Paged] source. */
@Composable
fun FeedList(
    paged: Paged,
    vm: AppVm,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
) {
    LaunchedEffect(paged) { if (!paged.started) paged.refresh() }
    LaunchedEffect(paged, state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= state.layoutInfo.totalItemsCount - 4) paged.more() }
    }
    LazyColumn(modifier.fillMaxSize(), state = state) {
        if (header != null) item { header() }
        items(paged.items) { EntryCard(it, vm) }
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    paged.loading -> CircularProgressIndicator(Modifier.size(28.dp))
                    paged.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(paged.error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { if (paged.items.isEmpty()) paged.refresh() else paged.more() }) { Text("Retry") }
                    }
                }
            }
        }
    }
}
