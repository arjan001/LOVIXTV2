/*
 * Custom audio sink that taps into audio samples for transcription.
 * Wraps the default audio sink and intercepts samples.
 */
package app.lovixtv.player

import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer

/**
 * Custom audio sink that intercepts audio samples for speech recognition.
 * 
 * This wraps the default audio sink and gets a copy of every audio sample
 * as it's being played, allowing us to feed it to Vosk without affecting playback.
 */
@OptIn(UnstableApi::class)
class TappingAudioSink(
    private val audioSink: DefaultAudioSink,
    private val audioCallback: (ByteArray) -> Unit
) : AudioSink by audioSink {
    
    companion object {
        private const val TAG = "TappingAudioSink"
    }
    
    private var isTapping = false
    
    fun enableTapping() {
        isTapping = true
        Log.d(TAG, "Audio tapping enabled")
    }
    
    fun disableTapping() {
        isTapping = false
        Log.d(TAG, "Audio tapping disabled")
    }
    
    @Throws(AudioSink.ConfigurationException::class)
    override fun configure(
        format: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ): AudioSink.SinkFormatSupport {
        val support = audioSink.configure(format, specifiedBufferSize, outputChannels)
        Log.d(TAG, "Audio sink configured: $format")
        return support
    }
    
    @Throws(AudioSink.WriteException::class)
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (isTapping && buffer.hasRemaining()) {
            // Get a copy of the audio data
            val bytes = ByteArray(buffer.remaining())
            buffer.mark()
            buffer.get(bytes)
            buffer.reset()
            
            // Send to callback for Vosk processing
            try {
                audioCallback(bytes)
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio callback", e)
            }
        }
        
        // Pass through to the actual audio sink
        return audioSink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }
}
