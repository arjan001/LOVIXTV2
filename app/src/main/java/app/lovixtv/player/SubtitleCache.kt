/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.player

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Caches Vosk-generated subtitle files locally so they play even when the server is down.
 *
 * On first playback, the subtitle is fetched from the server and saved to the app's cache
 * directory. Subsequent playbacks use the cached file directly. If the server is unreachable,
 * the cached copy is used as a fallback.
 *
 * Cache directory: `<app_cache>/subtitles/{mediaKey}_{lang}.vtt`
 * Cache TTL: 7 days — after that the file is re-fetched in the background.
 */
class SubtitleCache(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val CACHE_DIR = "subtitles"
        private const val TTL_MILLIS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }

    private val cacheDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    /**
     * Returns a list of (language, Uri) pairs for subtitle tracks.
     *
     * For each language, tries to serve from cache first. If the cache is stale or missing,
     * attempts to download from the server URL. If the server is down, falls back to any
     * cached copy regardless of age.
     *
     * @param mediaKey unique identifier for the movie/episode (used for cache file naming)
     * @param serverTracks list of (language code, server URL) pairs
     * @return list of (language code, Uri) pairs — only tracks that could be resolved
     */
    suspend fun resolveTracks(
        mediaKey: String,
        serverTracks: List<Pair<String, String>>,
    ): List<Pair<String, Uri>> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                serverTracks.map { (lang, serverUrl) ->
                    async {
                        runCatching { resolveOne(mediaKey, lang, serverUrl) }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
        }

    private suspend fun resolveOne(
        mediaKey: String,
        lang: String,
        serverUrl: String,
    ): Pair<String, Uri>? {
        val cacheFile = cacheFile(mediaKey, lang)

        // Try cache first if file exists and not stale
        if (cacheFile.exists()) {
            val age = System.currentTimeMillis() - cacheFile.lastModified()
            if (age < TTL_MILLIS) {
                // Fresh cache hit
                return lang to cacheFile.toUri()
            }
            // Stale cache — try server, fall back to stale file
            val downloaded = downloadToFile(serverUrl, cacheFile)
            if (downloaded) {
                return lang to cacheFile.toUri()
            }
            // Server failed — use stale cache as fallback
            return lang to cacheFile.toUri()
        }

        // No cache — try server
        val downloaded = downloadToFile(serverUrl, cacheFile)
        return if (downloaded) {
            lang to cacheFile.toUri()
        } else {
            null // No cache, server down — skip this track
        }
    }

    private fun cacheFile(mediaKey: String, lang: String): File {
        // Sanitize media key to be filesystem-safe
        val safeKey = mediaKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(cacheDir, "${safeKey}_${lang}.vtt")
    }

    /**
     * Returns a Uri for a cached subtitle file, or null if not cached.
     */
    fun getCachedUri(mediaKey: String, lang: String): Uri? {
        val f = cacheFile(mediaKey, lang)
        return if (f.exists()) f.toUri() else null
    }

    /**
     * Pre-fetch all subtitle tracks for a given media key in the background.
     * Called when entering a movie detail screen so subtitles are ready by the time playback starts.
     */
    suspend fun prefetchTracks(mediaKey: String, serverTracks: List<Pair<String, String>>) {
        resolveTracks(mediaKey, serverTracks)
    }

    /** Clear all cached subtitle files. */
    fun clear() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }

    private suspend fun downloadToFile(url: String, dest: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LOVIXTV")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching false
                    response.body?.byteStream()?.use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                }
            }.getOrDefault(false)
        }
}
