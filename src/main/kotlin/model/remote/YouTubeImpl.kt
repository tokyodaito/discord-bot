package model.remote

import com.google.gson.JsonParser
import java.net.URL
import java.net.URLEncoder

class YouTubeImpl(private val apiKey: String) {
    fun searchYoutube(query: String): String? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val apiUrl =
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=1&q=$encodedQuery&key=${apiKey}"

        return try {
            val resultJson = URL(apiUrl).readText()
            val jsonObject = JsonParser.parseString(resultJson).asJsonObject
            val items = jsonObject.getAsJsonArray("items")
            if (items.size() > 0) {
                val videoId = items[0].asJsonObject.getAsJsonObject("id")["videoId"].asString
                "https://www.youtube.com/watch?v=$videoId"
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun fetchInfo(link: String): String? {
        val videoSplit = link.split("v=")
        val videoId: String? = if (videoSplit.size > 1) videoSplit[1].split("&").getOrNull(0) else null

        val playlistSplit = link.split("list=")
        val playlistId: String? = if (playlistSplit.size > 1) playlistSplit[1].split("&").getOrNull(0) else null


        if (playlistId != null) {
            // Получение информации о плейлисте
            val playlistUrl = "https://www.googleapis.com/youtube/v3/playlists?id=${
                URLEncoder.encode(
                    playlistId,
                    "UTF-8"
                )
            }&key=$apiKey&part=snippet"
            val playlistJson = URL(playlistUrl).readText()
            val playlistObj = JsonParser.parseString(playlistJson).asJsonObject
            val items = playlistObj.getAsJsonArray("items")

            if (items.size() > 0) {
                val snippet = items[0].asJsonObject.getAsJsonObject("snippet")
                return snippet.getAsJsonPrimitive("title").asString
            }
        } else if (videoId != null) {
            // Получение информации о видео
            val videoUrl = "https://www.googleapis.com/youtube/v3/videos?id=${
                URLEncoder.encode(
                    videoId,
                    "UTF-8"
                )
            }&key=$apiKey&part=snippet"
            val videoJson = URL(videoUrl).readText()
            val videoObj = JsonParser.parseString(videoJson).asJsonObject
            val items = videoObj.getAsJsonArray("items")

            if (items.size() > 0) {
                val snippet = items[0].asJsonObject.getAsJsonObject("snippet")
                return snippet.getAsJsonPrimitive("title").asString
            }
        }
        return null
    }
}