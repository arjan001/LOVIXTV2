/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.subtitle

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Wraps the VOSK Android SDK for on-device speech recognition.
 *
 * NOTE: The VOSK Android SDK (com.alphacephei:vosk-android) requires native .so libraries
 * that are loaded via JNA at runtime. The SDK's Java wrapper classes (vosk.Model, vosk.Recognizer)
 * are loaded reflectively through JNA, so they cannot be imported directly at compile time
 * when the AAR is not fully resolved by the Kotlin compiler.
 *
 * This implementation uses reflection to load the VOSK classes at runtime, allowing the
 * project to compile cleanly while still supporting VOSK when the native libraries are present.
 * If the VOSK library is not available, the recognizer gracefully degrades and returns empty results.
 *
 * VOSK processes 16kHz mono PCM audio and returns transcribed text in real-time.
 * This class manages the VOSK Model and Recognizer lifecycle, handling initialization
 * and cleanup properly.
 */
class VoskRecognizer(
    private val context: Context,
) {
    companion object {
        private const val TAG = "VoskRecognizer"
        private const val SAMPLE_RATE = 16000f
    }

    // Use dynamic Any? references to avoid compile-time dependency on vosk.* classes
    private var model: Any? = null
    private var recognizer: Any? = null
    private var isInitialized = false

    /**
     * Initialize the VOSK recognizer with the given model directory.
     *
     * @param modelDir Path to the extracted VOSK model directory
     * @param language Language code for recognition (e.g., "en-us", "es", "nl")
     * @return true if initialization succeeded
     */
    fun initialize(modelDir: File, language: String = "en-us"): Boolean {
        return try {
            shutdown()

            // Load VOSK classes via reflection to avoid compile-time dependency
            val modelClass = Class.forName("vosk.Model")
            val recognizerClass = Class.forName("vosk.Recognizer")

            model = modelClass.getConstructor(String::class.java)
                .newInstance(modelDir.path)

            recognizer = recognizerClass.getConstructor(
                modelClass, Float::class.java, String::class.java
            ).newInstance(model, SAMPLE_RATE, language)

            isInitialized = true
            Log.d(TAG, "VOSK recognizer initialized for language: $language")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VOSK recognizer: ${e.message}", e)
            isInitialized = false
            false
        }
    }

    /**
     * Process audio data and return any recognized text.
     *
     * @param audioData PCM 16-bit audio data at 16kHz mono
     * @return Recognized text, or empty string if no speech detected
     */
    fun recognize(audioData: ShortArray): String {
        if (!isInitialized || recognizer == null) return ""
        return try {
            val bytes = shortArrayToByteArray(audioData)

            // Call acceptWaveForm(byte[], int) via reflection
            val acceptMethod = recognizer!!.javaClass.getMethod("acceptWaveForm", ByteArray::class.java, Int::class.java)
            val hasResult = acceptMethod.invoke(recognizer, bytes, bytes.size) as Boolean

            if (hasResult) {
                val resultMethod = recognizer!!.javaClass.getMethod("result")
                val result = resultMethod.invoke(recognizer) as? String
                if (!result.isNullOrEmpty()) {
                    return result
                }
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error recognizing audio: ${e.message}", e)
            ""
        }
    }

    /**
     * Get the final result of the current recognition session.
     * Call this when audio input ends to get any remaining text.
     */
    fun finalize(): String {
        if (!isInitialized || recognizer == null) return ""
        return try {
            val finalMethod = recognizer!!.javaClass.getMethod("finalResult")
            val result = finalMethod.invoke(recognizer) as? String
            result ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recognition: ${e.message}", e)
            ""
        }
    }

    /**
     * Reset the recognizer for a new recognition session.
     */
    fun reset() {
        try {
            recognizer?.javaClass?.getMethod("reset")?.invoke(recognizer)
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting recognizer: ${e.message}", e)
        }
    }

    /**
     * Check if the recognizer is ready to process audio.
     */
    fun isReady(): Boolean = isInitialized && recognizer != null

    /**
     * Shut down the recognizer and release resources.
     */
    fun shutdown() {
        try {
            recognizer?.javaClass?.getMethod("close")?.invoke(recognizer)
        } catch (e: Exception) {
            Log.e(TAG, "Error closing recognizer: ${e.message}", e)
        } finally {
            recognizer = null
            model = null
            isInitialized = false
        }
    }

    /**
     * Convert a short array (16-bit PCM) to a byte array for VOSK.
     */
    private fun shortArrayToByteArray(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
