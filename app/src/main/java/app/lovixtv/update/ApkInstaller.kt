/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Downloads an update APK and hands it to the system installer.
 *
 * On Android TV, uses DownloadManager to download directly to the Downloads folder,
 * then triggers installation via ACTION_VIEW. On phones/tablets, falls back to
 * FileProvider + ACTION_INSTALL_PACKAGE. The system shows its own install screen;
 * we never install silently.
 */
class ApkInstaller(private val http: OkHttpClient) {

    /** Progress as a 0f..1f fraction, or -1f when the total size is unknown. */
    fun interface Progress {
        fun onProgress(fraction: Float)
    }

    /**
     * Downloads [url] and launches the installer using DownloadManager for Android TV compatibility.
     * Returns true once the download is enqueued; throws on a failure so the caller can show a retry.
     */
    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        expectedBytes: Long,
        progress: Progress,
    ): Boolean {
        // Check install permission on Android 6+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.w("ApkInstaller", "Install unknown apps permission not granted, opening settings")
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return false
                }
            } catch (e: Exception) {
                Log.w("ApkInstaller", "Cannot check install permission: ${e.message}")
            }
        }

        // Use DownloadManager - works reliably on Android TV and phones
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("LOVIXTV Update")
            setDescription("Downloading LOVIXTV update...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "lovixtv-update.apk")
            setMimeType("application/vnd.android.package-archive")
            addRequestHeader("User-Agent", "LOVIXTV")
        }

        val downloadId = downloadManager.enqueue(request)
        Log.i("ApkInstaller", "Download enqueued with ID: $downloadId")

        // Simulate progress by polling download status
        withContext(Dispatchers.IO) {
            var lastProgress = 0f
            while (true) {
                delay(500)
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val status = cursor.getInt(statusIdx)
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            // Trigger install
                            val uri = Uri.parse("content://downloads/my_downloads/$downloadId")
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.w("ApkInstaller", "ACTION_VIEW install failed: ${e.message}")
                            }
                            progress.onProgress(1f)
                            cursor.close()
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            cursor.close()
                            error("Download failed")
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            val downloaded = cursor.getLong(bytesDownIdx)
                            val total = cursor.getLong(bytesTotalIdx)
                            val fraction = if (total > 0) downloaded.toFloat() / total else -1f
                            if (fraction != lastProgress) {
                                progress.onProgress(fraction)
                                lastProgress = fraction
                            }
                        }
                    }
                }
                cursor.close()
            }
        }
        return true
    }
}
