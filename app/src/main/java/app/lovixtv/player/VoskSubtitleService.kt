/*
 * Vosk-based real-time subtitle generation for LOVIX.
 * Transcribes audio from video playback in real-time.
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
 * Vosk subtitle service for real-time speech recognition.
 * 
 * Transcribes audio streams and returns word-level timestamps for subtitles.
 * Supports English, Spanish, and Dutch.
 */
class VoskSubtitleService(private val context: Context) {
    companion object {
        private const val TAG = "VoskSubtitleService"
        private const val SAMPLE_RATE = 16000f
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val modelDownloader = ModelDownloader(context)

    /**
     * Initialize Vosk model for speech recognition.
     * 
     * @param language Language code (en, es, nl)
     * @return True if initialization succeeded
     */
    suspend fun initialize(language: String = "en"): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if model is downloaded
            if (!modelDownloader.isModelDownloaded(language)) {
                Log.w(TAG, "Model not downloaded for language: $language")
                return@withContext false
            }

            val modelPath = modelDownloader.getModelPath(language)
            if (!modelPath.exists()) {
                Log.e(TAG, "Model path does not exist: ${modelPath.absolutePath}")
                return@withContext false
            }

            Log.i(TAG, "Loading Vosk model from: ${modelPath.absolutePath}")
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
     * @return List of subtitle segments with timing
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

                // Vosk Java API: acceptWaveForm takes buffer and length
                val isFinal = recognizer!!.acceptWaveForm(buffer, bytesRead)
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
     * Check if model is available for a language.
     */
    fun isModelAvailable(language: String): Boolean {
        return modelDownloader.isModelDownloaded(language)
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
