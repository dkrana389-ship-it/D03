package com.deep.focus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Finds photos for a keyword (for example "Mahindra Scorpio") using the free
 * Wikipedia and Wikimedia Commons search APIs. No API key is needed.
 */
object PhotoFinder {

    private const val UA = "FocusApp/1.1 (Android personal focus app)"

    private fun get(url: String): ByteArray? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun parse(json: String, pick: (JSONObject) -> String?): List<String> {
        val found = ArrayList<Pair<Int, String>>()
        try {
            val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages")
                ?: return emptyList()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.getJSONObject(keys.next())
                val url = pick(page) ?: continue
                found.add(Pair(page.optInt("index", 999), url))
            }
        } catch (e: Exception) {
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    /** Returns candidate image URLs, best match first. */
    fun search(query: String): List<String> {
        val result = LinkedHashSet<String>()

        val wiki = get(
            "https://en.wikipedia.org/w/api.php?action=query&format=json" +
                "&generator=search&gsrsearch=${enc(query)}&gsrlimit=8" +
                "&prop=pageimages&piprop=thumbnail&pithumbsize=1000"
        )
        if (wiki != null) {
            result.addAll(parse(String(wiki, Charsets.UTF_8)) { page ->
                page.optJSONObject("thumbnail")?.optString("source")?.takeIf { s -> s.isNotEmpty() }
            })
        }

        val commons = get(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                "&generator=search&gsrnamespace=6&gsrsearch=${enc("$query filetype:bitmap")}" +
                "&gsrlimit=15&prop=imageinfo&iiprop=url&iiurlwidth=1000"
        )
        if (commons != null) {
            result.addAll(parse(String(commons, Charsets.UTF_8)) { page ->
                page.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("thumburl")
                    ?.takeIf { s -> s.isNotEmpty() }
            })
        }
        return result.toList()
    }

    /** Downloads and decodes one image, scaled down to at most ~1600px. */
    fun download(url: String): Bitmap? {
        val data = get(url) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth < 300 || bounds.outHeight < 200) return null
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }
}
