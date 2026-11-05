/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Real-time translation service using LibreTranslate API.
 *
 * Translates subtitle text from the source language to the target language.
 * Uses the libretranslate.de API as the default, with configurable endpoint.
 *
 * Supported languages: English (en), Spanish (es), Dutch (nl), French (fr),
 * German (de), Italian (it), Portuguese (pt), Arabic (ar).
 */
class LibreTranslator(
    private val httpClient: OkHttpClient,
    private val apiUrl: String = "https://libretranslate.de/translate",
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        // Supported language codes
        val SUPPORTED_LANGUAGES = listOf("en", "es", "nl", "fr", "de", "it", "pt", "ar")

        // Language display names
        val LANGUAGE_NAMES = mapOf(
            "en" to "English",
            "es" to "Spanish",
            "nl" to "Dutch",
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "pt" to "Portuguese",
            "ar" to "Arabic",
        )
    }

    /**
     * Translate text from source language to target language.
     *
     * @param text Text to translate
     * @param sourceLang Source language code (e.g., "en")
     * @param targetLang Target language code (e.g., "es")
     * @return Translated text, or original text if translation fails
     */
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String = withContext(Dispatchers.IO) {
        // If source and target are the same, return original
        if (sourceLang == targetLang) return@withContext text

        // If text is empty or too short, skip translation
        if (text.trim().isEmpty() || text.length < 3) return@withContext text

        try {
            val requestBody = json.encodeToString(TranslateRequest(
                q = text,
                source = sourceLang,
                target = targetLang,
                format = "text"
            )).toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .header("User-Agent", "LOVIXTV/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let {
                        try {
                            val result = json.decodeFromString<TranslateResponse>(it)
                            result.translatedText
                        } catch (e: Exception) {
                            text // Return original on parse error
                        }
                    } ?: text
                } else {
                    text // Return original on HTTP error
                }
            }
        } catch (e: Exception) {
            // Network error or other exception — return original text
            e.printStackTrace()
            text
        }
    }

    /**
     * Batch translate multiple texts for efficiency.
     *
     * @param texts List of texts to translate
     * @param sourceLang Source language code
     * @param targetLang Target language code
     * @return List of translated texts (same order as input)
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLang: String,
        targetLang: String,
    ): List<String> {
        if (sourceLang == targetLang) return texts
        return texts.map { translate(it, sourceLang, targetLang) }
    }

    @Serializable
    private data class TranslateRequest(
        val q: String,
        val source: String,
        val target: String,
        val format: String,
    )

    @Serializable
    private data class TranslateResponse(
        val translatedText: String,
    )
}
