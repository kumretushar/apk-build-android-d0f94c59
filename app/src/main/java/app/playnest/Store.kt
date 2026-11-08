package app.playnest

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Local-only storage: history, liked, watch later, subscriptions, resume positions, settings. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("playnest", Context.MODE_PRIVATE)
    private val rs = ctx.getSharedPreferences("resume", Context.MODE_PRIVATE)

    var maxQuality by mutableIntStateOf(sp.getInt("maxq", 1080)); private set
    var sponsorBlock by mutableStateOf(sp.getBoolean("sb", true)); private set
    var autoplay by mutableStateOf(sp.getBoolean("auto", true)); private set

    fun setMaxQuality(v: Int) { maxQuality = v; sp.edit().putInt("maxq", v).apply() }
    fun setSponsorBlock(v: Boolean) { sponsorBlock = v; sp.edit().putBoolean("sb", v).apply() }
    fun setAutoplay(v: Boolean) { autoplay = v; sp.edit().putBoolean("auto", v).apply() }

    fun resume(url: String): Long = rs.getLong(url, 0)
    fun saveResume(url: String, ms: Long) { rs.edit().putLong(url, ms).apply() }

    fun loadVideos(key: String): List<VideoItem> = runCatching {
        val a = JSONArray(sp.getString(key, "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            VideoItem(
                url = o.getString("url"), title = o.optString("title"), uploader = o.optString("uploader"),
                uploaderUrl = o.optString("uploaderUrl"), thumb = o.optString("thumb").ifEmpty { null },
                duration = o.optLong("duration"), views = o.optLong("views", -1),
                uploaded = o.optString("uploaded").ifEmpty { null }, uploadedMs = o.optLong("uploadedMs", -1),
            )
        }
    }.getOrDefault(emptyList())

    fun saveVideos(key: String, list: List<VideoItem>) {
        val a = JSONArray()
        list.forEach { v ->
            a.put(JSONObject().apply {
                put("url", v.url); put("title", v.title); put("uploader", v.uploader)
                put("uploaderUrl", v.uploaderUrl); put("thumb", v.thumb ?: ""); put("duration", v.duration)
                put("views", v.views); put("uploaded", v.uploaded ?: ""); put("uploadedMs", v.uploadedMs)
            })
        }
        sp.edit().putString(key, a.toString()).apply()
    }

    fun loadChannels(): List<ChannelItem> = runCatching {
        val a = JSONArray(sp.getString("subs", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            ChannelItem(o.getString("url"), o.optString("name"), o.optString("avatar").ifEmpty { null }, o.optLong("subs", -1))
        }
    }.getOrDefault(emptyList())

    fun saveChannels(list: List<ChannelItem>) {
        val a = JSONArray()
        list.forEach { c ->
            a.put(JSONObject().apply {
                put("url", c.url); put("name", c.name); put("avatar", c.avatar ?: ""); put("subs", c.subs)
            })
        }
        sp.edit().putString("subs", a.toString()).apply()
    }

    fun loadSearches(): List<String> =
        sp.getString("searches", "")!!.split("\n").filter { it.isNotBlank() }

    fun saveSearches(list: List<String>) { sp.edit().putString("searches", list.joinToString("\n")).apply() }
}
