/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.subtitle

import android.content.Context
import java.io.File
import vosk.Model
import vosk.Recognizer

/**
 * Wraps the VOSK Android SDK for on-device speech recognition.
 *
 * VOSK processes 16kHz mono PCM audio and returns transcribed text in real-time.
 * This class manages the VOSK Model and Recognizer lifecycle, handling initialization
 * and cleanup properly.
 *
 * The recognizer is created with a specific language model and can be reconfigured
 * when the user switches languages.
 */
class VoskRecognizer(
    private val context: Context,
) {
    companion object {
        private const val SAMPLE_RATE = 16000f
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var isInitialized = false

    /**
     * Initialize the VOSK recognizer with the given model directory.
     *
     * @param modelDir Path to the extracted VOSK model directory
     * @param language Language code for recognition (e.g., "en-us", "es", "nl")
     * @return true if initialization succeeded
     */
    fun initialize(modelDir: File, language: String = "en-us"): Boolean {
        try {
            // Clean up any existing model
            shutdown()

            model = Model(modelDir.path)
            recognizer = Recognizer(model!!, SAMPLE_RATE, language)
            isInitialized = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
            return false
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
        try {
            // Convert short array to byte array for VOSK
            val bytes = shortArrayToByteArray(audioData)
            if (recognizer!!.acceptWaveForm(bytes, bytes.size)) {
                val result = recognizer!!.result
                if (result != null && result.isNotEmpty()) {
                    return result
                }
            }
            return ""
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * Get the final result of the current recognition session.
     * Call this when audio input ends to get any remaining text.
     */
    fun finalize(): String {
        if (!isInitialized || recognizer == null) return ""
        try {
            val result = recognizer!!.finalResult
            return if (result != null) result else ""
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    /**
     * Reset the recognizer for a new recognition session.
     */
    fun reset() {
        recognizer?.reset()
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
            recognizer?.close()
            // Note: VOSK Model does not expose a close() method in the Android SDK.
            // The native resources are released when the Model reference is garbage collected.
        } catch (e: Exception) {
            e.printStackTrace()
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
