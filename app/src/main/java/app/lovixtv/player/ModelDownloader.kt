/*
 * Model downloader for Vosk speech recognition models.
 * Downloads models on first app launch and stores them in device storage.
 */
package app.lovixtv.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads Vosk models to device storage.
 * 
 * Models are downloaded on first app launch and stored in:
 * /data/data/app.lovixtv/files/vosk-models/
 */
class ModelDownloader(private val context: Context) {
    companion object {
        private const val TAG = "ModelDownloader"
        private const val MODEL_DIR = "vosk-models"
        
        // Model URLs (Vosk official repository)
        private const val ENGLISH_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val SPANISH_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        private const val DUTCH_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip"
    }

    private val modelDir = File(context.filesDir, MODEL_DIR).apply { mkdirs() }

    /**
     * Check if a model is already downloaded.
     */
    fun isModelDownloaded(language: String): Boolean {
        val modelPath = File(modelDir, "vosk-model-$language")
        return modelPath.exists() && modelPath.isDirectory && modelPath.listFiles()?.isNotEmpty() == true
    }

    /**
     * Download a Vosk model for the specified language.
     * 
     * @param language Language code (en, es, nl)
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return True if download succeeded
     */
    suspend fun downloadModel(
        language: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelUrl = when (language.lowercase()) {
                "en" -> ENGLISH_MODEL_URL
                "es" -> SPANISH_MODEL_URL
                "nl" -> DUTCH_MODEL_URL
                else -> ENGLISH_MODEL_URL
            }

            val zipFile = File(modelDir, "vosk-model-$language.zip")
            val modelPath = File(modelDir, "vosk-model-$language")

            Log.i(TAG, "Downloading model for language: $language from $modelUrl")

            // Download the zip file
            downloadFile(modelUrl, zipFile, onProgress)

            // Extract the zip file
            Log.i(TAG, "Extracting model for language: $language")
            extractZip(zipFile, modelPath)

            // Clean up zip file
            zipFile.delete()

            Log.i(TAG, "Model download complete for language: $language")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model for language: $language", e)
            false
        }
    }

    /**
     * Download a file from URL with progress tracking.
     */
    private fun downloadFile(url: String, destFile: File, onProgress: (Float) -> Unit) {
        val connection = URL(url).openConnection()
        connection.connect()
        val fileSize = connection.contentLength

        val input = connection.getInputStream()
        val output = FileOutputStream(destFile)

        val buffer = ByteArray(4096)
        var totalBytesRead: Long = 0
        var bytesRead: Int

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            val progress = if (fileSize > 0) totalBytesRead.toFloat() / fileSize else 0f
            onProgress(progress)
        }

        output.close()
        input.close()
    }

    /**
     * Extract a zip file to destination directory using Java's built-in ZipInputStream.
     * This avoids depending on the 'unzip' command which is not available on Android.
     */
    private fun extractZip(zipFile: File, destDir: File) {
        destDir.mkdirs()
        
        ZipInputStream(zipFile.inputStream()).use { zis ->
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
                    FileOutputStream(entryFile).use { output ->
                        zis.copyTo(output)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Get the path to a downloaded model.
     */
    fun getModelPath(language: String): File {
        return File(modelDir, "vosk-model-$language")
    }

    /**
     * Get total size of all downloaded models in bytes.
     */
    fun getTotalModelSize(): Long {
        return modelDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /**
     * Delete all downloaded models.
     */
    fun clearAllModels() {
        modelDir.deleteRecursively()
        modelDir.mkdirs()
    }
}
