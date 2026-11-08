package app.playnest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playnest.AppVm
import app.playnest.ChannelData
import app.playnest.ChannelItem
import app.playnest.Chunk
import app.playnest.Paged
import app.playnest.PlaylistData
import app.playnest.Repo
import app.playnest.Screen
import app.playnest.VideoItem
import app.playnest.fmtCount
import coil.compose.AsyncImage

// ---------------- Home ----------------

@Composable
fun HomeScreen(vm: AppVm) {
    Column(Modifier.fillMaxSize()) {
        Bar("PlayNest", actions = {
            IconButton(onClick = { vm.home.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
            IconButton(onClick = { vm.push(Screen.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
        })
        FeedList(vm.home, vm)
    }
}

// ---------------- Search ----------------

@Composable
fun SearchScreen(vm: AppVm) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            singleLine = true,
            placeholder = { Text("Search videos, channels, playlists") },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, "Clear") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.submit(vm.query); focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        val results = vm.results
        when {
            vm.suggestions.isNotEmpty() -> LazyColumn {
                items(vm.suggestions) { s ->
                    Text(s, Modifier.fillMaxWidth().clickable { vm.submit(s); focus.clearFocus() }.padding(16.dp))
                }
            }
            results != null -> FeedList(results, vm)
            else -> LazyColumn {
                if (vm.searches.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { vm.clearSearches() }) { Text("Clear") }
                        }
                    }
                    items(vm.searches) { s ->
                        Row(Modifier.fillMaxWidth().clickable { vm.submit(s); focus.clearFocus() }.padding(16.dp)) {
                            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Subscriptions ----------------

@Composable
fun SubsScreen(vm: AppVm) {
    LaunchedEffect(vm.subs.size) { if (!vm.subsFeed.started || vm.subsFeed.items.isEmpty()) vm.subsFeed.refresh() }
    Column(Modifier.fillMaxSize()) {
        Bar("Subscriptions", actions = {
            IconButton(onClick = { vm.subsFeed.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
        })
        if (vm.subs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Subscribe to channels and their latest videos show up here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
            }
        } else {
            LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(vm.subs) { c ->
                    Column(Modifier.clickable { vm.push(Screen.Channel(c.url)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(c.avatar, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                        Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp).size(width = 64.dp, height = 16.dp))
                    }
                }
            }
            FeedList(vm.subsFeed, vm)
        }
    }
}

// ---------------- Library ----------------

@Composable
fun LibraryScreen(vm: AppVm) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val names = listOf("History", "Liked", "Watch later")
    val keys = listOf("history", "liked", "later")
    val list = when (section) { 0 -> vm.history; 1 -> vm.liked; else -> vm.later }
    Column(Modifier.fillMaxSize()) {
        Bar("Library", actions = {
            IconButton(onClick = { vm.push(Screen.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
        })
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { i, n ->
                FilterChip(selected = section == i, onClick = { section = i }, label = { Text("$n") })
            }
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(list, key = { it.url }) { v ->
                    VideoCard(
                        v, vm,
                        extra = listOf("Remove from ${names[section]}" to { vm.removeFrom(keys[section], v) }),
                        onClick = { vm.ctl.play(v, list, list.indexOf(v)) },
                    )
                }
            }
        }
    }
}

// ---------------- Channel ----------------

@Composable
fun ChannelScreen(vm: AppVm, url: String) {
    var data by remember(url) { mutableStateOf<ChannelData?>(null) }
    val scope = rememberCoroutineScope()
    val paged = remember(url) {
        Paged(scope) { page ->
            if (page == null) {
                val d = Repo.channel(url)
                data = d
                d.first
            } else {
                val t = data?.tab
                if (t == null) Chunk(emptyList(), null) else Repo.channelMore(t, page)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Bar(data?.name ?: "Channel", back = { vm.pop() })
        FeedList(paged, vm, header = {
            data?.let { d ->
                val ch = ChannelItem(url, d.name, d.avatar, d.subs)
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(d.avatar, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (d.subs >= 0) Text("${fmtCount(d.subs)} subscribers", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.toggleSub(ch) }) { Text(if (vm.isSub(url)) "Subscribed" else "Subscribe") }
                }
                if (d.desc.isNotBlank()) Text(d.desc, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        })
    }
}

// ---------------- Playlist ----------------

@Composable
fun PlaylistScreen(vm: AppVm, url: String) {
    var data by remember(url) { mutableStateOf<PlaylistData?>(null) }
    val scope = rememberCoroutineScope()
    val paged = remember(url) {
        Paged(scope) { page ->
            if (page == null) {
                val d = Repo.playlist(url)
                data = d
                d.first
            } else Repo.playlistMore(url, page)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Bar(data?.name ?: "Playlist", back = { vm.pop() })
        FeedList(paged, vm, header = {
            data?.let { d ->
                Column(Modifier.padding(16.dp)) {
                    Text(d.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${d.uploader} • ${d.count} videos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val vids = paged.items.filterIsInstance<VideoItem>()
                            if (vids.isNotEmpty()) vm.ctl.play(vids[0], vids, 0)
                        }) { Text("Play all") }
                    }
                }
            }
        })
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(vm: AppVm) {
    val s = vm.store
    Column(Modifier.fillMaxSize()) {
        Bar("Settings", back = { vm.pop() })
        Column(Modifier.padding(16.dp)) {
            Text("Preferred max quality", fontWeight = FontWeight.Bold)
            LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(360, 480, 720, 1080, 1440, 2160)) { q ->
                    FilterChip(selected = s.maxQuality == q, onClick = { s.setMaxQuality(q) }, label = { Text("${q}p") })
                }
            }
            SwitchRow("SponsorBlock (skip sponsors, intros, outros)", s.sponsorBlock) { s.setSponsorBlock(it) }
            SwitchRow("Autoplay next video", s.autoplay) { s.setAutoplay(it) }
            OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.padding(top = 16.dp)) { Text("Clear watch history") }
            Text("PlayNest 1.0 — all data stays on this device.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
