package app.playnest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

/** Infinite-scroll list state: refresh() loads the first page, more() loads the next one. */
class Paged(
    private val scope: CoroutineScope,
    private val loader: suspend (Page?) -> Chunk<Entry>,
) {
    var items by mutableStateOf<List<Entry>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var started = false; private set
    private var next: Page? = null
    private var gen = 0

    fun refresh() {
        started = true
        val g = ++gen
        loading = true
        error = null
        scope.launch {
            try {
                val c = loader(null)
                if (g != gen) return@launch
                items = c.items
                next = c.next
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (g == gen) error = e.message ?: e.javaClass.simpleName
            } finally {
                if (g == gen) loading = false
            }
        }
    }

    fun more() {
        val n = next ?: return
        if (loading) return
        val g = gen
        loading = true
        scope.launch {
            try {
                val c = loader(n)
                if (g != gen) return@launch
                val seen = items.map { (it as? VideoItem)?.url ?: (it as? ChannelItem)?.url ?: (it as? PlaylistItem)?.url }.toSet()
                items = items + c.items.filter {
                    val k = (it as? VideoItem)?.url ?: (it as? ChannelItem)?.url ?: (it as? PlaylistItem)?.url
                    k !in seen
                }
                next = c.next
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (g == gen) error = e.message ?: e.javaClass.simpleName
            } finally {
                if (g == gen) loading = false
            }
        }
    }
}
