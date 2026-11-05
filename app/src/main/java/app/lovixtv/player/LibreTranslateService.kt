/*
 * LibreTranslate integration for subtitle translation.
 * Translates subtitle text between English, Spanish, and Dutch.
 */
package app.lovixtv.player

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * LibreTranslate service for subtitle translation.
 * 
 * Translates subtitle text between English, Spanish, and Dutch.
 * Can use a local LibreTranslate server or a remote API.
 */
class LibreTranslateService(
    private val httpClient: OkHttpClient,
    private val serverUrl: String = "http://localhost:5000"
) {
    companion object {
        private const val TAG = "LibreTranslateService"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        // Supported language pairs
        private val SUPPORTED_PAIRS = mapOf(
            "en" to listOf("es", "nl"),
            "es" to listOf("en"),
            "nl" to listOf("en")
        )
    }

    /**
     * Translate text from source language to target language.
     * 
     * @param text Text to translate
     * @param source Source language code (en, es, nl)
     * @param target Target language code (en, es, nl)
     * @return Translated text, or original text if translation fails
     */
    suspend fun translate(text: String, source: String, target: String): String = withContext(Dispatchers.IO) {
        if (source == target) {
            return@withContext text
        }

        if (!isTranslationSupported(source, target)) {
            Log.w(TAG, "Translation not supported: $source -> $target")
            return@withContext text
        }

        try {
            val payload = JSONObject().apply {
                put("q", text)
                put("source", source)
                put("target", target)
                put("format", "text")
            }

            val request = Request.Builder()
                .url("$serverUrl/translate")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Translation request failed: ${response.code}")
                return@withContext text
            }

            val body = response.body?.string() ?: return@withContext text
            val result = JSONObject(body)

            if (result.has("translatedText")) {
                val translated = result.getString("translatedText")
                Log.d(TAG, "Translated: $source -> $target (${text.length} chars)")
                return@withContext translated
            } else {
                Log.w(TAG, "Translation response missing translatedText")
                return@withContext text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            return@withContext text
        }
    }

    /**
     * Check if translation is supported between language pair.
     */
    fun isTranslationSupported(source: String, target: String): Boolean {
        val targets = SUPPORTED_PAIRS[source.lowercase()] ?: return false
        return target.lowercase() in targets
    }

    /**
     * Translate subtitle segments.
     * 
     * @param segments List of subtitle segments
     * @param source Source language code
     * @param target Target language code
     * @return List of translated segments
     */
    suspend fun translateSegments(
        segments: List<SubtitleSegment>,
        source: String,
        target: String
    ): List<SubtitleSegment> = withContext(Dispatchers.IO) {
        if (source == target) {
            return@withContext segments
        }

        segments.map { segment ->
            val translatedText = translate(segment.text, source, target)
            segment.copy(text = translatedText)
        }
    }
}
