package model.remote

import com.google.gson.JsonParser
import java.net.URL
import java.net.URLEncoder

class YouTubeImpl(private val apiKey: String) {
    fun searchYoutube(query: String): String? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val apiUrl =
            "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=1&q=$encodedQuery&key=${apiKey}"

        return try {
            val resultJson = URL(apiUrl).readText()
            val jsonObject = JsonParser.parseString(resultJson).asJsonObject
            val items = jsonObject.getAsJsonArray("items")
            if (items.size() > 0) {
                val videoId = items.get(0).asJsonObject.getAsJsonObject("id").get("videoId").asString
                "https://www.youtube.com/watch?v=$videoId"
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}