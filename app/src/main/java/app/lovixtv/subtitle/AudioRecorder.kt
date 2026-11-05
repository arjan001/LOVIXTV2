/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.subtitle

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Records audio from the device microphone and feeds it to the VOSK recognizer.
 *
 * Captures 16kHz mono 16-bit PCM audio, which is the format VOSK expects.
 * Runs in a background coroutine and feeds audio chunks to the engine continuously.
 *
 * Note: On Android TV, this captures system audio played through the speakers.
 * The user hears the content, and the microphone picks it up for transcription.
 * For best results, the TV should be in a relatively quiet environment.
 */
class AudioRecorder(
    private val engine: LiveSubtitleEngine,
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MS = 100 // 100ms chunks
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Start recording audio from the microphone.
     *
     * @return true if recording started successfully
     */
    fun start(): Boolean {
        if (isRecording) return true

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        return try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2 // Double buffer for safety
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            startRecordingLoop()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            audioRecord?.release()
            audioRecord = null
            false
        }
    }

    /**
     * Stop recording audio.
     */
    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
        }
    }

    private fun startRecordingLoop() {
        recordingJob = scope.launch {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            // Calculate bytes per chunk: SAMPLE_RATE * bytesPerSample * channels * durationMs / 1000
            val bytesPerChunk = (SAMPLE_RATE * 2 * 1 * BUFFER_SIZE_MS / 1000)
            val buffer = ShortArray(bytesPerChunk / 2) // 2 bytes per short

            while (isRecording) {
                audioRecord?.use { record ->
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        return
                    }

                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        // Feed the audio chunk to the VOSK engine
                        val actualData = if (bytesRead < buffer.size) {
                            buffer.copyOf(bytesRead)
                        } else {
                            buffer
                        }
                        engine.feedAudio(actualData)
                    }
                }

                // Small delay to maintain consistent chunk timing
                delay(BUFFER_SIZE_MS.toLong())
            }
        }
    }
}
