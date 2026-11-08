package app.playnest

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppVm(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    var history by mutableStateOf(store.loadVideos("history")); private set
    var liked by mutableStateOf(store.loadVideos("liked")); private set
    var later by mutableStateOf(store.loadVideos("later")); private set
    var subs by mutableStateOf(store.loadChannels()); private set
    var searches by mutableStateOf(store.loadSearches()); private set

    val ctl = PlayerCtl(app, store, viewModelScope) { addHistory(it) }

    // navigation
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var tab by mutableStateOf<Screen>(Screen.Home); private set

    fun openTab(s: Screen) { tab = s; stack.clear(); stack.add(s) }
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    // feeds
    val home = Paged(viewModelScope) { Repo.home(it) }
    val subsFeed = Paged(viewModelScope) { Chunk(Repo.subsFeed(subs), null) }

    // search
    var query by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set
    var results by mutableStateOf<Paged?>(null); private set
    private var suggestJob: Job? = null

    fun onQueryChange(q: String) {
        query = q
        suggestJob?.cancel()
        if (q.isBlank()) { suggestions = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = runCatching { Repo.suggestions(q) }.getOrDefault(emptyList())
        }
    }

    fun submit(q: String) {
        val term = q.trim()
        if (term.isEmpty()) return
        query = term
        suggestJob?.cancel()
        suggestions = emptyList()
        searches = (listOf(term) + searches.filter { it != term }).take(12)
        store.saveSearches(searches)
        results = Paged(viewModelScope) { Repo.search(term, it) }.also { it.refresh() }
    }

    fun clearSearches() { searches = emptyList(); store.saveSearches(searches) }

    // library
    private fun addHistory(v: VideoItem) {
        history = (listOf(v) + history.filter { it.url != v.url }).take(300)
        store.saveVideos("history", history)
    }

    fun clearHistory() { history = emptyList(); store.saveVideos("history", history) }

    fun isLiked(url: String) = liked.any { it.url == url }

    fun toggleLike(v: VideoItem) {
        liked = if (isLiked(v.url)) liked.filter { it.url != v.url } else listOf(v) + liked
        store.saveVideos("liked", liked)
    }

    fun isLater(url: String) = later.any { it.url == url }

    fun toggleLater(v: VideoItem) {
        later = if (isLater(v.url)) later.filter { it.url != v.url } else listOf(v) + later
        store.saveVideos("later", later)
    }

    fun removeFrom(list: String, v: VideoItem) {
        when (list) {
            "history" -> { history = history.filter { it.url != v.url }; store.saveVideos("history", history) }
            "liked" -> { liked = liked.filter { it.url != v.url }; store.saveVideos("liked", liked) }
            "later" -> { later = later.filter { it.url != v.url }; store.saveVideos("later", later) }
        }
    }

    fun isSub(url: String) = subs.any { it.url == url }

    fun toggleSub(c: ChannelItem) {
        subs = if (isSub(c.url)) subs.filter { it.url != c.url } else listOf(c) + subs
        store.saveChannels(subs)
        subsFeed.refresh()
    }

    /** Open a youtube.com / youtu.be link shared into the app. */
    fun openLink(url: String) {
        when {
            "/@" in url || "/channel/" in url || "/c/" in url -> { ctl.expanded = false; push(Screen.Channel(url)) }
            "list=" in url && "v=" !in url && "youtu.be" !in url -> { ctl.expanded = false; push(Screen.Playlist(url)) }
            else -> ctl.play(VideoItem(url = url))
        }
    }

    override fun onCleared() { ctl.release() }
}
