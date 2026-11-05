/*
 * Custom audio processor that intercepts audio samples for speech recognition.
 * Processes audio samples in the Media3 pipeline before they reach the output.
 */
package app.lovixtv.player

import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Audio processor that intercepts PCM audio samples for real-time transcription.
 * 
 * This processor sits in the Media3 audio pipeline and gets a copy of every
 * audio sample as it flows through the player. We feed these samples to Vosk
 * for speech recognition without interfering with playback.
 */
@OptIn(UnstableApi::class)
class AudioInterceptorProcessor : AudioProcessor {
    companion object {
        private const val TAG = "AudioInterceptor"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_COUNT = 1
        private var outputBuffer: ByteBuffer? = null
    }

    private var active = false
    private var audioSink: ((ByteArray) -> Unit)? = null
    private var outputFormat: AudioFormat? = null
    private var inputFormat: AudioFormat? = null

    /**
     * Set the audio sink callback.
     * This will receive audio samples as they flow through the pipeline.
     */
    fun setAudioSink(sink: (ByteArray) -> Unit) {
        this.audioSink = sink
    }

    /**
     * Activate the interceptor.
     */
    fun activate() {
        active = true
    }

    /**
     * Deactivate the interceptor.
     */
    fun deactivate() {
        active = false
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        inputFormat = inputAudioFormat
        // Pass through the original format unchanged
        outputFormat = inputAudioFormat
        // Initialize the output buffer
        outputBuffer = ByteBuffer.allocate(16 * 1024)
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return active
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (active && audioSink != null) {
            // Get a copy of the audio data
            val bytes = ByteArray(inputBuffer.remaining())
            inputBuffer.get(bytes)
            inputBuffer.rewind()
            
            // Send to audio sink for Vosk processing
            audioSink?.invoke(bytes)
        }
        
        // Pass the buffer through unchanged
        outputBuffer?.put(inputBuffer.duplicate())
    }

    private val dummyBuffer: ByteBuffer = ByteBuffer.allocate(0)

    override fun getOutput(): ByteBuffer {
        return outputBuffer ?: dummyBuffer
    }

    override fun isEnded(): Boolean {
        return outputBuffer?.remaining() == 0 && outputBuffer?.position() != 0
    }

    override fun flush() {
        outputBuffer?.clear()
    }

    override fun reset() {
        outputBuffer?.clear()
    }

    override fun queueEndOfStream() {
        // Signal end of stream - no special handling needed for interception
    }
}
