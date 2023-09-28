package model.remote

import com.google.gson.JsonParser
import java.net.URL
import java.net.URLEncoder

class YouTubeImpl(private val apiKey: String) {
    fun searchYoutube(query: String): String? {
        val apiUrl = buildYoutubeApiUrl(query)
        return try {
            val resultJson = URL(apiUrl).readText()
            extractVideoIdFromJson(resultJson)?.let { videoId ->
                "https://www.youtube.com/watch?v=$videoId"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun fetchInfo(link: String): String? {
        val videoId = extractParameter(link, "v=")
        val playlistId = extractParameter(link, "list=")

        return when {
            playlistId != null -> fetchPlaylistTitle(playlistId)
            videoId != null -> fetchVideoTitle(videoId)
            else -> null
        }
    }

    private fun extractParameter(link: String, key: String): String? {
        val split = link.split(key)
        return if (split.size > 1) split[1].split("&").getOrNull(0) else null
    }

    private fun fetchPlaylistTitle(playlistId: String): String? {
        val url = buildUrl("playlists", playlistId)
        val json = URL(url).readText()
        return extractTitleFromJson(json)
    }

    private fun fetchVideoTitle(videoId: String): String? {
        val url = buildUrl("videos", videoId)
        val json = URL(url).readText()
        return extractTitleFromJson(json)
    }

    private fun buildUrl(type: String, id: String): String {
        val encodedId = encodeQuery(id)
        return "$BASE_URL$type?id=$encodedId&key=$apiKey&part=snippet"
    }

    private fun extractTitleFromJson(json: String): String? {
        val jsonObj = JsonParser.parseString(json).asJsonObject
        val items = jsonObj.getAsJsonArray("items")
        return if (items.size() > 0) {
            val snippet = items[0].asJsonObject.getAsJsonObject("snippet")
            snippet.getAsJsonPrimitive("title").asString
        } else null
    }

    private fun encodeQuery(query: String): String {
        return URLEncoder.encode(query, "UTF-8")
    }


    private fun buildYoutubeApiUrl(query: String): String {
        val encodedQuery = encodeQuery(query)
        return "${BASE_URL}search?part=snippet&type=video&maxResults=1&q=$encodedQuery&key=$apiKey"
    }

    private fun extractVideoIdFromJson(resultJson: String): String? {
        val jsonObject = JsonParser.parseString(resultJson).asJsonObject
        val items = jsonObject.getAsJsonArray("items")
        return if (items.size() > 0) {
            items[0].asJsonObject.getAsJsonObject("id")["videoId"].asString
        } else {
            null
        }
    }

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"
    }
}