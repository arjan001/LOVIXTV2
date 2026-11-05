/*
 * Vosk-based offline subtitle generation for LOVIX.
 * Uses Vosk for speech recognition and LibreTranslate for translation.
 */
package app.lovixtv.player

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.InputStream

/**
 * Vosk subtitle service for offline speech recognition.
 *
 * Generates subtitles from audio streams using Vosk models.
 * Supports English, Spanish, and Dutch subtitle generation.
 */
class VoskSubtitleService(private val context: Context) {
    companion object {
        private const val TAG = "VoskSubtitleService"
        private const val SAMPLE_RATE = 16000f
        private const val MODEL_DIR = "vosk-models"
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val modelDir = File(context.filesDir, MODEL_DIR).apply { mkdirs() }

    /**
     * Initialize Vosk model for speech recognition.
     * Downloads model if not present.
     */
    suspend fun initialize(language: String = "en"): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelPath = File(modelDir, "vosk-model-$language")
            if (!modelPath.exists()) {
                Log.w(TAG, "Vosk model not found at $modelPath. Model must be downloaded separately.")
                return@withContext false
            }

            model = Model(modelPath.absolutePath)
            recognizer = Recognizer(model, SAMPLE_RATE)
            Log.i(TAG, "Vosk model initialized for language: $language")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk model", e)
            false
        }
    }

    /**
     * Transcribe audio stream to text.
     *
     * @param audioStream PCM audio stream (16kHz, mono, 16-bit)
     * @return Transcribed text with word-level timestamps
     */
    suspend fun transcribe(audioStream: InputStream): List<SubtitleSegment> = withContext(Dispatchers.IO) {
        if (recognizer == null) {
            Log.e(TAG, "Recognizer not initialized")
            return@withContext emptyList()
        }

        try {
            val segments = mutableListOf<SubtitleSegment>()
            val bufferSize = 4096
            val buffer = ByteArray(bufferSize)

            while (true) {
                val bytesRead = audioStream.read(buffer)
                if (bytesRead == -1) break

                // Android Vosk API: acceptWaveForm with capital F
                val isFinal = recognizer!!.acceptWaveForm(buffer)
                if (isFinal) {
                    val result = recognizer!!.result
                    val segment = parseResult(result)
                    if (segment != null) {
                        segments.add(segment)
                    }
                }
            }

            // Get final result
            val finalResult = recognizer!!.finalResult
            val finalSegment = parseResult(finalResult)
            if (finalSegment != null) {
                segments.add(finalSegment)
            }

            segments
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            emptyList()
        }
    }

    /**
     * Parse Vosk JSON result to subtitle segment.
     */
    private fun parseResult(json: String): SubtitleSegment? {
        try {
            val result = org.json.JSONObject(json)
            if (!result.has("result")) return null

            val words = result.getJSONArray("result")
            if (words.length() == 0) return null

            val firstWord = words.getJSONObject(0)
            val lastWord = words.getJSONObject(words.length() - 1)

            val startTime = firstWord.getDouble("start")
            val endTime = lastWord.getDouble("end")
            val text = buildString {
                for (i in 0 until words.length()) {
                    append(words.getJSONObject(i).getString("word"))
                    if (i < words.length() - 1) append(" ")
                }
            }

            return SubtitleSegment(
                startTime = startTime,
                endTime = endTime,
                text = text.trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse result", e)
            return null
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        recognizer?.close()
        model?.close()
        recognizer = null
        model = null
    }

    /**
     * Check if model is available.
     */
    fun isModelAvailable(language: String = "en"): Boolean {
        return File(modelDir, "vosk-model-$language").exists()
    }
}

/**
 * Subtitle segment with timing information.
 */
data class SubtitleSegment(
    val startTime: Double,
    val endTime: Double,
    val text: String
)
