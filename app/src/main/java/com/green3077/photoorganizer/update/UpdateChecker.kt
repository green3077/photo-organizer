package com.green3077.photoorganizer.update

import com.green3077.photoorganizer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String
)

sealed class UpdateCheckResult {
    data class Available(val update: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    object Error : UpdateCheckResult()
}

/**
 * GitHub Releases에서 최신 릴리스를 확인한다. CI가 태그를 "v<versionCode>"로 붙이므로
 * 태그에서 바로 versionCode를 뽑아 현재 앱과 비교한다 (release-workflow의 태그 규칙과 짝을 이룸).
 */
object UpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/green3077/photo-organizer/releases/latest"

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext UpdateCheckResult.Error

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name")
            val remoteVersionCode = tagName.removePrefix("v").toIntOrNull()
                ?: return@withContext UpdateCheckResult.Error
            if (remoteVersionCode <= BuildConfig.VERSION_CODE) return@withContext UpdateCheckResult.UpToDate

            val assets = json.optJSONArray("assets") ?: return@withContext UpdateCheckResult.Error
            var downloadUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    downloadUrl = asset.optString("browser_download_url")
                    break
                }
            }

            val url = downloadUrl ?: return@withContext UpdateCheckResult.Error
            UpdateCheckResult.Available(UpdateInfo(remoteVersionCode, json.optString("name", tagName), url))
        } catch (e: Exception) {
            UpdateCheckResult.Error
        } finally {
            connection.disconnect()
        }
    }
}
