package app.playnest

import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Net {
    const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/** Bridge between NewPipe Extractor and OkHttp. */
class AppDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody()
            method == "POST" -> ByteArray(0).toRequestBody()
            else -> null
        }
        val builder = OkRequest.Builder().method(method, body).url(url)
            .addHeader("User-Agent", Net.WEB_UA)
        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            for (v in values) builder.addHeader(name, v)
        }
        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha challenge requested", url)
            val text = resp.body?.string()
            return Response(resp.code, resp.message, resp.headers.toMultimap(), text, resp.request.url.toString())
        }
    }
}

/** Public SponsorBlock API (sponsor.ajay.app). */
object SponsorBlock {
    data class Seg(val start: Long, val end: Long, val category: String)

    fun fetch(videoId: String): List<Seg> {
        val cats = URLEncoder.encode("[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\"]", "UTF-8")
        val actions = URLEncoder.encode("[\"skip\"]", "UTF-8")
        val req = OkRequest.Builder()
            .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$cats&actionTypes=$actions")
            .build()
        return runCatching {
            Net.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use emptyList<Seg>()
                val arr = JSONArray(r.body?.string() ?: "[]")
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val s = o.getJSONArray("segment")
                    Seg((s.getDouble(0) * 1000).toLong(), (s.getDouble(1) * 1000).toLong(), o.optString("category"))
                }.sortedBy { it.start }
            }
        }.getOrDefault(emptyList())
    }
}
