/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.subtitle

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Downloads VOSK language models on-demand and caches them on-device.
 *
 * Models are stored in `<app_files>/vosk_models/{model_name}/` and downloaded only once.
 * The English model (~45 MB) is the default; Spanish and Dutch models are downloaded when
 * the user selects those languages.
 *
 * Model URLs from the VOSK project:
 * - English (small): https://alphacephei.com/vosk-model/en-us-0.15
 * - Spanish: https://alphacephei.com/vosk-model/es-0.22
 * - Dutch: https://alphacephei.com/vosk-model/nl-0.8
 */
class ModelDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val MODELS_DIR = "vosk_models"
        private const val BASE_URL = "https://alphacephei.com/vosk-model"

        val ENGLISH = ModelInfo("en-us-0.15", "$BASE_URL/en-us-0.15.zip")
        val SPANISH = ModelInfo("es-0.22", "$BASE_URL/es-0.22.zip")
        val DUTCH = ModelInfo("nl-0.8", "$BASE_URL/nl-0.8.zip")

        val ALL = listOf(ENGLISH, SPANISH, DUTCH)
    }

    data class ModelInfo(val name: String, val downloadUrl: String)

    private val modelsDir = File(context.filesDir, MODELS_DIR).apply { mkdirs() }

    /** Returns the path to the model directory, downloading if necessary. */
    suspend fun ensureModel(modelInfo: ModelInfo): File =
        withContext(Dispatchers.IO) {
            val modelDir = File(modelsDir, modelInfo.name)
            if (modelDir.exists() && modelDir.isDirectory && modelDir.listFiles()?.isNotEmpty() == true) {
                // Check for the required 'uuid' file that VOSK needs
                val uuidFile = File(modelDir, "uuid")
                if (uuidFile.exists()) {
                    return@withContext modelDir
                }
            }
            downloadAndExtract(modelInfo, modelDir)
            modelDir
        }

    /** Returns true if the model is already downloaded and ready. */
    fun isModelAvailable(modelInfo: ModelInfo): Boolean {
        val modelDir = File(modelsDir, modelInfo.name)
        return modelDir.exists() && File(modelDir, "uuid").exists()
    }

    /** Remove a downloaded model to free space. */
    fun removeModel(modelInfo: ModelInfo) {
        File(modelsDir, modelInfo.name).deleteRecursively()
    }

    /** Returns total size of all downloaded models in bytes. */
    fun totalModelsSize(): Long {
        return modelsDir.listFiles()?.sumOf { it.walkTopDown().sumOf { f -> f.length() } } ?: 0
    }

    private suspend fun downloadAndExtract(modelInfo: ModelInfo, destDir: File) {
        // Remove partial download if it exists
        destDir.deleteRecursively()
        destDir.mkdirs()

        val tempFile = File(modelsDir, "${modelInfo.name}.zip.tmp").apply { deleteOnExit() }

        val request = Request.Builder()
            .url(modelInfo.downloadUrl)
            .header("User-Agent", "LOVIXTV/1.0")
            .build()

        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Failed to download model: ${response.code}" }

            response.body?.byteStream()?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        // Extract the zip to the model directory
        tempFile.inputStream().use { zipInput ->
            ZipInputStream(zipInput).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(destDir, entry.name)
                    // Prevent zip-slip vulnerability
                    if (!entryFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                        throw SecurityException("Malicious zip entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        zis.use { it.copyTo(entryFile.outputStream()) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        // Ensure uuid file exists (VOSK requires it)
        val uuidFile = File(destDir, "uuid")
        if (!uuidFile.exists()) {
            uuidFile.writeText(java.util.UUID.randomUUID().toString())
        }

        // Clean up the zip file
        tempFile.delete()
    }
}
