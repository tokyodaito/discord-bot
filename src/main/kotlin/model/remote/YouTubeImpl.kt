package model.remote

import com.google.gson.JsonParser
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

class YouTubeImpl(private val apiKey: String) {
    fun searchYoutube(query: String): String? {
        val apiUrl = buildYoutubeApiUrl(query)
        return readUrl(apiUrl)?.let { json ->
            extractVideoIdFromJson(json)?.let { videoId ->
                "https://www.youtube.com/watch?v=$videoId"
            }
        }
    }

    fun fetchInfo(link: String): String? {
        val normalized = normalizeLink(link)
        val ids = extractIds(normalized)

        return when {
            ids.playlistId != null -> fetchPlaylistTitle(ids.playlistId)
            ids.videoId != null -> fetchVideoTitle(ids.videoId)
            else -> null
        }
    }

    private fun normalizeLink(link: String): String {
        val trimmed = link.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    private fun fetchPlaylistTitle(playlistId: String): String? {
        return getCached("playlist:$playlistId") {
            val url = buildUrl("playlists", playlistId)
            readUrl(url)?.let { extractTitleFromJson(it) }
        }
    }

    private fun fetchVideoTitle(videoId: String): String? {
        return getCached("video:$videoId") {
            val url = buildUrl("videos", videoId)
            readUrl(url)?.let { extractTitleFromJson(it) }
        }
    }

    private fun buildUrl(type: String, id: String): String {
        val encodedId = encodeQuery(id)
        return "$BASE_URL$type?id=$encodedId&key=$apiKey&part=snippet"
    }

    private fun extractTitleFromJson(json: String): String? {
        return runCatching {
            val jsonObj = JsonParser.parseString(json).asJsonObject
            val items = jsonObj.getAsJsonArray("items") ?: return null
            if (items.size() <= 0) return null
            val snippet = items[0].asJsonObject.getAsJsonObject("snippet") ?: return null
            snippet.getAsJsonPrimitive("title")?.asString
        }.getOrNull()
    }

    private fun encodeQuery(query: String): String {
        return URLEncoder.encode(query, StandardCharsets.UTF_8)
    }


    private fun buildYoutubeApiUrl(query: String): String {
        val encodedQuery = encodeQuery(query)
        return "${BASE_URL}search?part=snippet&type=video&maxResults=1&q=$encodedQuery&key=$apiKey"
    }

    private fun extractVideoIdFromJson(resultJson: String): String? {
        return runCatching {
            val jsonObject = JsonParser.parseString(resultJson).asJsonObject
            val items = jsonObject.getAsJsonArray("items") ?: return null
            if (items.size() <= 0) return null
            items[0].asJsonObject.getAsJsonObject("id")?.getAsJsonPrimitive("videoId")?.asString
        }.getOrNull()
    }

    private fun readUrl(url: String): String? {
        return runCatching {
            val connection = URL(url).openConnection().apply {
                connectTimeout = 3_000
                readTimeout = 5_000
            }
            connection.getInputStream().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }

    private data class ExtractedIds(val videoId: String?, val playlistId: String?)

    private fun extractIds(link: String): ExtractedIds {
        val uri = runCatching { URI(link) }.getOrNull() ?: return ExtractedIds(null, null)
        val host = uri.host.orEmpty().lowercase()
        val queryParams = parseQuery(uri.rawQuery.orEmpty())

        val playlistId = queryParams["list"]
        val videoId = when {
            host.endsWith("youtu.be") -> uri.path.trim('/').split('/').firstOrNull()
            host.contains("youtube.com") && uri.path == "/watch" -> queryParams["v"]
            else -> queryParams["v"]
        }

        return ExtractedIds(videoId, playlistId)
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = part.substring(0, idx)
                val value = part.substring(idx + 1)
                if (key.isBlank()) null else key to value
            }
            .toMap()
    }

    private val titleCache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 500
    }

    private fun getCached(key: String, loader: () -> String?): String? {
        synchronized(titleCache) {
            titleCache[key]?.let { return it }
        }
        val loaded = loader() ?: return null
        synchronized(titleCache) {
            titleCache[key] = loaded
        }
        return loaded
    }

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"
    }
}
