package com.flowseal.tgwsproxy.util

import android.content.Context
import com.flowseal.tgwsproxy.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val latestVersion: String,
    val htmlUrl: String,
    val apkUrl: String? = null,
    val apkName: String? = null,
)

/**
 * Optional APK update notice. Only surfaces when the latest GitHub Release
 * actually contains an .apk asset — never just opens the upstream desktop page.
 */
object UpdateChecker {
    private const val REPO = "MushroomSquad/tg-ws-proxy-android"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    private const val MIN_INTERVAL_MS = 3_600_000L

    suspend fun check(context: Context, force: Boolean = false): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.filesDir, "update_check_cache.json")
            val cache = readCache(cacheFile)
            val now = System.currentTimeMillis()
            val last = cache.optLong("last_attempt_at", 0L)
            if (!force && last > 0 && now - last < MIN_INTERVAL_MS) {
                val tag = cache.optString("tag_name", "")
                val apkUrl = cache.optString("apk_url").ifBlank { null }
                if (tag.isNotBlank() && apkUrl != null && isNewer(tag, BuildConfig.VERSION_NAME)) {
                    return@withContext UpdateInfo(
                        latestVersion = tag.trimStart('v', 'V'),
                        htmlUrl = cache.optString("html_url", RELEASES_PAGE).ifBlank { RELEASES_PAGE },
                        apkUrl = apkUrl,
                        apkName = cache.optString("apk_name").ifBlank { null },
                    )
                }
                return@withContext null
            }

            try {
                val etag = cache.optString("etag", "").ifBlank { null }
                val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "tg-ws-proxy-android")
                    if (etag != null) setRequestProperty("If-None-Match", etag)
                }
                val code = conn.responseCode
                if (code == 304) {
                    cache.put("last_attempt_at", now)
                    writeCache(cacheFile, cache)
                    val tag = cache.optString("tag_name", "")
                    val apkUrl = cache.optString("apk_url").ifBlank { null }
                    if (tag.isNotBlank() && apkUrl != null && isNewer(tag, BuildConfig.VERSION_NAME)) {
                        return@withContext UpdateInfo(
                            latestVersion = tag.trimStart('v', 'V'),
                            htmlUrl = cache.optString("html_url", RELEASES_PAGE).ifBlank { RELEASES_PAGE },
                            apkUrl = apkUrl,
                            apkName = cache.optString("apk_name").ifBlank { null },
                        )
                    }
                    return@withContext null
                }
                if (code !in 200..299) {
                    cache.put("last_attempt_at", now)
                    cache.put("last_error", "HTTP $code")
                    writeCache(cacheFile, cache)
                    AppLog.w("Update check failed: HTTP $code")
                    return@withContext null
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(body)
                val tag = data.optString("tag_name", "").trim()
                val htmlUrl = data.optString("html_url", RELEASES_PAGE).ifBlank { RELEASES_PAGE }
                val (apkUrl, apkName) = pickApkAsset(data)

                cache.put("last_attempt_at", now)
                cache.put("tag_name", tag)
                cache.put("html_url", htmlUrl)
                cache.put("apk_url", apkUrl ?: "")
                cache.put("apk_name", apkName ?: "")
                conn.getHeaderField("ETag")?.let { cache.put("etag", it) }
                cache.remove("last_error")
                writeCache(cacheFile, cache)

                if (tag.isNotBlank() && apkUrl != null && isNewer(tag, BuildConfig.VERSION_NAME)) {
                    AppLog.i("APK update available: v${tag.trimStart('v', 'V')} ($apkName)")
                    UpdateInfo(
                        latestVersion = tag.trimStart('v', 'V'),
                        htmlUrl = htmlUrl,
                        apkUrl = apkUrl,
                        apkName = apkName,
                    )
                } else {
                    if (tag.isNotBlank() && isNewer(tag, BuildConfig.VERSION_NAME) && apkUrl == null) {
                        AppLog.i("Upstream tag $tag is newer, but no Android APK in that release — skip dialog")
                    }
                    null
                }
            } catch (e: Exception) {
                cache.put("last_attempt_at", now)
                cache.put("last_error", e.message ?: e.javaClass.simpleName)
                writeCache(cacheFile, cache)
                AppLog.w("Update check failed: ${e.javaClass.simpleName}")
                null
            }
        }

    fun isNewer(remote: String, current: String): Boolean {
        val a = parseVersion(remote)
        val b = parseVersion(current)
        val n = max(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x > y) return true
            if (x < y) return false
        }
        return false
    }

    private fun parseVersion(s: String): List<Int> =
        s.trim().trimStart('v', 'V').split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            .ifEmpty { listOf(0) }

    private fun pickApkAsset(data: JSONObject): Pair<String?, String?> {
        val assets = data.optJSONArray("assets") ?: return null to null
        val candidates = mutableListOf<Pair<String, String>>()
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val url = a.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                candidates += name to url
            }
        }
        if (candidates.isEmpty()) return null to null
        val preferred = candidates.firstOrNull { (name, _) ->
            name.contains("android", ignoreCase = true) ||
                name.contains("tgwsproxy", ignoreCase = true)
        } ?: candidates.first()
        return preferred.second to preferred.first
    }

    private fun readCache(file: File): JSONObject {
        if (!file.isFile) return JSONObject()
        return try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeCache(file: File, obj: JSONObject) {
        try {
            file.writeText(obj.toString())
        } catch (_: Exception) {
        }
    }
}
