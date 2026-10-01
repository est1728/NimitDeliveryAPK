package com.nimit.delivery.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** หา release ล่าสุดที่ tag ขึ้นต้นด้วย prefix (นับ prerelease ด้วย) คืน (เลข build, ลิงก์ APK) */
suspend fun fetchByPrefix(prefix: String): Pair<Int, String>? {
    return withContext<Pair<Int, String>?>(Dispatchers.IO) {
        try {
            val c = URL("https://api.github.com/repos/est1728/NimitDelivery-releases/releases?per_page=30").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return@withContext null
            val arr = JSONArray(c.inputStream.bufferedReader().readText())
            var bestCode: Int = -1
            var bestUrl: String = ""
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                if (j.optBoolean("draft")) continue
                val tag: String = j.getString("tag_name")
                if (!tag.startsWith(prefix)) continue
                val code: Int = tag.removePrefix(prefix).toIntOrNull() ?: continue
                if (code <= bestCode) continue
                val assets = j.getJSONArray("assets")
                for (k in 0 until assets.length()) {
                    val a = assets.getJSONObject(k)
                    if (a.getString("name").endsWith(".apk")) {
                        bestCode = code
                        bestUrl = a.getString("browser_download_url")
                        break
                    }
                }
            }
            if (bestUrl.isEmpty()) null else Pair<Int, String>(bestCode, bestUrl)
        } catch (_: Exception) {
            null
        }
    }
}
